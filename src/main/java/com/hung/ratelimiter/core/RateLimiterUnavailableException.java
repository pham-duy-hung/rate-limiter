package com.hung.ratelimiter.core;

/** Ném ra khi bộ giới hạn không hoạt động được (ví dụ Redis không liên lạc được). */
public class RateLimiterUnavailableException extends RuntimeException {

    public RateLimiterUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
