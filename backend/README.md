# HUIT Library Backend

Backend là ứng dụng Spring Boot 3.2 chạy trên Java 17. Hệ thống dùng PostgreSQL cho dữ liệu nghiệp vụ, Redis cho OTP và token bị thu hồi, Liquibase cho migration và Mailpit để kiểm thử email cục bộ.

## Chạy bằng Docker Compose

Từ thư mục gốc của repository:

```bash
docker compose up -d --build
```

Các địa chỉ phát triển:

- Backend trực tiếp: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Mailpit: `http://localhost:8025`

Docker Compose cũng khởi động MongoDB, Elasticsearch và Kafka để phục vụ hướng mở rộng. Các dịch vụ này chưa phải đường dữ liệu chính của catalog, circulation hoặc recommender và không được tính là chức năng hoàn tất chỉ vì container đang chạy.

## Chạy Backend cục bộ

Khởi động các dịch vụ phụ thuộc cần thiết:

```bash
docker compose up -d postgres redis mailpit
```

Sau đó chạy tại thư mục `backend`:

```bash
./mvnw spring-boot:run
```

Biến môi trường quan trọng được khai báo trong `application.yml` và `docker-compose.yml`, gồm kết nối PostgreSQL, Redis, SMTP, `JWT_SECRET`, `QR_SECRET`, thời hạn access/refresh token, `QR_TTL_MINUTES` và cấu hình `GEMINI_*`. Production phải đặt các secret riêng, không dùng secret phát triển mặc định và không lưu credential production vào repository.

`DATABASE_SEEDING_ENABLED=true` bật bước chuẩn hóa catalog/nhà cung cấp phục vụ local demo. Bản migration demo có ba tài khoản chính `admin@huit.edu.vn`, `librarian@huit.edu.vn`, `student@student.huit.edu.vn` (Student cũng đăng nhập bằng MSSV `20012001`), cùng mật khẩu local `admin123`. Production phải đặt `DATABASE_SEEDING_ENABLED=false` và đổi/vô hiệu hóa các tài khoản demo trước khi mở Internet.

## Module

- `auth`: đăng nhập, JWT, refresh token, OTP, hồ sơ và quản lý sinh viên.
- `catalog`: sách, thể loại, bản sao, barcode và tầng kệ ngăn. Search hiện dùng PostgreSQL JPA.
- `inventory`: nhà cung cấp, phiếu nhập và kiểm kê barcode.
- `dashboard`: số liệu tổng quan, hoạt động gần đây và sách phổ biến.
- `interactive`: đề xuất sách; review, wishlist và complaint mới ở mức một phần.
- `circulation`: hoàn tất tuần 9 với state machine, checkout/return theo barcode, gia hạn từng tài liệu một lần/+10 ngày, hàng đợi FIFO 48 giờ, scheduler, khóa ba tháng và kiểm soát nợ/quá hạn.
- `penalty` và `finance`: hồ sơ mất/hỏng, hệ số 5/3, phí kỹ thuật, biểu phí cấu hình, sách thay thế, miễn giảm, thanh toán nhiều đợt, biên nhận phân bổ và giao dịch điều chỉnh ngược.
- `digital`: upload/download file thật, metadata PostgreSQL, storage volume, liên kết sách, quyền bạn đọc/nhân viên và log truy cập.
- `notification`: có lưu thông báo idempotent, API danh sách cá nhân/đánh dấu đã đọc và nhắc sắp hạn, quá hạn, giữ chỗ hết hạn từ scheduler.
- `rag`: nạp nguồn Markdown có kiểm soát, chunking/embedding, pgvector retrieval, tìm catalog, Gemini generation, nguồn trích dẫn, fallback offline và audit dạng hash.
- `facilities`: đặt phòng đã bị loại khỏi phạm vi; code còn lại là legacy. Kiosk tự phục vụ được tạm hoãn.

Quét QR cho thủ thư đã được triển khai trên nền state machine circulation. Backend phát hành payload `HUITQR1` có chữ ký HMAC và hạn dùng cho thẻ bạn đọc/phiếu mượn; chỉ `ADMIN`/`LIBRARIAN` được resolve. Mobile chỉ nhận diện QR và gửi checkout/return có idempotency key; mọi thay đổi trạng thái vẫn do Backend xử lý trong transaction.

## Hợp đồng API

Auth hiện dùng prefix `/api/v1/auth`. Phần lớn module còn lại dùng đường dẫn gốc như `/books`, `/loans`, `/inventory`, `/locations` và `/dashboard`. `GET /loans/staff` trả danh sách phiếu thật cho màn hình quản lý của Admin/Librarian; `GET /loans/{ticketId}` trả chi tiết theo đúng định danh phiếu. Feedback tạm giữ hai alias `/feedback` và `/api/v1/feedback`.

Prefix API chưa hoàn toàn thống nhất. Khi chuẩn hóa, cần giữ alias chuyển tiếp có thời hạn hoặc cập nhật đồng thời Web, Mobile, SecurityConfig và Nginx. Không giả định mọi endpoint đều nằm dưới `/api/v1`.

API RAG dùng prefix thống nhất:

- `POST /api/v1/chatbot/query`: hỏi Chatbot, trả câu trả lời và nguồn.
- `GET /api/v1/admin/rag/status`: trạng thái chỉ mục, chỉ Admin.
- `POST /api/v1/admin/rag/reindex`: dựng lại chỉ mục, chỉ Admin.
- `POST /api/v1/admin/rag/evaluate?delayMillis=10000`: chạy bộ đánh giá RAG có kiểm soát và trả JSON metric, chỉ Admin.

Nếu `GEMINI_API_KEY` để trống, Backend dùng embedding/fallback cục bộ để demo không phụ thuộc Internet. Khi có key, provider `auto` dùng model trong `GEMINI_EMBEDDING_MODEL` và `GEMINI_GENERATION_MODEL`; key không bao giờ được gửi xuống Web.

## Migration

- Master changelog: `src/main/resources/db/changelog/db.changelog-master.yaml`
- Changeset: `src/main/resources/db/changelog/changesets/`
- Hibernate chạy ở chế độ validate; thay đổi schema phải được mô tả bằng Liquibase.

## Quality gate

Docker phải hoạt động để chạy integration test bằng Testcontainers:

```bash
./mvnw test
```

Bộ test hiện kiểm tra Auth, phân quyền, catalog, vị trí, nhập sách, dashboard, kiểm kê, state machine, checkout/return, QR, scheduler, mất/hỏng, đền bù, thanh toán/điều chỉnh, hàng đợi, khóa lưu thông và RAG. Migration RAG chạy trên image pgvector thật; PostgreSQL/Redis/Mailpit cũng được kiểm tra bằng Testcontainers.

Workflow tương ứng nằm tại `.github/workflows/week6-quality.yml`.

Bộ đánh giá tuần 12B có thể chạy bằng `scripts/week12b-rag-evaluation.ps1`; kết quả và cách đọc metric được mô tả tại `Docs/TUAN_12B_DANH_GIA_CHATBOT_RAG.md`.
