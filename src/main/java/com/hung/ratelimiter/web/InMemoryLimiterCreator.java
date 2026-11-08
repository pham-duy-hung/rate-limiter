package com.hung.ratelimiter.web;

import com.hung.ratelimiter.core.Algorithm;
import com.hung.ratelimiter.core.LimiterCreator;
import com.hung.ratelimiter.core.RateLimiter;
import com.hung.ratelimiter.core.RateLimiterFactory;
import com.hung.ratelimiter.core.TimeProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ratelimiter.store", havingValue = "memory",
        matchIfMissing = true)
public class InMemoryLimiterCreator implements LimiterCreator {

    private final TimeProvider time;

    public InMemoryLimiterCreator(TimeProvider time) {
        this.time = time;
    }

    @Override
    public RateLimiter create(String endpointId, Algorithm algorithm,
                              int limit, long windowMs) {
        return RateLimiterFactory.create(algorithm, limit, windowMs, time);
    }
}
