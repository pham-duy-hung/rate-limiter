package com.hung.ratelimiter.algorithm;

import com.hung.ratelimiter.core.RateLimitResult;
import com.hung.ratelimiter.core.RateLimiter;
import com.hung.ratelimiter.core.TimeProvider;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class FixedWindowCasRateLimiter implements  RateLimiter {
    private record Window(long start, int count){}
    private final int limit;
    private final long windowMs;
    private final TimeProvider time;
    private final ConcurrentHashMap<String, AtomicReference<Window>> states = new ConcurrentHashMap<>();

    public FixedWindowCasRateLimiter(int limit,long windowMs, TimeProvider time){
        this.limit = limit;
        this.time = time;
        this.windowMs = windowMs;
    }
    @Override
    public RateLimitResult tryAcquire(String key){
        long now = time.nowMillis();
        long windowStart = now - (now % windowMs);
        AtomicReference<Window> ref = states.computeIfAbsent(key , k -> new AtomicReference<>(new Window(windowStart,0)));
        while (true){
            Window cur = ref.get();
            Window base = (cur.start() == windowStart) ? cur : new Window(windowStart, 0);
            if (base.count() >= limit) {
                return RateLimitResult.deny(windowStart + windowMs - now);
            }
            Window next = new Window(windowStart, base.count() + 1);
            if (ref.compareAndSet(cur, next)) {
                return RateLimitResult.allow(limit - next.count());
            }
            // neu CAS thất bại do luồng khác vừa cập nhật thi thử lại với trạng thái mới

        }
    }
}
