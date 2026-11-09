package com.hung.ratelimiter.bench;

import com.hung.ratelimiter.ManualTimeProvider;
import com.hung.ratelimiter.core.Algorithm;
import com.hung.ratelimiter.core.RateLimiter;
import com.hung.ratelimiter.core.RateLimiterFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Đo độ chính xác của từng thuật toán bằng mô phỏng thời gian (không cần chờ thật).
 * Với mỗi kịch bản lưu lượng, đo hai thứ:
 * - maxInWindow: số request nhiều nhất được nhận trong BẤT KỲ khoảng thời gian dài bằng một cửa sổ;
 * - avgPerWindow: số request trung bình được nhận mỗi cửa sổ trong cả quá trình.
 * Cả hai được chia cho limit để thành tỉ lệ.
 */
public class AccuracyExperiment {

    static final int LIMIT = 100;
    static final long WINDOW_MS = 10_000;
    static final long DURATION_MS = 10 * 60_000;   // mô phỏng 10 phút

    interface Scenario {
        List<Long> timestamps();

        String name();
    }

    /**
     * Mỗi mili giây một request, liên tục.
     */
    static List<Long> flood() {
        List<Long> t = new ArrayList<>();
        for (long ms = 0; ms < DURATION_MS; ms++) t.add(ms);
        return t;
    }

    /**
     * Chỉ gửi dồn dập trong 1 giây quanh mỗi ranh giới cửa sổ (±500 ms), im lặng ở thời gian còn lại.
     */
    static List<Long> boundaryAttack() {
        List<Long> t = new ArrayList<>();
        for (long boundary = WINDOW_MS; boundary < DURATION_MS; boundary += WINDOW_MS) {
            for (long ms = boundary - 500; ms < boundary + 500; ms++) t.add(ms);
        }
        return t;
    }

    /**
     * Các đợt dồn dập ngẫu nhiên xen kẽ những quãng nghỉ ngẫu nhiên.
     */
    static List<Long> randomBursts(long seed) {
        Random rnd = new Random(seed);
        List<Long> t = new ArrayList<>();
        long now = 0;
        while (now < DURATION_MS) {
            long burst = 100 + rnd.nextInt(2_000);
            for (long ms = now; ms < Math.min(now + burst, DURATION_MS); ms++) t.add(ms);
            now += burst + rnd.nextInt(8_000);
        }
        return t;
    }

    /**
     * Đều đặn, nhanh gấp 1,5 lần giới hạn.
     */
    static List<Long> steadyOverLimit() {
        List<Long> t = new ArrayList<>();
        double gapMs = WINDOW_MS / (LIMIT * 1.5);
        for (double ms = 0; ms < DURATION_MS; ms += gapMs) t.add((long) ms);
        return t;
    }

    static List<Long> accepted(Algorithm algo, List<Long> requests) {
        ManualTimeProvider clock = new ManualTimeProvider(0);
        RateLimiter limiter = RateLimiterFactory.create(algo, LIMIT, WINDOW_MS, clock);
        List<Long> ok = new ArrayList<>();
        for (long ts : requests) {
            clock.set(ts);
            if (limiter.isAllowed("attacker")) ok.add(ts);
        }
        return ok;
    }

    /**
     * Số request nhiều nhất trong một khoảng [t, t + WINDOW_MS) bất kỳ (hai con trỏ).
     */
    static int maxInAnyWindow(List<Long> ts) {
        int best = 0, left = 0;
        for (int right = 0; right < ts.size(); right++) {
            while (ts.get(right) - ts.get(left) >= WINDOW_MS) left++;
            best = Math.max(best, right - left + 1);
        }
        return best;
    }

    public static void main(String[] args) {
        List<String> names = List.of("Flood", "BoundaryAttack", "RandomBursts", "Steady1.5x");
        List<Supplier<List<Long>>> gens = List.of(
                AccuracyExperiment::flood, AccuracyExperiment::boundaryAttack,
                () -> randomBursts(42), AccuracyExperiment::steadyOverLimit);

        System.out.printf("limit = %d, window = %d ms, mô phỏng %d phút%n%n", LIMIT, WINDOW_MS, DURATION_MS / 60_000);
        System.out.println("| Kịch bản | Thuật toán | Tối đa trong 1 cửa sổ bất kỳ | Tỉ lệ so với limit | Trung bình mỗi cửa sổ | Tỉ lệ |");
        System.out.println("|---|---|---|---|---|---|");
        for (int s = 0; s < names.size(); s++) {
            List<Long> requests = gens.get(s).get();
            for (Algorithm algo : Algorithm.values()) {
                List<Long> ok = accepted(algo, requests);
                int max = maxInAnyWindow(ok);
                double avg = ok.size() / (DURATION_MS / (double) WINDOW_MS);
                System.out.printf("| %s | %s | %d | %.2f | %.1f | %.2f |%n",
                        names.get(s), algo, max, max / (double) LIMIT, avg, avg / LIMIT);
            }
        }
    }
}
