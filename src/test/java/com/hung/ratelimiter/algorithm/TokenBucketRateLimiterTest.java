package com.hung.ratelimiter.algorithm;

import com.hung.ratelimiter.ManualTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
class TokenBucketRateLimiterTest {
    private ManualTimeProvider time;
    private TokenBucketRateLimiter limiter;
    @BeforeEach
    void setUp() {
        time = new ManualTimeProvider(0);
        limiter = new TokenBucketRateLimiter(5,1.0,time);
    }
    private int send(int n){
        int accepted = 0;
        for(int k = 0; k < n; k++){
            if (limiter.isAllowed("u1")) accepted++;
        }
        return accepted;
    }
    @Test
    void burstEqualsCapacity(){
        assertEquals(5,send(6));
    }
    @Test
    void refillOverTime(){
        send(5);
        time.advance(3_000);
        assertEquals(3,send(4));
    }
    @Test
    void neverExceedCapacity(){
        send(5);
        time.advance(1_000_000);
        assertEquals(5,send(10));
    }
    @Test
    void retryAfterTellsHowLongToWait(){
        send(5);
        assertEquals(1_000,limiter.tryAcquire("u1").retryAfterMillis());
    }
    @Test
    void rejectsInvalidArguments(){
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(0,1.0,time));
    }
}
