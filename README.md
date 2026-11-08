# Rate Limiter cho Spring Boot

![CI](https://github.com/pham-duy-hung/rate-limiter/actions/workflows/maven.yml/badge.svg)

Thư viện giới hạn tốc độ request (rate limiter) viết bằng **Java 25** và **Spring Boot 4**, cài đặt từ đầu ba thuật toán **Fixed Window**, **Sliding Window Counter** và **Token Bucket**. Chỉ cần gắn annotation `@RateLimit` lên một endpoint là endpoint đó được giới hạn; khi vượt giới hạn, client nhận mã **HTTP 429** kèm các header `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`.

Dự án tập trung vào việc **hiểu và so sánh các thuật toán**: lõi thuật toán là Java thuần (không phụ thuộc Spring), được kiểm thử kỹ, và được tích hợp vào Spring Boot như một lớp mỏng ở bên ngoài.

## Mục lục

- [Tính năng](#tính-năng)
- [Ba thuật toán](#ba-thuật-toán)
- [Bắt đầu nhanh](#bắt-đầu-nhanh)
- [Cách dùng](#cách-dùng)
- [Cấu hình](#cấu-hình)
- [Kiến trúc và quyết định thiết kế](#kiến-trúc-và-quyết-định-thiết-kế)
- [Kiểm thử](#kiểm-thử)
- [Hạn chế đã biết](#hạn-chế-đã-biết)
- [Kế hoạch tiếp theo](#kế-hoạch-tiếp-theo)

## Tính năng

- Ba thuật toán giới hạn cùng implement một interface `RateLimiter`, đổi qua lại bằng một tham số.
- Annotation `@RateLimit` đặt được trên từng method hoặc trên cả controller.
- Giới hạn theo **địa chỉ IP** hoặc theo **API key**; mỗi endpoint có bộ đếm riêng.
- Phản hồi 429 theo chuẩn HTTP, kèm header giúp client tự điều tiết.
- Cấu hình qua `application.yml`, có thể bật tắt toàn bộ.
- Dọn tự động các key không còn hoạt động để không rò rỉ bộ nhớ.
- Xử lý đa luồng đúng (khóa theo từng key), có test đồng thời.

## Ba thuật toán

| | Fixed Window | Sliding Window Counter | Token Bucket |
|---|---|---|---|
| **Ý tưởng** | Đếm request trong từng cửa sổ thời gian cố định, hết cửa sổ thì reset | Ước lượng số request trong cửa sổ trượt từ bộ đếm của cửa sổ hiện tại và cửa sổ trước | Xô chứa token, nạp đều theo thời gian, mỗi request tiêu một token |
| **Trạng thái mỗi key** | `count`, `windowStart` | `prev`, `curr`, `currWindowStart` | `tokens`, `lastRefill` |
| **Burst** | Lỗi ở ranh giới cửa sổ: có thể lọt gần 2 × limit | Hạn chế được burst ở ranh giới | Cho phép burst có kiểm soát (tối đa `capacity`) |
| **Độ chính xác** | Thấp | Khá (là ước lượng) | Tốt |
| **Bộ nhớ** | Rất ít | Ít | Rất ít |
| **Độ phức tạp** | O(1) | O(1) | O(1) |

**Công thức chính**

- Fixed Window: `windowStart = now - (now % windowSize)`; cho phép nếu `count < limit`.
- Sliding Window Counter: `estimated = prev × (1 - elapsedRatio) + curr`; cho phép nếu `estimated < limit`.
- Token Bucket: `tokens = min(capacity, tokens + elapsedSeconds × refillRate)`; cho phép nếu `tokens >= 1`. Token được nạp theo kiểu *lazy* khi có request đến, không cần thread nền.

### Vì sao mỗi endpoint demo dùng một thuật toán

| Endpoint | Thuật toán | Cấu hình | Lý do |
|---|---|---|---|
| `POST /api/login` | Sliding Window Counter | 5 request / 60 giây, theo IP | Chống dò mật khẩu: không để kẻ tấn công lợi dụng burst ở ranh giới cửa sổ |
| `GET /api/search` | Token Bucket | 10 request / 10 giây, theo IP (burst 10, nạp 1 token/giây) | Người dùng thật hay bấm dồn dập vài lần rồi nghỉ: cho phép burst ngắn nhưng giữ tốc độ trung bình |
| `GET /api/report` | Fixed Window | 3 request / 30 giây, theo API key | Hạn mức đơn giản, dễ hiểu theo gói dịch vụ; chấp nhận sai số ở ranh giới |
| `GET /api/ping` | (không giới hạn) | | Kiểm tra ứng dụng còn sống |

## Bắt đầu nhanh

**Yêu cầu:** JDK 25 và Maven (hoặc dùng IntelliJ IDEA).

```bash
git clone https://github.com/pham-duy-hung/rate-limiter.git
cd rate-limiter

# chạy toàn bộ test
mvn test

# chạy ứng dụng (cổng 8080)
mvn spring-boot:run
```

Nếu dùng IntelliJ IDEA: mở thư mục chứa `pom.xml`, chờ Maven nạp xong, mở `RateLimiterApplication.java` rồi bấm mũi tên xanh cạnh hàm `main`.

## Cách dùng

### Gắn annotation lên endpoint

```java
@RestController
@RequestMapping("/api")
public class DemoController {

    @PostMapping("/login")
    @RateLimit(algorithm = Algorithm.SLIDING_WINDOW_COUNTER,
               limit = 5, windowSeconds = 60, key = KeyBy.IP)
    public String login() {
        return "login attempt accepted";
    }

    @GetMapping("/search")
    @RateLimit(algorithm = Algorithm.TOKEN_BUCKET,
               limit = 10, windowSeconds = 10)
    public String search(@RequestParam(defaultValue = "") String q) {
        return "results for: " + q;
    }
}
```

| Thuộc tính | Ý nghĩa | Mặc định |
|---|---|---|
| `algorithm` | `FIXED_WINDOW`, `SLIDING_WINDOW_COUNTER` hoặc `TOKEN_BUCKET` | `TOKEN_BUCKET` |
| `limit` | Số request tối đa mỗi cửa sổ | lấy từ `ratelimiter.default-limit` |
| `windowSeconds` | Độ dài cửa sổ (giây) | lấy từ `ratelimiter.default-window-seconds` |
| `key` | Giới hạn theo `IP` hay `API_KEY` | `IP` |

Với Token Bucket, `limit` và `windowSeconds` được quy đổi thành `capacity = limit` và tốc độ nạp `limit / windowSeconds` token mỗi giây, nên ba thuật toán dùng chung một kiểu cấu hình "tối đa `limit` request mỗi `windowSeconds`".

### Thử bằng curl

Gọi dồn dập `/api/search`:

```bash
for i in $(seq 1 12); do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/search
done
```

Khoảng 10 request đầu trả `200`, sau đó trả `429`. Xem đầy đủ phản hồi khi bị chặn:

```bash
curl -i http://localhost:8080/api/search
```

```
HTTP/1.1 429
Retry-After: 1
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0
Content-Type: application/json;charset=UTF-8

{"error":"Too Many Requests","retryAfterSeconds":1}
```

*(Giá trị `Retry-After` thay đổi theo thời điểm gọi.)*

Giới hạn theo API key:

```bash
curl -i -H "X-API-Key: alice" http://localhost:8080/api/report
```

Hai API key khác nhau có bộ đếm riêng.

## Cấu hình

`src/main/resources/application.yml`:

```yaml
ratelimiter:
  enabled: true
  default-limit: 100
  default-window-seconds: 60
  api-key-header: X-API-Key
  trust-forwarded-for: false
  idle-eviction-seconds: 3600
```

| Khóa | Ý nghĩa | Mặc định |
|---|---|---|
| `enabled` | Bật hoặc tắt toàn bộ bộ giới hạn | `true` |
| `default-limit` | Giá trị `limit` dùng khi annotation không chỉ định | `100` |
| `default-window-seconds` | Giá trị `windowSeconds` dùng khi annotation không chỉ định | `60` |
| `api-key-header` | Tên header chứa API key | `X-API-Key` |
| `trust-forwarded-for` | Có lấy IP client từ header `X-Forwarded-For` hay không. **Chỉ bật khi ứng dụng nằm sau reverse proxy tin cậy**, vì header này có thể bị giả mạo | `false` |
| `idle-eviction-seconds` | Key không hoạt động quá thời gian này sẽ bị dọn khỏi bộ nhớ | `3600` |

## Kiến trúc và quyết định thiết kế

```
Request
   |
   v
RateLimitInterceptor --- đọc @RateLimit trên endpoint (không có thì cho qua)
   |        |
   |        +--> ClientKeyResolver   : xác định client (IP hoặc API key)
   |        +--> RateLimiterRegistry : mỗi endpoint một RateLimiter
   |                    |
   |                    v
   |             RateLimiter  (lõi Java thuần, không biết gì về Spring)
   |
   +--> cho phép : đặt header X-RateLimit-*, chạy tiếp controller
   +--> bị chặn  : trả 429 + Retry-After, controller không được chạy
```

```
src/main/java/com/hung/ratelimiter
├── core/        RateLimiter, RateLimitResult, TimeProvider, Factory (không phụ thuộc Spring)
├── algorithm/   FixedWindow, SlidingWindowCounter, TokenBucket
├── web/         @RateLimit, interceptor, registry, cấu hình (phần tích hợp Spring)
└── demo/        controller minh họa
```

Các quyết định đáng chú ý:

- **Lõi tách khỏi Spring.** Toàn bộ thuật toán là Java thuần nên dễ kiểm thử, dễ benchmark và có thể thay bằng bản lưu trạng thái ở nơi khác mà không sửa phần web.
- **`TimeProvider` thay cho gọi thời gian hệ thống trực tiếp.** Test điều khiển được thời gian bằng đồng hồ giả, không cần `Thread.sleep`, kết quả tất định.
- **Khóa theo từng key** (`synchronized` trên trạng thái của key) trên `ConcurrentHashMap`. `ConcurrentHashMap` chỉ bảo vệ thao tác trên map; chuỗi "đọc, so sánh, ghi" vẫn cần khóa để không vượt giới hạn khi nhiều luồng cùng gọi.
- **Dùng `HandlerInterceptor`** thay vì Servlet Filter hay AOP: nó đọc được annotation trên method và kiểm soát được response (429, header).
- **Mỗi endpoint một `RateLimiter`**, để quota của endpoint này không bị endpoint khác chiếm.
- **Dọn key không hoạt động** theo chu kỳ, ngưỡng dọn phải lớn hơn cửa sổ dài nhất (và lớn hơn thời gian nạp đầy xô với Token Bucket) để việc dọn không làm thay đổi hành vi.

## Kiểm thử

Bộ test chạy bằng `mvn test` (JUnit 5, Spring MockMvc), gồm:

- **Test hành vi từng thuật toán:** giới hạn, reset hoặc nạp lại theo thời gian, tái hiện lỗi burst ở ranh giới của Fixed Window và chứng minh Sliding Window Counter chặn được.
- **Test hợp đồng chung** chạy lặp cho cả ba thuật toán: `retryAfter` đủ để request kế tiếp được chấp nhận, `remaining` giảm dần, các key độc lập.
- **Test trường hợp biên:** tham số không hợp lệ, `limit = 1`, cửa sổ rất nhỏ, key `null`, nhảy thời gian rất lớn, đồng hồ nhảy lùi.
- **Test theo quy luật với dữ liệu ngẫu nhiên** (seed cố định, kết quả tái hiện được): Fixed Window khớp mô hình tham chiếu; Sliding Window Counter không bao giờ nhận quá `limit` trong một cửa sổ; Token Bucket không bao giờ tiêu quá `capacity` cộng số token đã nạp.
- **Test đa luồng:** 32 luồng cùng gọi một key, chạy lặp nhiều lần, số request được nhận không vượt giới hạn.
- **Test tích hợp web bằng MockMvc:** mã 429, header, `Retry-After`, tách bộ đếm theo IP và theo endpoint, API key, chế độ tắt.

Mỗi lần đẩy code lên GitHub, toàn bộ test được chạy tự động bằng GitHub Actions (xem huy hiệu CI ở đầu trang).

## Hạn chế đã biết

- **Trạng thái chỉ nằm trong bộ nhớ của một instance.** Chạy nhiều instance thì mỗi instance đếm riêng. Hướng giải quyết (Redis và Lua script) nằm trong kế hoạch tiếp theo.
- **API key chưa được xác thực.** Trong bản demo, bất kỳ giá trị nào ở header cũng được coi là một client. Kẻ tấn công có thể bịa nhiều key để mỗi key có bộ đếm riêng. Trong hệ thống thật, rate limiter nên chạy sau bước xác thực và chỉ dùng key hợp lệ.
- **`X-Forwarded-For` chỉ nên bật khi có proxy tin cậy**, vì client có thể tự gửi giá trị giả.
- **Giới hạn theo IP có thể chặn oan** những người dùng chung một địa chỉ IP công cộng (sau NAT hoặc mạng công ty).
- **Đồng hồ nhảy lùi:** Token Bucket không nạp thêm token khi thời gian lùi; Fixed Window và Sliding Window Counter khi lùi sang một cửa sổ cũ sẽ tính lại bộ đếm, tức tạm thời nới lỏng giới hạn.
- **Sliding Window Counter là ước lượng**, dựa trên giả định request ở cửa sổ trước phân bố đều. Giá trị `Retry-After` của thuật toán này là cận trên an toàn, không phải thời điểm sớm nhất có thể thử lại.

## Kế hoạch tiếp theo

- [x] Ba thuật toán với bộ test đầy đủ
- [x] Tích hợp Spring Boot với `@RateLimit`
- [x] Chạy test tự động bằng GitHub Actions
- [ ] Chế độ phân tán: lưu trạng thái bằng Redis, dùng Lua script để thao tác nguyên tử
- [ ] Benchmark (k6, JMH): thông lượng, độ trễ p50/p95/p99, bộ nhớ theo số lượng key, so sánh in-memory với Redis
- [ ] Số liệu giám sát (Micrometer, Prometheus)

## Tác giả

Hung
