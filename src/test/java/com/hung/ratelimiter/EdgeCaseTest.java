package com.hung.ratelimiter;

import com.hung.ratelimiter.algorithm.TokenBucketRateLimiter;
import com.hung.ratelimiter.core.Algorithm;
import com.hung.ratelimiter.core.RateLimiter;
import com.hung.ratelimiter.core.RateLimiterFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

 class EdgeCaseTest {
     @ParameterizedTest
     @EnumSource(Algorithm.class)
     void rejectsInvalidConfiguration(Algorithm algo) {
         ManualTimeProvider time = new ManualTimeProvider(0);
         assertThrows(IllegalArgumentException.class,
                 () -> RateLimiterFactory.create(algo, 0, 1_000, time));
         assertThrows(IllegalArgumentException.class,
                 () -> RateLimiterFactory.create(algo, -1, 1_000, time));
         assertThrows(IllegalArgumentException.class,
                 () -> RateLimiterFactory.create(algo, 5, 0, time));
         assertThrows(NullPointerException.class,
                 () -> RateLimiterFactory.create(algo, 5, 1_000, null));
     }

     @ParameterizedTest
     @EnumSource(Algorithm.class)
     void rejectsNullKey(Algorithm algo) {
         RateLimiter limiter = RateLimiterFactory.create(
                 algo, 1, 1_000, new ManualTimeProvider(0));
         assertThrows(NullPointerException.class, () -> limiter.tryAcquire(null));
     }

     @ParameterizedTest
     @EnumSource(Algorithm.class)
     void limitOneAllowsExactlyOnePerWindow(Algorithm algo) {
         ManualTimeProvider time = new ManualTimeProvider(0);
         RateLimiter limiter = RateLimiterFactory.create(algo, 1, 1_000, time);

         assertTrue(limiter.isAllowed("k"));
         assertFalse(limiter.isAllowed("k"));
         time.advance(1_001);
         assertTrue(limiter.isAllowed("k"));
     }

     @ParameterizedTest
     @EnumSource(Algorithm.class)
     void tinyWindowStillWorks(Algorithm algo) {
         ManualTimeProvider time = new ManualTimeProvider(0);
         RateLimiter limiter = RateLimiterFactory.create(algo, 2, 1, time);  // 2 req/ms

         assertTrue(limiter.isAllowed("k"));
         assertTrue(limiter.isAllowed("k"));
         time.advance(5);
         assertTrue(limiter.isAllowed("k"));
     }

     @Test
     void tokenBucketCapsAtCapacityAfterHugeTimeJump() {
         ManualTimeProvider time = new ManualTimeProvider(0);
         TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 1.0, time);
         for (int i = 0; i < 5; i++) limiter.isAllowed("k");

         time.advance(315_360_000_000L);          // khoảng 10 năm
         int accepted = 0;
         for (int i = 0; i < 10; i++) {
             if (limiter.isAllowed("k")) accepted++;
         }
         assertEquals(5, accepted);
     }

     @Test
     void tokenBucketDoesNotRefillWhenClockGoesBackwards() {
         ManualTimeProvider time = new ManualTimeProvider(10_000);
         TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 1.0, time);
         for (int i = 0; i < 5; i++) limiter.isAllowed("k");   // cạn xô

         time.set(1_000);                         // đồng hồ nhảy lùi 9 giây
         assertFalse(limiter.isAllowed("k"));
         time.set(10_000);                        // quay lại giờ cũ
         assertFalse(limiter.isAllowed("k"));     // không được hồi token từ thời gian ảo
     }
 }

