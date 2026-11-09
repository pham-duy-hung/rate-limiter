package com.hung.ratelimiter.bench;

import com.hung.ratelimiter.core.Algorithm;
import com.hung.ratelimiter.core.RateLimiter;
import com.hung.ratelimiter.core.RateLimiterFactory;
import com.hung.ratelimiter.core.SystemTimeProvider;

/**
 * Ước lượng bộ nhớ heap mà bộ giới hạn chiếm khi theo dõi N key khác nhau.
 */
public class MemoryExperiment {

    static long usedMemory() {
        Runtime rt = Runtime.getRuntime();
        for (int i = 0; i < 5; i++) {
            System.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return rt.totalMemory() - rt.freeMemory();
    }

    public static void main(String[] args) {
        int[] sizes = {10_000, 100_000, 1_000_000};
        System.out.println("| Thuật toán | Số key | Heap tăng thêm (MB) | Byte mỗi key |");
        System.out.println("|---|---|---|---|");
        for (Algorithm algo : Algorithm.values()) {
            for (int n : sizes) {
                long before = usedMemory();
                RateLimiter limiter = RateLimiterFactory.create(algo, 100, 60_000, new SystemTimeProvider());
                for (int i = 0; i < n; i++) {
                    limiter.isAllowed("ip:10." + (i >> 16 & 255) + "." + (i >> 8 & 255) + "." + (i & 255));
                }
                long after = usedMemory();
                double mb = (after - before) / 1024.0 / 1024.0;
                System.out.printf("| %s | %,d | %.1f | %.0f |%n", algo, n, mb, (after - before) / (double) n);
                if (limiter.hashCode() == 42) System.out.print("");   // giữ limiter còn sống đến đây
                limiter = null;
            }
        }
    }
}
