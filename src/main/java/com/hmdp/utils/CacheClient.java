package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }
    //逻辑过期构造
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //不确定类型，返回值使用泛型，最后返回时擦除
    //输入 需要指定类型，需要id查找数据库id也可以不确定，两者使用泛型；需要前缀+id获取真正的key；需要确定数据库逻辑；写入redis需要设置生命期
    public <R, ID> R queryByIdPassThough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        //拼接key
        String key = keyPrefix + id;
        //在redis中查找缓存，用string存,json格式接收
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断命中
        if(StrUtil.isNotBlank(json)){
            //命中返回商铺信息，转bean成Java对象
            return JSONUtil.toBean(json, type);
        }
        //判断命中是否空字符串""(返回空值解决缓存穿透)
        if(json != null){
            return null;
        }
        //未命中在数据库查找(参数加入数据库查询逻辑Function,调用apply请求参数)
        //判断命中
        R r = dbFallback.apply(id);
        if(r == null){
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //命中将数据写入redis
        set(key, r, time, unit);

        return r;
    }
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //在redis中根据id查找，用string存,json格式接收
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断命中
        if(StrUtil.isBlank(json)){
            //为空，未命中返回空
            return null;
        }
        //命中，判断过期时间：先json反序列化（toBean）为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期直接返回信息
            return r;
        }


        //过期获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //判断是否获取锁
        //拿到，新建线程重建缓存
        if (isLock){
            //成功获取锁后再次检查缓存是否过期
            if (expireTime.isAfter(LocalDateTime.now())){
                //未过期直接返回信息
                return r;
            }
            //缓存不存在重建，使用线程池获取新线程
            //这里实际应该新设置30min，但这里为了测试重建过期线程安全，仅设置30s
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    //先查数据库
                    R r1 = dbFallback.apply(id);
                    //写入缓存
                    setWithLogicalExpire(key, r1, time, unit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unLock(lockKey);
                }
            });

        }
        //没拿到锁返回的是过期信息
        return r;
    }
    public boolean tryLock(String lockKey){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    public void unLock(String lockKey){
        stringRedisTemplate.delete(lockKey);
    }



}
