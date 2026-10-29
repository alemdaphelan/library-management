# HỢP ĐỒNG API HỆ THỐNG THƯ VIỆN HUIT

Tài liệu mô tả API đang tồn tại trong mã nguồn tại ngày 19/10/2026 và phân biệt rõ endpoint hoạt động, endpoint một phần và endpoint stub. Đường dẫn hiện chưa dùng chung một prefix: Auth nằm dưới `/api/v1/auth`, phần lớn module dùng đường dẫn gốc, còn Feedback tạm giữ hai alias.

## 1 Quy ước

- Payload ghi dữ liệu dùng JSON DTO và Jakarta Validation.
- Endpoint bảo vệ nhận JWT trong `Authorization: Bearer <token>`.
- Role chính là `ADMIN`, `LIBRARIAN` và `STUDENT`.
- Không tính endpoint trả rỗng hoặc trả thành công cố định là chức năng hoàn tất.
- Khi chuẩn hóa `/api/v1`, phải cập nhật đồng thời SecurityConfig, Nginx, Web, Mobile và test.

## 2 Auth và người dùng

Base path: `/api/v1/auth`

| Method | Path | Trạng thái | Ghi chú |
|---|---|---|---|
| POST | `/login` | Hoạt động | Trả access token, refresh token và role |
| POST | `/logout` | Hoạt động | Thu hồi access và refresh token bằng Redis |
| POST | `/refresh-token` | Hoạt động | Nhận JSON DTO |
| POST | `/change-password` | Hoạt động | Lấy người dùng từ JWT |
| POST | `/forgot-password` | Hoạt động | Gửi OTP qua SMTP |
| POST | `/verify-otp` | Hoạt động | Có giới hạn số lần sai và thời gian khóa |
| POST | `/reset-password` | Hoạt động | Xóa trạng thái OTP sau khi thành công |
| GET PUT | `/me` | Hoạt động | Đọc và cập nhật hồ sơ |
| POST | `/avatar` | Hoạt động một phần | Cần kiểm tra lại lưu trữ file khi triển khai |

Base path quản lý người dùng: `/users`

- `GET /students`, `GET /students/{mssv}`: Admin và Librarian được đọc.
- `POST /students`, `PUT /students/{mssv}`, `DELETE /students/{mssv}`: Admin được ghi.
- `POST /sync`: có API nhận dữ liệu đồng bộ; đồng bộ realtime với hệ thống trường được tạm hoãn.
- `POST /wishlist`: chưa phải luồng hoàn chỉnh.

## 3 Catalog và vị trí

### Sách và thể loại

- `GET /books?q=&categoryId=&page=&size=`: tìm kiếm PostgreSQL JPA, có phân trang.
- `GET /books/{id}`: lấy chi tiết sách.
- `POST /books`, `PUT /books/{id}`, `DELETE /books/{id}`: CRUD sách theo quyền.
- `GET /categories`: danh sách thể loại.
- `GET /books/recommendations` hoặc `/books/recommendations/popular`: baseline độ phổ biến có time-decay và fallback danh mục, không yêu cầu đăng nhập.
- `GET /books/{id}/similar`: xếp hạng sách tương tự bằng TF-IDF và cosine similarity.
- `GET /books/recommendations/personalized?limit=10`: bắt buộc JWT; trả Hybrid cá nhân hóa hoặc cold-start theo chuyên ngành, kèm điểm Content/Department/Popularity và lý do gợi ý. Kết quả được cache Redis 30 phút theo fingerprint lịch sử người dùng.
- `GET /admin/recommendations/evaluation?k=10&scenarios=40`: chỉ `ADMIN`; audit khả năng chia lịch sử thật theo thời gian và so sánh Popularity/Content/Hybrid trên bộ proxy được ghi nhãn bằng Precision/Recall/HitRate@K, Coverage và p50/p95 latency.
- `POST /books/{id}/reviews`: chưa phải luồng đánh giá hoàn chỉnh.

### Bản sao và vị trí

- `GET /book-copies/book/{bookId}`
- `GET /book-copies/{barcode}`
- `PUT /book-copies/{barcode}/location`
- `PUT /book-copies/{barcode}/status`
- `/locations/floors`, `/locations/bookshelves`, `/locations/shelves`: GET, POST, PUT và DELETE cho cây vị trí.

## 4 Inventory và dashboard

Base path Inventory: `/inventory`

- `GET POST /suppliers`
- `POST /receipts`, `GET /receipts`
- `GET /stats`
- `POST /audits`, `GET /audits`, `GET /audits/{auditId}`
- `POST /audits/{auditId}/scans`
- `POST /audits/{auditId}/complete`
- `POST /shift-handover`: tạm hoãn và chưa được tính hoàn thành.

Base path Dashboard: `/dashboard`

- `GET /stats`
- `GET /weekly-stats`
- `GET /recent-activities`
- `GET /trending-books`

Các API Dashboard và kiểm kê cơ bản đã dùng dữ liệu PostgreSQL thật và có test tự động.

## 5 Circulation

Base path: `/loans`

| Method | Path | Trạng thái hiện tại |
|---|---|---|
| POST | `/reserve` | Có tạo phiếu và khóa bản sao cơ bản |
| GET | `/my-loans` | Có dữ liệu người dùng hiện tại |
| GET | `/staff` | Danh sách tóm tắt phiếu thật cho Admin/Librarian, gồm bạn đọc, số tài liệu, quá hạn và tổng phí trễ |
| GET | `/{ticketId}` | Có chi tiết phiếu cho Admin/Librarian |
| POST | `/{ticketId}/approve` | Có duyệt phiếu `PENDING → RESERVED`, lưu người xử lý và lịch sử |
| POST | `/{ticketId}/reject` | Có từ chối phiếu, ghi lý do, nhả bản sao và lưu lịch sử |
| POST | `/cancel/{ticketId}` | Có hủy đặt giữ và nhả bản sao |
| POST | `/renew/{ticketId}/items/{detailId}` | Gia hạn đúng một tài liệu thêm 10 ngày, tối đa một lần; chặn quá hạn, nợ, khóa thẻ và waitlist |
| POST | `/checkout` | Transaction thật với `idempotencyKey`, `ticketId` và tối đa 3 `barcodes`; chỉ Admin/Librarian |
| POST | `/return` | Transaction thật với `idempotencyKey`, tối đa 3 `barcodes` và ghi chú tình trạng; chỉ Admin/Librarian |

`POST /waitlist/join` và `GET /waitlist/my` dùng hàng đợi FIFO thật; một bản trả về được giữ 48 giờ cho người đầu hàng, có xử lý ngày nghỉ/giờ đóng cửa.

State machine chuẩn từ gói 9A dùng các trạng thái phiếu `PENDING`, `RESERVED`, `BORROWING`, `OVERDUE`, `RETURNED`, `CANCELLED`, `REJECTED`. Các chuyển trạng thái hợp lệ hiện được kiểm tra tập trung; trạng thái cũ `APPROVED`, `CHECKED_OUT`, `ACTIVE`, `COMPLETED` được migration lần lượt về `RESERVED`, `BORROWING`, `BORROWING`, `RETURNED`. Mọi lần duyệt, từ chối hoặc hủy đều có bản ghi `loan_status_history`; chuyển trạng thái sai trả HTTP `409`.

Gói 9B cập nhật phiếu, chi tiết và bản sao trong cùng transaction. Checkout yêu cầu phiếu `RESERVED`, barcode đúng với chi tiết đang chờ giao, không vượt ba tài liệu và người đọc không có sách quá hạn/nợ. Pessimistic lock ngăn hai quầy giao cùng một barcode. Kết quả thao tác được lưu trong `circulation_operations`; gửi lại cùng `idempotencyKey` và payload sẽ nhận kết quả cũ, còn tái sử dụng key cho payload khác trả `409`.

9C chạy scheduler mỗi 5 phút để hủy giữ chỗ/hàng đợi hết hạn, chuyển trạng thái quá hạn, tính phí theo ngày dương lịch và phát thông báo theo lịch. Web Portal/Admin dùng thông báo PostgreSQL thật.

### Mất/hỏng và tài chính tuần 9

- `/api/v1/penalty-cases`: lập, liệt kê hồ sơ mất/hỏng và xem hồ sơ cá nhân.
- `PATCH /api/v1/penalty-cases/{id}/replacement-acceptance`: thủ thư xác minh sách thay thế và tạo barcode mới.
- `POST /api/v1/penalty-cases/{id}/review-request`, `PATCH /{id}/review`: luồng miễn giảm; chỉ Admin ghi nhận quyết định bên ngoài.
- `POST /api/v1/penalty-cases/rates`: Admin cấu hình biểu phí tài liệu không có giá bìa.
- `POST /api/v1/transactions/payments`: thu tiền mặt/chuyển khoản, hỗ trợ nhiều đợt và trả biên nhận phân bổ từng khoản.
- `GET /api/v1/transactions/my-ledger`: người dùng xem công nợ khi bị khóa.
- `/api/v1/transactions/adjustments`: Librarian yêu cầu, Admin duyệt/từ chối; không hard delete giao dịch.
- `/api/v1/library-violations`: lưu vi phạm mang tài liệu chưa checkout riêng khỏi bộ đếm quá hạn.

## 6 Hợp đồng QR bắt buộc

QR chỉ dùng để nhận diện thẻ sinh viên hoặc phiếu mượn. Backend vẫn là nơi duy nhất được thay đổi trạng thái mượn trả.

Hợp đồng QR mục tiêu trên nền checkout/return 9B đã ổn định:

- `POST /qr/resolve`: nhận payload đã ký và trả loại QR, mã người dùng hoặc mã phiếu cùng trạng thái hợp lệ.
- Chỉ role `LIBRARIAN` được dùng kết quả resolve để mở luồng tại quầy.
- Payload phải có loại đối tượng, định danh, thời điểm hết hạn và chữ ký; không chứa thông tin nhạy cảm dư thừa.
- QR sai chữ ký, hết hạn hoặc bị thu hồi trả lỗi rõ ràng.
- Checkout và return nhận `idempotencyKey` để chống quét trùng.
- Mobile không tự cập nhật LoanTicket, LoanDetail, BookCopy hoặc Transaction.

Các path circulation, trạng thái và idempotency đã được chốt ở 9A–9B.

## 7 Librarian QR

- `GET /api/v1/qr/tickets/{ticketId}`: chủ phiếu hoặc Admin/Librarian tạo payload QR có chữ ký, hết hạn sau 10 phút.
- `GET /api/v1/qr/cards/me`: tạo QR thẻ thư viện cho tài khoản hiện tại.
- `POST /api/v1/qr/resolve`: chỉ `ADMIN`/`LIBRARIAN`; xác thực chữ ký, thời hạn và trả hồ sơ/phiếu cùng danh sách thao tác Backend cho phép.
- Payload có dạng `HUITQR1.<claims-base64url>.<hmac-base64url>`; client không được tự giải mã để quyết định trạng thái nghiệp vụ.
- Sau resolve, client dùng `/loans/checkout` hoặc `/loans/return` với `idempotencyKey`; không có API QR nào tự sửa trạng thái dữ liệu.

## 8 Interactive và tài liệu số

- `/proposals`: tạo đề xuất, xem lịch sử cá nhân, xem danh sách quản trị và cập nhật trạng thái.
- `/feedback` và `/api/v1/feedback`: alias tạm thời cho complaint và proposal.
- `GET /digital-assets`: danh sách tài liệu theo quyền; sinh viên không nhìn thấy tài liệu `STAFF_ONLY`.
- `POST /digital-assets/upload`: Admin/Librarian upload PDF, EPUB, MP3 hoặc PPTX tối đa 50 MB, có thể liên kết `bookId` và chọn mức truy cập.
- `PUT /digital-assets/{id}` và `DELETE /digital-assets/{id}`: nhân viên cập nhật metadata hoặc xóa cả metadata/tệp.
- `GET /digital-assets/{id}/download`: tải tệp có JWT và ghi `DigitalUsageLog`; dữ liệu file nằm trong volume `digital_assets_data` khi chạy Docker.
- `/users/students`: Admin CRUD hồ sơ sinh viên; Librarian chỉ được đọc. Tạo mới dùng MSSV làm mật khẩu ban đầu và buộc đổi ở lần đăng nhập đầu.
- Notification đã có API đọc/đánh dấu đã đọc và scheduler nhắc hạn thật; Web và Mobile đều đã nối vào API này.

## 9 Phần loại hoặc tạm hoãn

- `/rooms/book` và toàn bộ đặt phòng đã loại khỏi phạm vi. Endpoint hoặc entity còn lại là legacy.
- Kiosk tự phục vụ, giao ca, realtime university sync và tài chính nâng cao được tạm hoãn.
- Elasticsearch, Kafka, MongoDB và AI service không được coi là luồng hoạt động nếu chưa có code tích hợp và test tương ứng.
