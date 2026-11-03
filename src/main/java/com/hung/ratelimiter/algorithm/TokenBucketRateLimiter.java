package com.hung.ratelimiter.algorithm;

import com.hung.ratelimiter.core.RateLimiter;
import com.hung.ratelimiter.core.RateLimitResult;
import com.hung.ratelimiter.core.TimeProvider;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class TokenBucketRateLimiter implements RateLimiter {
    private final int capacity;
    private final double refillPerSecond;
    private final TimeProvider time;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(int capacity, double refillPerSecond, TimeProvider time) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be > 0");
        if (refillPerSecond <= 0) throw new IllegalArgumentException("RefillPerSecond must be > 0");
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.time = Objects.requireNonNull(time);
    }

    @Override
    public RateLimitResult tryAcquire(String key) {
        long now = time.nowMillis();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, now));

        synchronized (bucket) {
            // Cập nhật mốc thời gian truy cập gần nhất để phục vụ dọn dẹp (eviction)
            bucket.lastAccess = now;

            long elapsedMs = Math.max(0, now - bucket.lastRefillMillis);
            bucket.tokens = Math.min(capacity, bucket.tokens + (elapsedMs / 1000.0) * refillPerSecond);
            bucket.lastRefillMillis = Math.max(bucket.lastRefillMillis, now);

            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return RateLimitResult.allow((long) Math.floor(bucket.tokens));
            }

            double missing = 1.0 - bucket.tokens;
            long retryAfter = (long) Math.ceil((missing / refillPerSecond) * 1000);
            return RateLimitResult.deny(retryAfter);
        }
    }

    public void evictIdle(long idleMs) {
        long now = time.nowMillis();
        buckets.entrySet().removeIf(e -> {
            Bucket bucket = e.getValue();
            synchronized (bucket) {
                return (now - bucket.lastAccess) > idleMs;
            }
        });
    }

    public int trackedKeys() {
        return buckets.size();
    }

    private static final class Bucket {
        double tokens;
        long lastRefillMillis;
        long lastAccess;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefillMillis = now;
            this.lastAccess = now;
        }
    }
}