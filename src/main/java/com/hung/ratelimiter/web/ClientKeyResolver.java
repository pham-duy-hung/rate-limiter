package com.hung.ratelimiter.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientKeyResolver {

    private final RateLimitProperties props;

    public ClientKeyResolver(RateLimitProperties props) {
        this.props = props;
    }

    public String resolve(HttpServletRequest request, KeyBy keyBy) {
        if (keyBy == KeyBy.API_KEY) {
            String apiKey = request.getHeader(props.apiKeyHeader());
            if (apiKey != null && !apiKey.isBlank()) {
                return "key:" + apiKey.trim();
            }
            // không có API key thì trả về IP
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        if (props.trustForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();   // phần tử đầu = client gốc
            }
        }
        return request.getRemoteAddr();
    }
}
