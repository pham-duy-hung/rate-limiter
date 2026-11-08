package com.hung.ratelimiter.web;

import com.hung.ratelimiter.core.RateLimitResult;
import com.hung.ratelimiter.core.RateLimiter;
import com.hung.ratelimiter.core.RateLimiterUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterRegistry registry;
    private final ClientKeyResolver keyResolver;
    private final RateLimitProperties props;

    public RateLimitInterceptor(RateLimiterRegistry registry,
                                ClientKeyResolver keyResolver,
                                RateLimitProperties props) {
        this.registry = registry;
        this.keyResolver = keyResolver;
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        if (!props.enabled() || !(handler instanceof HandlerMethod method)) {
            return true;
        }

        RateLimit config = method.getMethodAnnotation(RateLimit.class);
        if (config == null) {
            config = AnnotatedElementUtils.findMergedAnnotation(
                    method.getBeanType(), RateLimit.class);
        }
        if (config == null) {
            return true;   // endpoint không bị giới hạn
        }

        int limit = config.limit() > 0 ? config.limit() : props.defaultLimit();
        long windowSeconds = config.windowSeconds() > 0
                ? config.windowSeconds() : props.defaultWindowSeconds();

        String endpointId = method.getBeanType().getName()
                + "#" + method.getMethod().getName();
        RateLimiter limiter = registry.get(
                endpointId, config.algorithm(), limit, windowSeconds * 1000);

        String clientKey = keyResolver.resolve(request, config.key());
        RateLimitResult result;
        try {
            result = limiter.tryAcquire(clientKey);
        } catch (RateLimiterUnavailableException e) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setHeader("Retry-After", "1");
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"Rate limiter unavailable\"}");
            return false;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        if (result.remaining() >= 0) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        }

        if (result.allowed()) {
            return true;
        }

        long retryAfterSeconds = Math.max(1, (result.retryAfterMillis() + 999) / 1000);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"Too Many Requests\","
                + "\"retryAfterSeconds\":" + retryAfterSeconds + "}");
        return false;   // dừng tại đây, controller không được chạy
    }
}
