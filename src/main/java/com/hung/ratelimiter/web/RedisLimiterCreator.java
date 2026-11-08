package com.hung.ratelimiter.web;

import com.hung.ratelimiter.core.Algorithm;
import com.hung.ratelimiter.core.LimiterCreator;
import com.hung.ratelimiter.core.RateLimiter;
import com.hung.ratelimiter.core.TimeProvider;
import com.hung.ratelimiter.redis.FailPolicyRateLimiter;
import com.hung.ratelimiter.redis.RedisFixedWindowRateLimiter;
import com.hung.ratelimiter.redis.RedisRateLimitProperties;
import com.hung.ratelimiter.redis.RedisSlidingWindowCounterRateLimiter;
import com.hung.ratelimiter.redis.RedisTokenBucketRateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@ConditionalOnProperty(name = "ratelimiter.store", havingValue = "redis")
public class RedisLimiterCreator implements LimiterCreator {

    private final StringRedisTemplate redis;
    private final TimeProvider time;
    private final RedisRateLimitProperties props;

    public RedisLimiterCreator(StringRedisTemplate redis, TimeProvider time,
                               RedisRateLimitProperties props) {
        this.redis = redis;
        this.time = time;
        this.props = props;
    }

    @Override
    public RateLimiter create(String endpointId, Algorithm algorithm,
                              int limit, long windowMs) {
        // ví dụ: rl:token_bucket:com.hung...DemoController#search:
        String prefix = props.keyPrefix() + ":"
                + algorithm.name().toLowerCase(Locale.ROOT) + ":"
                + endpointId + ":";

        RateLimiter limiter = switch (algorithm) {
            case FIXED_WINDOW ->
                    new RedisFixedWindowRateLimiter(redis, time, prefix, limit, windowMs);
            case SLIDING_WINDOW_COUNTER ->
                    new RedisSlidingWindowCounterRateLimiter(redis, time, prefix, limit, windowMs);
            case TOKEN_BUCKET ->
                    new RedisTokenBucketRateLimiter(redis, time, prefix, limit,
                            limit * 1000.0 / windowMs);
        };
        return new FailPolicyRateLimiter(limiter, props.failOpen());
    }
}
