package com.hung.ratelimiter.core;

import java.util.concurrent.TimeUnit;

public interface RateLimiter {
    RateLimitResult tryAcquire(String key);
    default boolean isAllowed(String key){
        return tryAcquire(key).allowed();
    }
    default void ecictIdle(long idleMs){
        //không làm gì
    }
}
