package com.hung.ratelimiter.web;

import com.hung.ratelimiter.core.Algorithm;
import com.hung.ratelimiter.core.LimiterCreator;
import com.hung.ratelimiter.core.RateLimiter;
import com.hung.ratelimiter.core.TimeProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiterRegistry {

    private final LimiterCreator creator;
    private final ConcurrentHashMap<String, RateLimiter> limiters =
            new ConcurrentHashMap<>();

    @Autowired
    public RateLimiterRegistry(LimiterCreator creator) {
        this.creator = creator;
    }

    /** Tiện cho test: luôn dùng bản trong bộ nhớ. */
    public RateLimiterRegistry(TimeProvider time) {
        this(new InMemoryLimiterCreator(time));
    }

    public RateLimiter get(String endpointId, Algorithm algorithm,
                           int limit, long windowMs) {
        return limiters.computeIfAbsent(endpointId,
                id -> creator.create(id, algorithm, limit, windowMs));
    }

    public void evictIdle(long idleMs) {
        limiters.values().forEach(limiter -> limiter.evictIdle(idleMs));
    }
}
