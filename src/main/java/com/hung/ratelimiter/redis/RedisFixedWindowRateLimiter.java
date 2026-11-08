package com.hung.ratelimiter.redis;

import com.hung.ratelimiter.core.TimeProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@SuppressWarnings("rawtypes")
public class RedisFixedWindowRateLimiter extends RedisScriptRateLimiter {

    private static final RedisScript<List> SCRIPT =
            RedisScripts.load("redis/fixed_window.lua");

    public RedisFixedWindowRateLimiter(StringRedisTemplate redis, TimeProvider time,
                                       String keyPrefix, int limit, long windowMs) {
        super(redis, time, keyPrefix, SCRIPT,
                String.valueOf(positive(limit, "limit")),
                String.valueOf(positive(windowMs, "windowMs")));
    }
}
