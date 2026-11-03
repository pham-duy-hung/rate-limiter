package com.hung.ratelimiter.algorithm;

import com.hung.ratelimiter.core.RateLimiter;
import com.hung.ratelimiter.core.RateLimitResult;
import com.hung.ratelimiter.core.TimeProvider;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class SlidingWindowCounterRateLimiter implements RateLimiter {
    private final int limit;
    private final long windowMs;
    private final TimeProvider time;
    private final ConcurrentHashMap<String, State> states =
            new ConcurrentHashMap<>();

    public SlidingWindowCounterRateLimiter(int limit, long windowMs, TimeProvider time) {
        if(limit<=0) throw new IllegalArgumentException("limit must be > 0");
        if(windowMs<=0) throw new IllegalArgumentException("windowMs must be > 0");
        this.limit = limit;
        this.windowMs = windowMs;
        this.time = Objects.requireNonNull(time);
    }
    @Override
    public RateLimitResult tryAcquire(String key) {
        long now = time.nowMillis();
        long currStart = now - (now % windowMs);
        State s = states.computeIfAbsent(key, k -> new State(currStart));

        synchronized (s) {
            s.lastAccess = now;
            if(s.currStart != currStart){
                // cửa sổ sau thì prev = cur,  bỏ qua >=1 cửa sổ thì prev = 0
                s.prev = (currStart - s.currStart == windowMs) ? s.curr : 0;
                s.curr = 0;
                s.currStart = currStart;
            }
            double elapsedRatio = (double)(now - currStart) / windowMs;
            double estimated = s.prev * (1 - elapsedRatio) + s.curr;

            if(estimated < limit) {
                s.curr++;
                long remaining = (long) Math.floor(limit - (estimated + 1));
                return  RateLimitResult.allow(Math.max(0,remaining));
            }
            return RateLimitResult.deny(currStart + windowMs - now + 1);
        }
    }
    private static final class State{
        long prev;
        long curr;
        long currStart;
        State(long currStart){
            this.currStart = currStart;
        }
        long lastAccess;
    }
    public void evictIdle(long idleMs){
        long now = time.nowMillis();
        states.entrySet().removeIf(e -> {
            SlidingWindowCounterRateLimiter.State s = e.getValue();
            synchronized (s) {
                return  now  - s.lastAccess > idleMs;
            }
        });
    }
    public int trackedKeys(){
        return states.size();
    }
}
