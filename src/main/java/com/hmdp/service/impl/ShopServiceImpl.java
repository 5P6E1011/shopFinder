package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import net.sf.jsqlparser.statement.SetStatement;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.awt.print.Book;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //原查找方法
        //Shop shop = queryByIdPassThough(id);
        //互斥锁解决缓存击穿
        //Shop shop = queryByIdMutex(id);
        //逻辑过期解决缓存击穿
        //Shop shop = queryWithLogicalExpire(id);
        //使用工具类完成缓存穿透
        //Shop shop = cacheClient.queryByIdPassThough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //工具类完成缓存击穿,需要提前在单元测试缓存预热
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }

        return  Result.ok(shop);
    }

//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
//    public Shop queryWithLogicalExpire(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        //在redis中根据id查找，用string存,json格式接收
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //判断命中
//        if(StrUtil.isBlank(shopJson)){
//            //为空，未命中返回空
//            return null;
//        }
//        //命中，判断过期时间：先json反序列化（toBean）为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        //这里先反序列化为jsonObj，后续data类型可以自由变动
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())){
//            //未过期直接返回信息
//            return shop;
//        }
//
//
//        //过期获取锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        //判断是否获取锁
//        //拿到，新建线程重建缓存
//        if (isLock){
//            //成功获取锁后再次检查缓存是否过期
//            if (expireTime.isAfter(LocalDateTime.now())){
//                //未过期直接返回信息
//                return shop;
//            }
//            //缓存不存在重建，使用线程池获取新线程
//            //这里实际应该新设置30min，但这里为了测试重建过期线程安全，仅设置30s
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    this.saveShopToRedis(id, 30L);
//
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }finally {
//                    unLock(lockKey);
//                }
//            });
//
//        }
//        //没拿到锁返回的是过期信息
//        return shop;
//    }
//    public Shop queryByIdMutex(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        //在redis中根据id查找，用string存,json格式接收
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //判断命中
//        if(StrUtil.isNotBlank(shopJson)){
//            //命中返回商铺信息，转bean成Java对象
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //返回空值解决缓存穿透
//        if(shopJson != null){
//            return null;
//        }
//        //未命中缓存重建
//        //获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        Shop shop;
//        boolean isLock = tryLock(lockKey);
//        try {
//            //判断是否获取锁成功
//            //失败休眠并重试,sleep50ms后递归
//            if (!isLock){
//                Thread.sleep(50);
//                queryByIdMutex(id);
//            }
//            //成功获取锁后再次检查缓存是否存在
//            shopJson = stringRedisTemplate.opsForValue().get(key);
//            if(StrUtil.isNotBlank(shopJson)){
//                //命中返回商铺信息，转bean成Java对象
//                return JSONUtil.toBean(shopJson, Shop.class);
//            }
//            //成功根据id查询数据库
//            //不存在写入空值
//            shop = getById(id);
//            //模拟重建延时
//            Thread.sleep(200);
//            if(shop == null){
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_SHOP_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            //命中将数据写入redis
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            //写完释放锁
//            unLock(lockKey);
//        }
//        return shop;
//    }
//
//    public boolean tryLock(String lockKey){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//    public void unLock(String lockKey){
//        stringRedisTemplate.delete(lockKey);
//    }

//    public void saveShopToRedis(Long id, Long exSeconds){
//        //查询店铺数据
//        Shop shop = getById(id);
//        //封装信息
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(exSeconds));
//        //写入缓存
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }

//    public Shop queryByIdPassThough(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        //在redis中根据id查找，用string存,json格式接收
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //判断命中
//        if(StrUtil.isNotBlank(shopJson)){
//            //命中返回商铺信息，转bean成Java对象
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //未命中在数据库查找
//        //判断命中
//        //未命中404
//        Shop shop = getById(id);
//        if(shop == null){
//            return null;
//        }
//        //命中将数据写入redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        return shop;
//    }



    @Override
    @Transactional
    public Result update(Shop shop) {

        //判id
        if(shop.getId() == null)
            return Result.fail("店铺数据不存在!");
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());


        return Result.ok();
    }
}
