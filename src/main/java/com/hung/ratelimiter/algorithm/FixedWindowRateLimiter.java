package com.hung.ratelimiter.algorithm;

import com.hung.ratelimiter.core.RateLimitResult;
import com.hung.ratelimiter.core.RateLimiter;
import com.hung.ratelimiter.core.TimeProvider;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class FixedWindowRateLimiter implements RateLimiter{
    private final int limit;
    private final long windowMs;
    private final TimeProvider time;
    private final ConcurrentHashMap<String, WindowState> states =
            new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int limit, long windowMs, TimeProvider time) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be > 0");
        if (windowMs <= 0) throw new IllegalArgumentException("windowMs must be > 0");
        this.limit = limit;
        this.windowMs = windowMs;
        this.time = Objects.requireNonNull(time);
    }

    @Override
    public RateLimitResult tryAcquire(String key) {
        long now = time.nowMillis();
        long windowStart = now - (now % windowMs);
        WindowState state = states.computeIfAbsent(key, k -> new WindowState());
        synchronized (state) {
            state.lastAccess = now;
            if (state.windowStart != windowStart) {
                state.windowStart = windowStart;
                state.count = 0;
            }
            if (state.count < limit) {
                state.count++;
                return RateLimitResult.allow(limit - state.count);
            }
            return RateLimitResult.deny(windowStart + windowMs - now);
        }
    }
    private static final class WindowState{
        long windowStart = -1;
        int count = 0;
        long lastAccess;
    }

    public void evictIdle(long idleMs){
        long now = time.nowMillis();
        states.entrySet().removeIf(e -> {
            WindowState s = e.getValue();
            synchronized (s) {
                return  now  - s.lastAccess > idleMs;
            }
        });
    }
    public int trackedKeys(){
        return states.size();
    }
}
