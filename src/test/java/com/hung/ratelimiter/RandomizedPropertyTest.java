package com.hung.ratelimiter;

import com.hung.ratelimiter.algorithm.FixedWindowRateLimiter;
import com.hung.ratelimiter.algorithm.SlidingWindowCounterRateLimiter;
import com.hung.ratelimiter.algorithm.TokenBucketRateLimiter;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;


class RandomizedPropertyTest {

    @Test
    void fixedWindowMatchesSimpleReferenceModel() {
        Random rnd = new Random(42);
        int limit = 5;
        long window = 10_000;
        ManualTimeProvider time = new ManualTimeProvider(0);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(limit, window, time);

        Map<Long, Integer> acceptedPerWindow = new HashMap<>();   // mô hình tham chiếu
        for (int i = 0; i < 20_000; i++) {
            time.advance(rnd.nextInt(2_000));
            long now = time.nowMillis();
            long windowIndex = now / window;

            boolean expected = acceptedPerWindow.getOrDefault(windowIndex, 0) < limit;
            if (expected) acceptedPerWindow.merge(windowIndex, 1, Integer::sum);

            assertEquals(expected, limiter.isAllowed("k"), "at t=" + now);
        }
    }

    @Test
    void slidingWindowCounterNeverAcceptsMoreThanLimitPerWindow() {
        Random rnd = new Random(7);
        int limit = 20;
        long window = 5_000;
        ManualTimeProvider time = new ManualTimeProvider(0);
        SlidingWindowCounterRateLimiter limiter =
                new SlidingWindowCounterRateLimiter(limit, window, time);

        Map<Long, Integer> acceptedPerWindow = new HashMap<>();
        for (int i = 0; i < 50_000; i++) {
            time.advance(rnd.nextInt(300));
            if (limiter.isAllowed("k")) {
                acceptedPerWindow.merge(time.nowMillis() / window, 1, Integer::sum);
            }
        }
        acceptedPerWindow.forEach((w, count) ->
                assertTrue(count <= limit, "window " + w + " accepted " + count));
    }

    @Test
    void tokenBucketNeverSpendsMoreThanCapacityPlusRefill() {
        Random rnd = new Random(1);
        int capacity = 10;
        double ratePerSecond = 2.0;
        ManualTimeProvider time = new ManualTimeProvider(0);
        TokenBucketRateLimiter limiter =
                new TokenBucketRateLimiter(capacity, ratePerSecond, time);

        long accepted = 0;
        double bound = capacity;
        for (int i = 0; i < 50_000; i++) {
            time.advance(rnd.nextInt(200));
            if (limiter.isAllowed("k")) accepted++;
            bound = capacity + ratePerSecond * time.nowMillis() / 1000.0;
            assertTrue(accepted <= bound + 1e-6,
                    "accepted " + accepted + " > bound " + bound);
        }
        // tải nhiều và nhanh nên xô gần như luôn cạn: số được nhận phải sát cận trên
        assertTrue(accepted >= bound - 2, "accepted " + accepted + " too low");
    }
}
