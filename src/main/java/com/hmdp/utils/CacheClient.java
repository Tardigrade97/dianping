package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;


/**
 * 缓存类型工具类
 */
@Component
@Slf4j
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 设置缓存
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 设置redis，携带逻辑过期时间
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 解决缓存穿透
     * @param keyPrefix 前缀
     * @param id id
     * @param type 数据类型
     * @param dbFallback 操作数据库的方法
     * @param time time
     * @param unit 单位
     * @return
     * @param <T>
     * @param <ID>
     */
    public <T, ID> T queryWithPassThrough(
            String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallback,Long time,TimeUnit unit) {
        // 1. 从redis中查询缓
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3. 存在，返回结果
            return JSONUtil.toBean(json, type);
        }
        // 命中是否是“”
        if (json != null) {
            return null;
        }
        // 4. 不存在，查询数据库。判断商铺是否存在.
        T t = dbFallback.apply(id);
        if (t == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 4.1 商铺存在，写入redis缓存.
        this.set(key, t, time, unit);
        // 4.2 不存在，放回404.
        return t;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期
     *
     * @param id id
     * @return shop实体
     */
    public <R,ID> R queryWithLogicExpire(
            String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFullBack,Long time,TimeUnit unit) {
        // 1. 从redis中查询缓
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isBlank(json)) {
            // 3. 存在，返回结果
            return null;
        }
        // 命中，需要把json反序列化
        RedisData shopData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) shopData.getData(), type);
        // 判断是否过期
        if (shopData.getExpireTime().isAfter(LocalDateTime.now())) {
            //未过期，返回店铺数据
            return r;
        }

        // 过期,尝试获取锁，判断是否获取锁。获取锁后开启独立线程，否则返回店铺信息。
        String lockKey = LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);
        if (lock) {
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    // 重建缓存
                    // 1.先查数据库
                    R r1 = dbFullBack.apply(id);
                    // 2.写入redis
                    this.setWithLogicExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        return r;
    }


    /**
     * 尝试锁
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    /**
     * 释放锁
     *
     * @param key 解锁
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }


}
