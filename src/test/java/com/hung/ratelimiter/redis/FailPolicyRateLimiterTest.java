package com.hung.ratelimiter.redis;

import com.hung.ratelimiter.ManualTimeProvider;
import com.hung.ratelimiter.core.RateLimitResult;
import com.hung.ratelimiter.core.RateLimiter;
import com.hung.ratelimiter.core.RateLimiterUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class FailPolicyRateLimiterTest {

    /** Redis "chết": không có ai lắng nghe ở cổng này. */
    private static RateLimiter deadRedisLimiter(boolean failOpen) {
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(200))
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", 6390), client);
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();

        RateLimiter real = new RedisFixedWindowRateLimiter(
                template, new ManualTimeProvider(0), "x:", 5, 60_000);
        return new FailPolicyRateLimiter(real, failOpen);
    }

    @Test
    void failOpenAllowsTheRequestAndReportsUnknownRemaining() {
        RateLimitResult result = deadRedisLimiter(true).tryAcquire("k");
        assertTrue(result.allowed());
        assertEquals(-1, result.remaining());
    }

    @Test
    void failClosedThrowsUnavailable() {
        assertThrows(RateLimiterUnavailableException.class,
                () -> deadRedisLimiter(false).tryAcquire("k"));
    }
}
