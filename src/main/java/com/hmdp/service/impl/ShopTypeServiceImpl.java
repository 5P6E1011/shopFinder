package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        //随机生成shopKey
        String shopKey = CACHE_SHOP_TYPE_KEY + UUID.randomUUID().toString(true);
        //转json查询缓存
        String shopTypeJSON = stringRedisTemplate.opsForValue().get(shopKey);
        //建立商家列表
        List<ShopType> typeList;
        //判断缓存命中
        if (StrUtil.isNotBlank(shopTypeJSON)){
            //命中返回json转列表
            typeList = JSONUtil.toList(shopTypeJSON, ShopType.class);
            return  Result.ok(typeList);
        }
        //未命中查库
        //使用了MyBatis-Plus的Lambda查询功能
        //根据 ShopType 实体类中的 sort 字段进行升序排序，并执行查询操作，返回满足条件的 ShopType 对象列表。
        typeList = this.list(new LambdaQueryWrapper<ShopType>()
                .orderByAsc(ShopType::getSort));
        //判断数据库有无数据
        //不存在返回提示
        if (Objects.isNull(typeList))
            return Result.fail("店铺类型不存在");
        //存在写入redis,返回查询数据
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(typeList), CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        return Result.ok(typeList);
    }
}
