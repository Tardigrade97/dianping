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
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);


        // 互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        Shop shop = cacheClient
                .queryWithLogicExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期
     *
     * @param id id
     * @return shop实体
     */
//    public Shop queryWithLogicExpire(Long id) {
//        // 1. 从redis中查询缓
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 2. 判断是否存在
//        if (StrUtil.isBlank(shopJson)) {
//            // 3. 存在，返回结果
//            return null;
//        }
//        // 命中，需要把json反序列化
//        RedisData shopData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) shopData.getData(), Shop.class);
//        // 判断是否过期
//        if (shopData.getExpireTime().isAfter(LocalDateTime.now())) {
//            //未过期，返回店铺数据
//            return shop;
//        }
//
//        // 过期,尝试获取锁，判断是否获取锁。获取锁后开启独立线程，否则返回店铺信息。
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean lock = tryLock(lockKey);
//        if (lock) {
//            CACHE_REBUILD_EXECUTOR.submit(()->{
//                try {
//                    // 重建缓存
//                    this.saveShop2Redis(id, 20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }finally {
//                    // 释放锁
//                    unlock(lockKey);
//                }
//            });
//        }
//        return shop;
//    }


//    public Shop queryWithMutex(Long id) {
//        // 1. 从redis中查询缓
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 2. 判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 3. 存在，返回结果
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        // 命中是否是“”
//        if (shopJson != null) {
//            return null;
//        }
//
//        // 4.实现缓存重建
//        // 4.1 获取互斥锁
//        String lockKey = "lock:shop:" + id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockKey);
//            // 4.2 判断获取是否成功
//            if (!isLock) {
//                // 4.3 失败，则休眠并重试
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            // 4.4 成功，根据id查询数据库
//            shop = getById(id);
//            if (shop == null) {
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            //  商铺存在，写入redis缓存.
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            // 5. 释放互斥锁
//            unlock(lockKey);
//        }
//
//        return shop;
//    }


    public Shop queryWithPassThrough(Long id) {
        // 1. 从redis中查询缓
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 存在，返回结果
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 命中是否是“”
        if (shopJson != null) {
            return null;
        }
        // 4. 不存在，查询数据库。判断商铺是否存在.
        Shop shop = getById(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 4.1 商铺存在，写入redis缓存.
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 4.2 不存在，放回404.
        return shop;
    }
//
//    /**
//     * 尝试锁
//     *
//     * @param key
//     * @return
//     */
//    private boolean tryLock(String key) {
//        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(aBoolean);
//    }
//
//    /**
//     * 释放锁
//     *
//     * @param key 解锁
//     */
//    private void unlock(String key) {
//        stringRedisTemplate.delete(key);
//    }
//
//
//    public void saveShop2Redis(Long id, Long expiredSeconds) {
//        // 1. 查询店铺数据
//        Shop shop = getById(id);
//        // 2. 封装逻辑过期时间
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expiredSeconds));
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }


    /**
     * 先更新数据库，再删除缓存。
     *
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1. 更新数据库
        updateById(shop);
        // 2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
