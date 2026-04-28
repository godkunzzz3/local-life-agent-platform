package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component // ⚠️修复：交给 Spring 管理，这样其他地方才能注入使用
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    // 声明一个包含10个线程的线程池，专门用来在后台重建缓存
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 普通存入 Redis 的方法 (附带 TTL)
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 存入 Redis 的方法 (设置逻辑过期时间)
     * 主要用于解决【缓存击穿】问题
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 1. 将传入的数据封装进 RedisData 对象
        RedisData redisData = new RedisData();
        redisData.setData(value);
        // 2. 计算逻辑过期时间：当前时间 + 传入的时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        // ⚠️修复：既然是逻辑过期，就不给 Redis 设置真实的 TTL，让它永久存活（或者设一个极长的时间）
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 解决【缓存穿透】的通用查询方法
     * @param keyPrefix 缓存前缀
     * @param id 查询的 ID
     * @param type 返回结果的具体类型 (比如 Shop.class)
     * @param dbFallback 查数据库的函数 (传一段查数据库的代码过来)
     * @param time 缓存时间
     * @param unit 时间单位
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        // 1. 从 redis 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在（如果有真实的 JSON 字符串，说明命中了正常缓存）
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type); // 转换为指定类型后返回
        }

        // 3. 判断命中的是否是空值 ""（这就是防穿透的精髓：说明这是一个为了防黑客存入的空字符串）
        if (json != null) {
            // 直接返回 null，不去打扰数据库
            return null;
        }

        // 4. 缓存没命中，调用传进来的 dbFallback 函数，去查数据库
        R r = dbFallback.apply(id);

        // 5. 查不到数据，说明可能是恶意请求，将空字符串 "" 写入 redis，防止后续继续穿透
        if (r == null) {
            // 写入一个极短的过期时间，比如 2 分钟
            stringRedisTemplate.opsForValue().set(key, "", 2, TimeUnit.MINUTES);
            return null;
        }

        // 6. 查到了，正常写入 redis
        this.set(key, r, time, unit);

        return r;
    }

    /**
     * 解决【缓存击穿】的通用查询方法 (基于逻辑过期)
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        // 1. 从 redis 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. ⚠️修复：逻辑过期通常用于热点数据，如果连缓存都没，直接返回 null 即可（或者可以在这查数据库）
        if (StrUtil.isBlank(json)) {
            return null;
        }

        // 3. 命中，先把 json 反序列化为包含数据的 RedisData 对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        // 从 RedisData 里把真正的业务数据拿出来，转成指定的类型 R
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        // 获取逻辑过期时间
        LocalDateTime expireTime = redisData.getExpireTime();

        // 4. 判断是否逻辑过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 时间在当前时间之后，说明未过期，直接返回旧数据
            return r;
        }

        // 5. 走到这里，说明【已过期】，需要缓存重建
        // 获取互斥锁
        String lockKey = "lock:" + keyPrefix + id;
        boolean isLock = tryLock(lockKey);

        // 6. 判断是否获取到了锁
        if (isLock) {
            // 获取到了锁，开启一条新线程去慢悠悠地查数据库、写缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R r1 = dbFallback.apply(id);
                    // ⚠️重建缓存：不仅要把新数据写进去，还要重新计算逻辑过期时间
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // ⚠️修复：无论成功与否，必须释放锁
                    unLock(lockKey);
                }
            });
        }

        // 7. ⚠️修复：不管有没有获取到锁，都直接返回原来的【旧数据】（这叫降级）
        return r;
    }

    // 获取互斥锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    // 释放互斥锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}