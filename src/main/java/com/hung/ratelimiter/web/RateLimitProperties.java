package com.hung.ratelimiter.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
    @ConfigurationProperties(prefix = "ratelimiter")
public record RateLimitProperties (
        @DefaultValue("true") boolean enabled,
        @DefaultValue("100") int defaultLimit,
        @DefaultValue("60") long defaultWindowSeconds,
        @DefaultValue("X-API-Key") String apiKeyHeader,
        @DefaultValue("false") boolean trustForwardedFor,
        @DefaultValue("3600") long idleEvictionSeconds) { }

