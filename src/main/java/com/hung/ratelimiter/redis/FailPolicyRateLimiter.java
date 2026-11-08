package com.hung.ratelimiter.redis;

import com.hung.ratelimiter.core.RateLimitResult;
import com.hung.ratelimiter.core.RateLimiter;
import com.hung.ratelimiter.core.RateLimiterUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

public class FailPolicyRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(FailPolicyRateLimiter.class);

    private final RateLimiter delegate;
    private final boolean failOpen;

    public FailPolicyRateLimiter(RateLimiter delegate, boolean failOpen) {
        this.delegate = delegate;
        this.failOpen = failOpen;
    }

    @Override
    public RateLimitResult tryAcquire(String key) {
        try {
            return delegate.tryAcquire(key);
        } catch (DataAccessException e) {
            if (failOpen) {
                log.warn("Redis unavailable, allowing request (fail-open): {}",
                        e.getMessage());
                return RateLimitResult.allow(-1);   // -1: không biết số còn lại
            }
            throw new RateLimiterUnavailableException("Redis unavailable", e);
        }
    }
}
