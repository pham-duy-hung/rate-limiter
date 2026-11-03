package com.hung.ratelimiter.algorithm;

import com.hung.ratelimiter.ManualTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixedWindowRateLimiterTest {
    private ManualTimeProvider time;
    private FixedWindowRateLimiter limiter;

    @BeforeEach
    void setUp() {
        time = new ManualTimeProvider(0);
        limiter = new FixedWindowRateLimiter(5,10_000,time);
    }
    private int send(int n){
        int accepted = 0;
        for(int k=0;k<n;k++){
            if(limiter.isAllowed("u1"))accepted++;
        }
        return accepted;
    }
    @Test
    void allowsUpToLimitThenBlocks(){
        assertEquals(5,send(5));
        assertFalse(limiter.isAllowed("u1"));
    }
    @Test
    void resetsInNextWindow(){
        send(5);
        time.advance(10_000);
        assertTrue(limiter.isAllowed("u1"));
    }
    @Test
    void keyAreIndependent(){
        send(5);
        assertTrue(limiter.isAllowed("u2"));
    }
    @Test
    void boundaryBurstLetsDoubleThroughWhichIsTheKnownFlaw(){
        time.set(9_500);
        int first = send(5);
        time.set(10_500);
        int second = send(5);
        assertEquals(10,first + second);
    }
    @Test
    void retryAterPointsToWindowEnd(){
        time.set(3_000);
        send(5);
        assertEquals(7_000,limiter.tryAcquire("u1").retryAfterMillis());
    }
    @Test
    void evictsIdleKeys() {
        limiter.isAllowed("a");
        limiter.isAllowed("b");
        assertEquals(2, limiter.trackedKeys());

        time.advance(100_000);
        limiter.isAllowed("c");          // key c mới
        limiter.evictIdle(60_000);

        assertEquals(1, limiter.trackedKeys());   // chỉ còn c
    }

}
