package com.hung.ratelimiter.core;

@FunctionalInterface
public interface LimiterCreator {

    RateLimiter create(String endpointId, Algorithm algorithm,
                       int limit, long windowMs);
}
