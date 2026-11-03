package com.hung.ratelimiter;

import com.hung.ratelimiter.core.Algorithm;
import com.hung.ratelimiter.core.RateLimitResult;
import com.hung.ratelimiter.core.RateLimiter;
import com.hung.ratelimiter.core.RateLimiterFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterContractTest {

    static Stream<Arguments> cases() {
        List<Arguments> list = new ArrayList<>();
        for (Algorithm a : Algorithm.values()) {
            list.add(Arguments.of(a, 5, 10_000L, 0L));
            list.add(Arguments.of(a, 5, 10_000L, 1_234L));   // mốc không tròn cửa sổ
            list.add(Arguments.of(a, 100, 60_000L, 0L));
            list.add(Arguments.of(a, 3, 1_000L, 777L));
        }
        return list.stream();
    }

    @ParameterizedTest(name = "{0}: limit={1}, window={2}ms, start={3}")
    @MethodSource("cases")
    void afterRetryAfterTheNextRequestIsAllowed(
            Algorithm algo, int limit, long window, long start) {
        ManualTimeProvider time = new ManualTimeProvider(start);
        RateLimiter limiter = RateLimiterFactory.create(algo, limit, window, time);

        for (int i = 0; i < limit; i++) {
            assertTrue(limiter.isAllowed("k"), "request " + i);
        }
        RateLimitResult denied = limiter.tryAcquire("k");
        assertFalse(denied.allowed());
        assertTrue(denied.retryAfterMillis() > 0);
        assertEquals(0, denied.remaining());

        time.advance(denied.retryAfterMillis());
        RateLimitResult again = limiter.tryAcquire("k");
        assertTrue(again.allowed());
        assertEquals(0, again.retryAfterMillis());
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    void remainingDecreasesDownToZero(Algorithm algo) {
        ManualTimeProvider time = new ManualTimeProvider(0);
        RateLimiter limiter = RateLimiterFactory.create(algo, 5, 10_000, time);

        long previous = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            RateLimitResult r = limiter.tryAcquire("k");
            assertTrue(r.allowed());
            assertTrue(r.remaining() >= 0 && r.remaining() < previous);
            previous = r.remaining();
        }
        assertEquals(0, previous);
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    void keysAreIndependent(Algorithm algo) {
        ManualTimeProvider time = new ManualTimeProvider(0);
        RateLimiter limiter = RateLimiterFactory.create(algo, 3, 10_000, time);

        for (int i = 0; i < 3; i++) limiter.isAllowed("a");
        assertFalse(limiter.isAllowed("a"));
        assertTrue(limiter.isAllowed("b"));
    }
}
