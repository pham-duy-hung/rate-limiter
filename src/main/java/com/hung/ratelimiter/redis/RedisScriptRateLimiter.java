package com.hung.ratelimiter.redis;

import com.hung.ratelimiter.core.RateLimitResult;
import com.hung.ratelimiter.core.RateLimiter;
import com.hung.ratelimiter.core.TimeProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Objects;

@SuppressWarnings("rawtypes")
abstract class RedisScriptRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final TimeProvider time;
    private final String keyPrefix;
    private final RedisScript<List> script;
    private final String arg1;
    private final String arg2;

    RedisScriptRateLimiter(StringRedisTemplate redis, TimeProvider time,
                           String keyPrefix, RedisScript<List> script,
                           String arg1, String arg2) {
        this.redis = Objects.requireNonNull(redis);
        this.time = Objects.requireNonNull(time);
        this.keyPrefix = Objects.requireNonNull(keyPrefix);
        this.script = script;
        this.arg1 = arg1;
        this.arg2 = arg2;
    }

    static long positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    @Override
    public RateLimitResult tryAcquire(String key) {
        Objects.requireNonNull(key, "key");
        long now = time.nowMillis();

        // Với StringRedisTemplate, mọi tham số phải là String
        List<?> result = redis.execute(
                script, List.of(keyPrefix + key), arg1, arg2, String.valueOf(now));

        if (result == null || result.size() < 3) {
            throw new IllegalStateException("Unexpected script result: " + result);
        }
        long allowed = ((Number) result.get(0)).longValue();
        long second = ((Number) result.get(1)).longValue();
        long third = ((Number) result.get(2)).longValue();
        return allowed == 1 ? RateLimitResult.allow(second) : RateLimitResult.deny(third);
    }
}
