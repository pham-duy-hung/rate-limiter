package com.hung.ratelimiter.algorithm;

import com.hung.ratelimiter.ManualTimeProvider;
import com.hung.ratelimiter.core.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowCounterRateLimiterTest {
    private ManualTimeProvider time;
    private SlidingWindowCounterRateLimiter limiter;
    @BeforeEach
    void setUp() {
        time = new ManualTimeProvider(0);
        limiter = new SlidingWindowCounterRateLimiter(100, 60_000, time);
    }
    private int send(int n){
        int accepted = 0;
        for (int k = 0; k < n; k++) {
            if (limiter.isAllowed("u1")) accepted++;
        }
        return accepted;

    }
    @Test
    void blocksTheBoundaryBurstThatFixedWindowAllows() {
        time.set(59_000);
        assertEquals(100, send(100));
        time.set(61_000);
        // estimated bắt đầu ~98,3333 nên chỉ lọt 2 request, không phải 100
        assertEquals(2, send(100));
    }
    @Test
    void worksExampleFromTheory(){
        time.set(0);
        assertEquals(80, send(80)); // cửa số trc có 80request
        time.set(75_000); //giaay thứ 15 của cửa sổ kế tiếp
        assertEquals(30, send(30)); // cửa sổ hiêện tạo 30 request
        RateLimitResult r = limiter.tryAcquire("u1");
        assertTrue(r.allowed()); // estunated = 80*0.75 + 30 = 90<100
        assertEquals(9, r.remaining()); // sau khi nhaanj: 91 conf 9
    }
    @Test
    void forgetsPreviousWindowAfterLongIdle() {
        send(100);
        time.set(200_000);  // bỏ qua nhiều cửa sổ
        assertEquals(100, send(150));
    }

}

