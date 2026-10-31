package com.hung.ratelimiter;

import com.hung.ratelimiter.core.TimeProvider;
import java.util.concurrent.atomic.AtomicLong;

public class ManualTimeProvider implements TimeProvider {
    private final AtomicLong now;
    public ManualTimeProvider(long startMillis) {
        this.now = new AtomicLong(startMillis);
    }
    @Override
    public long nowMillis() {
        return now.get();
    }
    public void advance(long millis){
        now.addAndGet(millis);
    }
    public void set(long millis){
        now.set(millis);
    }
}
