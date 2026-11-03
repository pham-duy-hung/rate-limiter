package com.hung.ratelimiter.algorithm;

import com.hung.ratelimiter.ManualTimeProvider;
import com.hung.ratelimiter.core.RateLimiter;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConcurrencyTest {
    private static final int LIMIT = 100;
    private static final int THREADS = 32;
    private static final int ATTEMPTS_PER_THREAD = 100;

    @RepeatedTest(20)
    void fixedWindowNeverExceedsLimit() throws Exception {
        var limiter = new FixedWindowRateLimiter(LIMIT, 60_000, new ManualTimeProvider(0));
        assertEquals(LIMIT, countAccepted(limiter));
    }
    @RepeatedTest(20)
    void fixedWindowCasNeverExceedsLimit() throws Exception {
        var limiter = new FixedWindowCasRateLimiter(LIMIT, 60_000, new ManualTimeProvider(0));
        assertEquals(LIMIT, countAccepted(limiter));
    }
    @RepeatedTest(20)
    void slidingWindowCounterNeverExceedsLimit() throws Exception {
        var limiter = new SlidingWindowCounterRateLimiter(
                LIMIT, 60_000, new ManualTimeProvider(0));
        assertEquals(LIMIT, countAccepted(limiter));
    }

    @RepeatedTest(20)
    void tokenBucketNeverExceedsCapacity() throws Exception {
        var limiter = new TokenBucketRateLimiter(
                LIMIT, 1.0, new ManualTimeProvider(0));
        assertEquals(LIMIT, countAccepted(limiter));
    }
    private int countAccepted(RateLimiter limiter) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        List<Future<Object>> futures = new ArrayList<>();

        for(int t = 0; t < THREADS; t++ ) {
            futures.add(pool.submit(()->{
                ready.countDown();
                start.await();
                for(int k = 0; k< ATTEMPTS_PER_THREAD; k++) {
                    if(limiter.isAllowed("shared-key")){
                        accepted.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        ready.await();
        start.countDown();
        for(Future<Object> f : futures) {
            f.get(10,TimeUnit.SECONDS);
        }
        pool.shutdown();
        return accepted.get();
    }
}