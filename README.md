# Rate Limiter cho Spring Boot

![CI](https://github.com/pham-duy-hung/rate-limiter/actions/workflows/maven.yml/badge.svg)

Thư viện giới hạn tốc độ request (rate limiter) viết bằng **Java 25** và **Spring Boot 4**, cài đặt từ đầu ba thuật toán **Fixed Window**, **Sliding Window Counter** và **Token Bucket**. Chỉ cần gắn annotation `@RateLimit` lên một endpoint là endpoint đó được giới hạn; khi vượt giới hạn, client nhận mã **HTTP 429** kèm các header `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`.

Trạng thái đếm có thể lưu **trong bộ nhớ** (một instance) hoặc **trong Redis** (nhiều instance dùng chung một bộ đếm, thao tác nguyên tử bằng Lua script). Đổi giữa hai chế độ chỉ bằng một dòng cấu hình.

Dự án tập trung vào việc **hiểu và so sánh các thuật toán**: lõi thuật toán là Java thuần (không phụ thuộc Spring), được kiểm thử kỹ, và được tích hợp vào Spring Boot như một lớp mỏng ở bên ngoài.

## Mục lục

- [Tính năng](#tính-năng)
- [Ba thuật toán](#ba-thuật-toán)
- [Bắt đầu nhanh](#bắt-đầu-nhanh)
- [Cách dùng](#cách-dùng)
- [Cấu hình](#cấu-hình)
- [Chế độ phân tán với Redis](#chế-độ-phân-tán-với-redis)
- [Kiến trúc và quyết định thiết kế](#kiến-trúc-và-quyết-định-thiết-kế)
- [Kiểm thử](#kiểm-thử)
- [Hạn chế đã biết](#hạn-chế-đã-biết)
- [Kế hoạch tiếp theo](#kế-hoạch-tiếp-theo)

## Tính năng

- Ba thuật toán giới hạn cùng implement một interface `RateLimiter`, đổi qua lại bằng một tham số.
- Annotation `@RateLimit` đặt được trên từng method hoặc trên cả controller.
- Giới hạn theo **địa chỉ IP** hoặc theo **API key**; mỗi endpoint có bộ đếm riêng.
- Phản hồi 429 theo chuẩn HTTP, kèm header giúp client tự điều tiết.
- **Chế độ phân tán bằng Redis:** nhiều instance ứng dụng dùng chung một bộ đếm; mỗi thuật toán là một Lua script chạy nguyên tử trên Redis.
- **Chính sách khi Redis lỗi:** chọn cho request đi qua (fail-open) hoặc trả 503 (fail-closed).
- Cấu hình qua `application.yml`, có thể bật tắt toàn bộ.
- Tự dọn các key không còn hoạt động: bằng chu kỳ quét ở chế độ bộ nhớ, bằng TTL của Redis ở chế độ phân tán.
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

**Yêu cầu:** JDK 25 và Maven (hoặc dùng IntelliJ IDEA). Docker chỉ cần khi dùng chế độ Redis hoặc muốn chạy các test tích hợp Redis.

```bash
git clone https://github.com/pham-duy-hung/rate-limiter.git
cd rate-limiter

# chạy toàn bộ test (test cần Redis sẽ tự bỏ qua nếu Redis không chạy)
mvn test

# chạy ứng dụng ở chế độ bộ nhớ (cổng 8080)
mvn spring-boot:run
```

Nếu dùng IntelliJ IDEA: mở thư mục chứa `pom.xml`, chờ Maven nạp xong, mở `RateLimiterApplication.java` rồi bấm mũi tên xanh cạnh hàm `main`.

Để dùng chế độ Redis, xem [Chế độ phân tán với Redis](#chế-độ-phân-tán-với-redis).

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
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 200ms
      connect-timeout: 200ms

ratelimiter:
  enabled: true
  default-limit: 100
  default-window-seconds: 60
  api-key-header: X-API-Key
  trust-forwarded-for: false
  idle-eviction-seconds: 3600
  store: memory            # memory hoặc redis
  redis:
    key-prefix: rl
    fail-open: true
```

| Khóa | Ý nghĩa | Mặc định |
|---|---|---|
| `ratelimiter.enabled` | Bật hoặc tắt toàn bộ bộ giới hạn | `true` |
| `ratelimiter.default-limit` | Giá trị `limit` dùng khi annotation không chỉ định | `100` |
| `ratelimiter.default-window-seconds` | Giá trị `windowSeconds` dùng khi annotation không chỉ định | `60` |
| `ratelimiter.api-key-header` | Tên header chứa API key | `X-API-Key` |
| `ratelimiter.trust-forwarded-for` | Có lấy IP client từ header `X-Forwarded-For` hay không. **Chỉ bật khi ứng dụng nằm sau reverse proxy tin cậy**, vì header này có thể bị giả mạo | `false` |
| `ratelimiter.idle-eviction-seconds` | Key không hoạt động quá thời gian này sẽ bị dọn khỏi bộ nhớ (chỉ áp dụng cho chế độ `memory`; ở chế độ `redis` đã có TTL) | `3600` |
| `ratelimiter.store` | Nơi lưu trạng thái đếm: `memory` (trong bộ nhớ của instance) hoặc `redis` | `memory` |
| `ratelimiter.redis.key-prefix` | Tiền tố của mọi key trong Redis | `rl` |
| `ratelimiter.redis.fail-open` | Khi Redis không dùng được: `true` cho request đi qua, `false` trả 503 | `true` |
| `spring.data.redis.timeout`, `connect-timeout` | Thời gian chờ Redis. Nên đặt ngắn (ví dụ `200ms`) để Redis treo không kéo theo cả ứng dụng treo | tùy cấu hình Spring |

Các giá trị cấu hình cũng có thể truyền qua dòng lệnh, ví dụ `--ratelimiter.store=redis`.

## Chế độ phân tán với Redis

### Vấn đề

Với chế độ bộ nhớ, mỗi instance của ứng dụng đếm riêng. Nếu giới hạn là 5 lần đăng nhập mỗi phút và bạn chạy 3 instance sau một bộ cân bằng tải, kẻ tấn công thực tế được tới 15 lần mỗi phút. Trạng thái đếm cần được dùng chung.

### Giải pháp

Trạng thái đếm được lưu trong Redis, mỗi client một key dạng Hash. Thao tác "đọc trạng thái, tính, ghi lại" phải nguyên tử, nên mỗi thuật toán được viết thành một **Lua script** chạy trọn vẹn trên Redis, chỉ mất một lần gọi mạng.

| Cách | Đánh giá |
|---|---|
| `GET` rồi `SET` từ ứng dụng | Sai: hai instance có thể cùng đọc `count = 4` rồi cùng cho phép |
| `INCR` kèm `EXPIRE` | Đủ cho Fixed Window đơn giản, không diễn đạt được hai thuật toán còn lại |
| `MULTI` / `EXEC` | Không đọc được kết quả giữa chừng để rẽ nhánh |
| `WATCH` (khóa lạc quan) | Đúng nhưng phải thử lại khi xung đột, tốn nhiều lần gọi mạng |
| **Lua script** | Nguyên tử vì Redis chạy script như một lệnh duy nhất, một lần gọi mạng |

Script nằm ở `src/main/resources/redis/`: `fixed_window.lua`, `sliding_window_counter.lua`, `token_bucket.lua`. Công thức giống hệt bản Java.

### Cấu trúc dữ liệu và TTL

Tên key: `rl:<thuật toán>:<endpoint>:<ip hoặc key của client>`, ví dụ `rl:sliding_window_counter:com.hung.ratelimiter.demo.DemoController#login:ip:203.0.113.5`.

| Thuật toán | Các trường trong Hash | TTL của key | Lý do |
|---|---|---|---|
| Fixed Window | `ws`, `count` | `windowMs` | Trạng thái chỉ có ý nghĩa đến hết cửa sổ hiện tại |
| Sliding Window Counter | `cs`, `prev`, `curr` | `2 × windowMs` | Bộ đếm hiện tại còn ảnh hưởng đến hết cửa sổ kế tiếp |
| Token Bucket | `tokens`, `ts` | thời gian nạp đầy xô + 1 giây | Sau đó xô đã đầy lại, giống hệt key mới |

Redis tự xóa key hết hạn nên không cần chu kỳ quét như ở chế độ bộ nhớ. Số token là số thực nên được lưu dạng chuỗi 17 chữ số có nghĩa (`%.17g`) để ghi rồi đọc lại không làm sai lệch giá trị.

### Chạy thử

Chạy Redis bằng Docker:

```bash
docker compose up -d
docker exec -it ratelimiter-redis redis-cli ping    # PONG
```

`docker-compose.yml` dùng image `redis:7-alpine`, tắt ghi dữ liệu xuống đĩa vì bộ đếm chỉ có ý nghĩa trong thời gian ngắn.

Chạy hai instance cùng dùng Redis:

```bash
mvn -DskipTests package
java -jar target/rate-limiter-1.0-SNAPSHOT.jar --server.port=8080 --ratelimiter.store=redis
java -jar target/rate-limiter-1.0-SNAPSHOT.jar --server.port=8081 --ratelimiter.store=redis
```

`/api/login` cho phép 5 lần mỗi phút theo IP. Gọi 3 lần vào mỗi instance:

```bash
for i in 1 2 3; do curl -s -o /dev/null -w "8080: %{http_code}\n" -X POST http://localhost:8080/api/login; done
for i in 1 2 3; do curl -s -o /dev/null -w "8081: %{http_code}\n" -X POST http://localhost:8081/api/login; done
```

| Chế độ | Kết quả 6 request | Giải thích |
|---|---|---|
| `store=redis` | 5 lần `200`, lần thứ sáu `429` | Hai instance dùng chung một bộ đếm, tổng đúng bằng giới hạn |
| `store=memory` | Cả 6 lần `200` | Mỗi instance đếm riêng, mỗi cái cho phép 5 lần |

### Khi Redis không dùng được

| Chính sách | Hành vi | Khi nào chọn |
|---|---|---|
| `fail-open: true` | Request đi qua, bỏ header `X-RateLimit-Remaining`, ghi log cảnh báo | Ưu tiên ứng dụng luôn sống (tìm kiếm, xem nội dung) |
| `fail-open: false` | Trả `503 Service Unavailable` kèm `Retry-After: 1` | Ưu tiên an toàn (đăng nhập, gửi OTP) |

Thời gian chờ Redis ngắn (`timeout: 200ms`) giúp ứng dụng nhanh chóng nhận ra sự cố thay vì treo theo Redis. Khi Redis hoạt động lại, ứng dụng tự hoạt động lại, không cần khởi động lại.

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
   |             LimiterCreator  (chọn bằng ratelimiter.store)
   |               |                          |
   |      3 thuật toán trong bộ nhớ    3 thuật toán trên Redis (Lua)
   |               |                          |
   |               |                   FailPolicyRateLimiter (fail-open / fail-closed)
   |               v                          v
   |             RateLimiter  (interface của lõi, không biết gì về Spring)
   |
   +--> cho phép : đặt header X-RateLimit-*, chạy tiếp controller
   +--> bị chặn  : trả 429 + Retry-After, controller không được chạy
   +--> Redis lỗi + fail-closed : trả 503
```

```
src/main/java/com/hung/ratelimiter
├── core/        RateLimiter, RateLimitResult, TimeProvider, Factory, LimiterCreator (không phụ thuộc Spring)
├── algorithm/   FixedWindow, SlidingWindowCounter, TokenBucket (trong bộ nhớ)
├── redis/       bản cài đặt trên Redis (Lua), chính sách khi Redis lỗi
├── web/         @RateLimit, interceptor, registry, cấu hình (phần tích hợp Spring)
└── demo/        controller minh họa
src/main/resources/redis/    các Lua script
docker-compose.yml           Redis để phát triển và chạy test
```

Các quyết định đáng chú ý:

- **Lõi tách khỏi Spring.** Toàn bộ thuật toán là Java thuần nên dễ kiểm thử, dễ benchmark. Nhờ interface `RateLimiter`, bản Redis được thêm vào mà không sửa phần web.
- **`TimeProvider` thay cho gọi thời gian hệ thống trực tiếp.** Test điều khiển được thời gian bằng đồng hồ giả, không cần `Thread.sleep`, kết quả tất định. Ở chế độ Redis, thời gian vẫn do `TimeProvider` cấp rồi truyền vào script.
- **Khóa theo từng key** (`synchronized` trên trạng thái của key) trên `ConcurrentHashMap` ở chế độ bộ nhớ. `ConcurrentHashMap` chỉ bảo vệ thao tác trên map; chuỗi "đọc, so sánh, ghi" vẫn cần khóa để không vượt giới hạn khi nhiều luồng cùng gọi.
- **Lua script cho chế độ phân tán,** vì cần tính nguyên tử giữa nhiều máy mà vẫn chỉ tốn một lần gọi mạng.
- **Dùng `HandlerInterceptor`** thay vì Servlet Filter hay AOP: nó đọc được annotation trên method và kiểm soát được response (429, 503, header).
- **Mỗi endpoint một `RateLimiter`**, để quota của endpoint này không bị endpoint khác chiếm. Ở Redis, tên key có cả thuật toán và endpoint.
- **Dọn key không hoạt động:** theo chu kỳ ở chế độ bộ nhớ (ngưỡng dọn phải lớn hơn cửa sổ dài nhất và thời gian nạp đầy xô), bằng TTL ở chế độ Redis.
- **Chính sách khi Redis lỗi là cấu hình rõ ràng,** không để ngầm: hệ thống luôn có quyết định cho trường hợp phụ thuộc ngoài bị hỏng.

## Kiểm thử

Bộ test chạy bằng `mvn test` (JUnit 5, Spring MockMvc), gồm:

- **Test hành vi từng thuật toán:** giới hạn, reset hoặc nạp lại theo thời gian, tái hiện lỗi burst ở ranh giới của Fixed Window và chứng minh Sliding Window Counter chặn được.
- **Test hợp đồng chung** chạy lặp cho cả ba thuật toán: `retryAfter` đủ để request kế tiếp được chấp nhận, `remaining` giảm dần, các key độc lập.
- **Test trường hợp biên:** tham số không hợp lệ, `limit = 1`, cửa sổ rất nhỏ, key `null`, nhảy thời gian rất lớn, đồng hồ nhảy lùi.
- **Test theo quy luật với dữ liệu ngẫu nhiên** (seed cố định, kết quả tái hiện được): Fixed Window khớp mô hình tham chiếu; Sliding Window Counter không bao giờ nhận quá `limit` trong một cửa sổ; Token Bucket không bao giờ tiêu quá `capacity` cộng số token đã nạp.
- **Test đa luồng:** 32 luồng cùng gọi một key, chạy lặp nhiều lần, số request được nhận không vượt giới hạn.
- **Test tích hợp web bằng MockMvc:** mã 429, header, `Retry-After`, tách bộ đếm theo IP và theo endpoint, API key, chế độ tắt.
- **Test tích hợp Redis** (cần Redis chạy, tự bỏ qua nếu không có): hợp đồng chung cho cả ba script Lua, hai "instance" dùng chung một bộ đếm, nhiều luồng trên hai instance không vượt giới hạn, key có TTL.
- **Test chính sách khi Redis lỗi:** fail-open cho request đi qua, fail-closed ném ngoại lệ (không cần Redis chạy).

Mỗi lần đẩy code lên GitHub, toàn bộ test được chạy tự động bằng GitHub Actions, kèm một container Redis nên các test Redis chạy thật (xem huy hiệu CI ở đầu trang).

## Hạn chế đã biết

- **Chế độ `memory` chỉ đúng với một instance.** Chạy nhiều instance cần chế độ `redis`.
- **Chế độ `redis` phụ thuộc Redis.** Mỗi request tốn thêm một lần gọi mạng, và khi Redis lỗi hệ thống áp dụng chính sách fail-open hoặc fail-closed ở mức toàn cục (chưa chọn riêng cho từng endpoint). Chưa kiểm thử với Redis Cluster hay Sentinel.
- **Đồng hồ giữa các instance có thể lệch.** Thời gian do từng instance cấp và truyền vào script, nên lệch đồng hồ giữa các máy làm cách tính cửa sổ lệch theo. Có thể khắc phục bằng cách lấy giờ từ lệnh `TIME` của Redis.
- **API key chưa được xác thực.** Trong bản demo, bất kỳ giá trị nào ở header cũng được coi là một client. Kẻ tấn công có thể bịa nhiều key để mỗi key có bộ đếm riêng. Trong hệ thống thật, rate limiter nên chạy sau bước xác thực và chỉ dùng key hợp lệ.
- **`X-Forwarded-For` chỉ nên bật khi có proxy tin cậy**, vì client có thể tự gửi giá trị giả.
- **Giới hạn theo IP có thể chặn oan** những người dùng chung một địa chỉ IP công cộng (sau NAT hoặc mạng công ty).
- **Đồng hồ nhảy lùi:** Token Bucket không nạp thêm token khi thời gian lùi; Fixed Window và Sliding Window Counter khi lùi sang một cửa sổ cũ sẽ tính lại bộ đếm, tức tạm thời nới lỏng giới hạn.
- **Sliding Window Counter là ước lượng**, dựa trên giả định request ở cửa sổ trước phân bố đều. Giá trị `Retry-After` của thuật toán này là cận trên an toàn, không phải thời điểm sớm nhất có thể thử lại.

## Kế hoạch tiếp theo

- [x] Ba thuật toán với bộ test đầy đủ
- [x] Tích hợp Spring Boot với `@RateLimit`
- [x] Chạy test tự động bằng GitHub Actions
- [x] Chế độ phân tán: lưu trạng thái bằng Redis, dùng Lua script để thao tác nguyên tử
- [ ] Benchmark (k6, JMH): thông lượng, độ trễ p50/p95/p99, bộ nhớ theo số lượng key, so sánh bộ nhớ với Redis
- [ ] Số liệu giám sát (Micrometer, Prometheus)
- [ ] Chọn chính sách fail-open hoặc fail-closed riêng cho từng endpoint
- [ ] Tùy chọn lấy giờ từ Redis (`TIME`) để loại bỏ ảnh hưởng của lệch đồng hồ

## Tác giả

Hung
