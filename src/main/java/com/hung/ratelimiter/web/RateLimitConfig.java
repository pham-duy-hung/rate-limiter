package com.hung.ratelimiter.web;

import com.hung.ratelimiter.core.SystemTimeProvider;
import com.hung.ratelimiter.core.TimeProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
@EnableScheduling
public class RateLimitConfig {

    @Bean
    public TimeProvider timeProvider() {
        return new SystemTimeProvider();
    }
}
