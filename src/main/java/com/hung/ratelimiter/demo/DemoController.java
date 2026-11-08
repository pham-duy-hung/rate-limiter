package com.hung.ratelimiter.demo;

import com.hung.ratelimiter.core.Algorithm;
import com.hung.ratelimiter.web.KeyBy;
import com.hung.ratelimiter.web.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DemoController {

    /** Không giới hạn, dùng để kiểm tra ứng dụng sống. */
    @GetMapping("/ping")
    public String ping() {
        return "ok";
    }

    /**
     * Đăng nhập: chống dò mật khẩu. Sliding Window Counter vì không muốn
     * kẻ tấn công lợi dụng burst ở ranh giới cửa sổ.
     */
    @PostMapping("/login")
    @RateLimit(algorithm = Algorithm.SLIDING_WINDOW_COUNTER,
            limit = 5, windowSeconds = 60, key = KeyBy.IP)
    public String login() {
        return "login attempt accepted";
    }

    /**
     * Tìm kiếm: người dùng thật hay bấm dồn dập rồi nghỉ. Token Bucket cho
     * phép burst ngắn (10 lượt) nhưng giữ tốc độ trung bình 1 lượt/giây.
     */
    @GetMapping("/search")
    @RateLimit(algorithm = Algorithm.TOKEN_BUCKET,
            limit = 10, windowSeconds = 10, key = KeyBy.IP)
    public String search(@RequestParam(defaultValue = "") String q) {
        return "results for: " + q;
    }

    /**
     * Báo cáo: hạn mức theo gói dịch vụ, đơn giản dễ hiểu. Fixed Window,
     * tính theo API key.
     */
    @GetMapping("/report")
    @RateLimit(algorithm = Algorithm.FIXED_WINDOW,
            limit = 3, windowSeconds = 30, key = KeyBy.API_KEY)
    public String report() {
        return "report generated";
    }
}
