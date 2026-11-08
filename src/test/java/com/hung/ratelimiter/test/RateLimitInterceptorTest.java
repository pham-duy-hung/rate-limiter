package com.hung.ratelimiter.test;

import com.hung.ratelimiter.ManualTimeProvider;
import com.hung.ratelimiter.demo.DemoController;
import com.hung.ratelimiter.web.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitInterceptorTest {

    private ManualTimeProvider time;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = buildMvc(true);
    }

    private MockMvc buildMvc(boolean enabled) {
        time = new ManualTimeProvider(0);
        RateLimitProperties props =
                new RateLimitProperties(enabled, 100, 60, "X-API-Key", false, 3600);
        RateLimiterRegistry registry = new RateLimiterRegistry(time);
        ClientKeyResolver keys = new ClientKeyResolver(props);
        return MockMvcBuilders.standaloneSetup(new DemoController())
                .addInterceptors(new RateLimitInterceptor(registry, keys, props))
                .build();
    }

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    void blocksTheSixthLoginWith429AndHeaders() throws Exception {
        mvc.perform(post("/api/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "5"))
                .andExpect(header().string("X-RateLimit-Remaining", "4"));
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/login")).andExpect(status().isOk());
        }

        MvcResult blocked = mvc.perform(post("/api/login"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("Retry-After"))
                .andReturn();

        String body = blocked.getResponse().getContentAsString();
        assertTrue(body.contains("Too Many Requests"));
    }

    @Test
    void requestIsAllowedAgainAfterRetryAfter() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/login")).andExpect(status().isOk());
        }
        MvcResult blocked = mvc.perform(post("/api/login"))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        long retryAfterSeconds =
                Long.parseLong(blocked.getResponse().getHeader("Retry-After"));
        time.advance(retryAfterSeconds * 1000);

        mvc.perform(post("/api/login")).andExpect(status().isOk());
    }

    @Test
    void differentIpsHaveSeparateCounters() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/login").with(fromIp("10.0.0.1")))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/login").with(fromIp("10.0.0.1")))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/login").with(fromIp("10.0.0.2")))
                .andExpect(status().isOk());
    }

    @Test
    void endpointsDoNotShareCounters() throws Exception {
        for (int i = 0; i < 6; i++) {
            mvc.perform(post("/api/login"));
        }
        mvc.perform(post("/api/login")).andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/search").param("q", "java")).andExpect(status().isOk());
    }

    @Test
    void unannotatedEndpointIsNeverLimited() throws Exception {
        for (int i = 0; i < 50; i++) {
            mvc.perform(get("/api/ping")).andExpect(status().isOk());
        }
    }

    @Test
    void apiKeyHeaderDefinesTheClient() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/report").header("X-API-Key", "alice"))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/report").header("X-API-Key", "alice"))
                .andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/report").header("X-API-Key", "bob"))
                .andExpect(status().isOk());
    }

    @Test
    void doesNothingWhenDisabled() throws Exception {
        MockMvc disabled = buildMvc(false);
        for (int i = 0; i < 20; i++) {
            disabled.perform(post("/api/login")).andExpect(status().isOk());
        }
    }
}
