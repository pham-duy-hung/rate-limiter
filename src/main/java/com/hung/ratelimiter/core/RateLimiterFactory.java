package com.hung.ratelimiter.core;

import com.hung.ratelimiter.algorithm.FixedWindowRateLimiter;
import com.hung.ratelimiter.algorithm.SlidingWindowCounterRateLimiter;
import com.hung.ratelimiter.algorithm.TokenBucketRateLimiter;

public final class RateLimiterFactory {
    private RateLimiterFactory() {}
    // cùng 1 câu hình: tôi đa limit request trong windowMs từng mili s
    public static RateLimiter create(Algorithm algorithm, int limit, long windowMs, TimeProvider time) {
        return switch (algorithm){
            case FIXED_WINDOW -> new FixedWindowRateLimiter(limit, windowMs, time);
            case SLIDING_WINDOW_COUNTER ->  new SlidingWindowCounterRateLimiter(limit, windowMs, time);
            case TOKEN_BUCKET ->  new TokenBucketRateLimiter(limit, limit * 1000.0 / windowMs, time);
        };
    }
}
