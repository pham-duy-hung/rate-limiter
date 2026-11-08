package com.hung.ratelimiter.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

final class RedisScripts {

    private RedisScripts() {}

    @SuppressWarnings("rawtypes")
    static RedisScript<List> load(String classpathLocation) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(classpathLocation));
        script.setResultType(List.class);
        return script;
    }
}
