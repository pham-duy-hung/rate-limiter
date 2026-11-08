package com.hung.ratelimiter.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ratelimiter.redis")
public record RedisRateLimitProperties(
        @DefaultValue("rl") String keyPrefix,
        @DefaultValue("true") boolean failOpen) {
}
