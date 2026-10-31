package com.hung.ratelimiter.core;

public record RateLimitResult(boolean allowed, long remaining, long retryAfterMillis) {
    public static RateLimitResult allow(long remaining) {
        return new RateLimitResult(true, remaining, 0);
    }
    public static RateLimitResult deny(long retryAfterMillis){
        return new RateLimitResult(false,0, retryAfterMillis);
    }
}
