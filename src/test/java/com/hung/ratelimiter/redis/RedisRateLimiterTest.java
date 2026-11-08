package com.hung.ratelimiter.redis;

import com.hung.ratelimiter.ManualTimeProvider;
import com.hung.ratelimiter.core.Algorithm;
import com.hung.ratelimiter.core.RateLimitResult;
import com.hung.ratelimiter.core.RateLimiter;
import com.hung.ratelimiter.core.TimeProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RedisRateLimiterTest {

    private static StringRedisTemplate redis;

    private String prefix;

    @BeforeAll
    static void connect() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                System.getenv().getOrDefault("REDIS_HOST", "localhost"),
                Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379")));
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        try {
            template.hasKey("ratelimiter-ping");        // thử kết nối
        } catch (Exception e) {
            assumeTrue(false, "Redis không chạy, bỏ qua test tích hợp");
        }
        redis = template;
    }

    @BeforeEach
    void setUp() {
        prefix = "test:" + UUID.randomUUID() + ":";   // mỗi test một vùng key riêng
    }

    @AfterEach
    void cleanUp() {
        Set<String> keys = redis.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private RateLimiter create(Algorithm algo, int limit, long windowMs, TimeProvider clock) {
        return switch (algo) {
            case FIXED_WINDOW ->
                    new RedisFixedWindowRateLimiter(redis, clock, prefix, limit, windowMs);
            case SLIDING_WINDOW_COUNTER ->
                    new RedisSlidingWindowCounterRateLimiter(redis, clock, prefix, limit, windowMs);
            case TOKEN_BUCKET ->
                    new RedisTokenBucketRateLimiter(redis, clock, prefix, limit,
                            limit * 1000.0 / windowMs);
        };
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    void afterRetryAfterTheNextRequestIsAllowed(Algorithm algo) {
        ManualTimeProvider time = new ManualTimeProvider(1_234);
        RateLimiter limiter = create(algo, 5, 10_000, time);

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.isAllowed("k"), "request " + i);
        }
        RateLimitResult denied = limiter.tryAcquire("k");
        assertFalse(denied.allowed());
        assertTrue(denied.retryAfterMillis() > 0);

        time.advance(denied.retryAfterMillis());
        assertTrue(limiter.tryAcquire("k").allowed());
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    void remainingDecreasesDownToZero(Algorithm algo) {
        RateLimiter limiter = create(algo, 5, 10_000, new ManualTimeProvider(0));
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
        RateLimiter limiter = create(algo, 3, 10_000, new ManualTimeProvider(0));
        for (int i = 0; i < 3; i++) limiter.isAllowed("a");
        assertFalse(limiter.isAllowed("a"));
        assertTrue(limiter.isAllowed("b"));
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    void twoInstancesShareTheSameCounter(Algorithm algo) {
        ManualTimeProvider time = new ManualTimeProvider(0);
        RateLimiter instanceA = create(algo, 10, 60_000, time);
        RateLimiter instanceB = create(algo, 10, 60_000, time);

        int accepted = 0;
        for (int i = 0; i < 20; i++) {
            RateLimiter who = (i % 2 == 0) ? instanceA : instanceB;
            if (who.isAllowed("user")) accepted++;
        }
        assertEquals(10, accepted);   // tổng chung đúng bằng limit, không phải 2 x limit
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    void manyThreadsAcrossTwoInstancesNeverExceedLimit(Algorithm algo) throws Exception {
        ManualTimeProvider time = new ManualTimeProvider(0);
        RateLimiter[] instances = {
                create(algo, 100, 60_000, time), create(algo, 100, 60_000, time)
        };
        int threads = 32;
        int attemptsPerThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        List<Future<Object>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            RateLimiter limiter = instances[t % 2];
            futures.add(pool.submit(() -> {
                start.await();
                for (int k = 0; k < attemptsPerThread; k++) {
                    if (limiter.isAllowed("shared")) accepted.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<Object> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(100, accepted.get());
    }

    @Test
    void keyGetsATimeToLive() {
        RateLimiter limiter = create(Algorithm.FIXED_WINDOW, 3, 5_000, new ManualTimeProvider(0));
        limiter.isAllowed("k");

        Long ttl = redis.getExpire(prefix + "k", TimeUnit.MILLISECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 5_000, "ttl = " + ttl);
    }
}
