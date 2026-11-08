package com.hung.ratelimiter.redis;

import com.hung.ratelimiter.core.TimeProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@SuppressWarnings("rawtypes")
public class RedisTokenBucketRateLimiter extends RedisScriptRateLimiter {

    private static final RedisScript<List> SCRIPT =
            RedisScripts.load("redis/token_bucket.lua");

    public RedisTokenBucketRateLimiter(StringRedisTemplate redis, TimeProvider time,
                                       String keyPrefix, int capacity,
                                       double refillPerSecond) {
        super(redis, time, keyPrefix, SCRIPT,
                String.valueOf(positive(capacity, "capacity")),
                String.valueOf(requirePositive(refillPerSecond)));
    }

    private static double requirePositive(double value) {
        if (!(value > 0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("refillPerSecond must be > 0 and finite");
        }
        return value;
    }
}
