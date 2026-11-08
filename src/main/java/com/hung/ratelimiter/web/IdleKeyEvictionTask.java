package com.hung.ratelimiter.web;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IdleKeyEvictionTask {

    private final RateLimiterRegistry registry;
    private final RateLimitProperties props;

    public IdleKeyEvictionTask(RateLimiterRegistry registry,
                               RateLimitProperties props) {
        this.registry = registry;
        this.props = props;
    }

    @Scheduled(fixedDelay = 60_000)
    public void evict() {
        registry.evictIdle(props.idleEvictionSeconds() * 1000);
    }
}
