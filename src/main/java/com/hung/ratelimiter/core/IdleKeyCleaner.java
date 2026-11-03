package com.hung.ratelimiter.core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class IdleKeyCleaner implements AutoCloseable {

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "rate-limiter-cleaner");
                t.setDaemon(true);   // không giữ JVM sống khi ứng dụng tắt
                return t;
            });

    public IdleKeyCleaner(RateLimiter limiter, long idleMs, long periodMs) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                limiter.evictIdle(idleMs);
            } catch (RuntimeException e) {
                // nếu để lỗi lọt ra, các lần chạy sau sẽ bị hủy
                e.printStackTrace();
            }
        }, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
