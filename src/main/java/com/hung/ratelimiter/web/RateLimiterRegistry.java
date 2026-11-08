package com.hung.ratelimiter.web;

import com.hung.ratelimiter.core.Algorithm;
import com.hung.ratelimiter.core.RateLimiter;
import com.hung.ratelimiter.core.RateLimiterFactory;
import com.hung.ratelimiter.core.TimeProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiterRegistry {

    private final TimeProvider time;
    private final ConcurrentHashMap<String, RateLimiter> limiters =
            new ConcurrentHashMap<>();

    public RateLimiterRegistry(TimeProvider time) {
        this.time = time;
    }

    public RateLimiter get(String endpointId, Algorithm algorithm,
                           int limit, long windowMs) {
        return limiters.computeIfAbsent(endpointId,
                id -> RateLimiterFactory.create(algorithm, limit, windowMs, time));
    }

    public void evictIdle(long idleMs) {
        limiters.values().forEach(limiter -> limiter.evictIdle(idleMs));
    }
}
