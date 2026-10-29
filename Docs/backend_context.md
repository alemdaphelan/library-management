# KIẾN TRÚC BACKEND HỆ THỐNG THƯ VIỆN HUIT

Backend hiện là modular monolith Spring Boot. Tài liệu này phản ánh mã nguồn đang chạy và kiến trúc mục tiêu đã chốt, không xem dependency hoặc container chưa được nối vào nghiệp vụ là chức năng hoàn thành.

## 1 Công nghệ đang hoạt động

- Java 17 và Spring Boot 3.2.5.
- Spring Web, Spring Security, JWT và Jakarta Validation.
- Spring Data JPA với PostgreSQL; Hibernate dùng chế độ validate.
- Liquibase quản lý schema và dữ liệu seed.
- Redis lưu OTP, trạng thái giới hạn xác thực và token bị thu hồi.
- Spring Mail với Mailpit trong môi trường phát triển và integration test.
- springdoc OpenAPI tạo Swagger UI.
- JUnit 5, Mockito, Spring Security Test và Testcontainers.

MongoDB, Elasticsearch, Kafka, WebSocket và OpenFeign có dependency hoặc hạ tầng phát triển nhưng chưa tạo thành đường nghiệp vụ chính. Search catalog đang dùng PostgreSQL JPA. Điều kiện kích hoạt Elasticsearch nằm trong ADR 001.

## 2 Cấu trúc module

```text
com.huit.library
├── config
├── core
│   ├── entity
│   ├── exception
│   ├── response
│   └── security
└── modules
    ├── auth
    ├── catalog
    ├── circulation
    ├── dashboard
    ├── digital
    ├── finance
    ├── interactive
    ├── inventory
    ├── notification
    ├── penalty
    ├── recommendation
    └── facilities
```

`facilities` là code legacy của đặt phòng đã bị loại khỏi phạm vi. Không phát triển thêm controller, service, UI hoặc test cho module này. Kiosk tự phục vụ được tạm hoãn.

## 3 Mô hình dữ liệu chính

### Người dùng

- `UserEntity`: khóa `UUID`, role, hồ sơ, MSSV, khoa, trạng thái và thời hạn thẻ.
- `StaffRoleEntity`: khóa chuỗi như ADMIN, LIBRARIAN và STUDENT.
- `WalletEntity`: khóa `Long`, số dư tiền thế chân và tổng nợ. Nghiệp vụ tài chính nâng cao được tạm hoãn; khoản phạt mượn trả vẫn thuộc lõi.

Trường `roomViolationCount` còn trong `UserEntity` là dữ liệu legacy và phải được loại bằng migration riêng sau khi kiểm tra tham chiếu.

### Catalog và vị trí

- `BookEntity`: khóa `Long`; author và publisher hiện được lưu dạng chuỗi; category là quan hệ JPA.
- `BookCopyEntity`: barcode là khóa nghiệp vụ chuỗi; có `bookId`, `shelf`, trạng thái, tình trạng và khóa tạm.
- `FloorEntity`, `BookshelfEntity`, `ShelfEntity`: khóa `Long` và tạo cây vị trí tầng, dãy kệ, ngăn.

Không dùng mô hình Author, Publisher và BookAuthor tách bảng trong ERD chính thức khi code và migration chưa triển khai mô hình đó.

### Circulation và phạt

- `LoanTicketEntity`: khóa `Long`, `userId` UUID, loại phiếu, trạng thái, người xử lý, ghi chú quyết định và trường `@Version` để chống ghi đè đồng thời.
- `LoanDetailEntity`: khóa `Long`, ticket, barcode, ngày hạn, ngày trả, số lần gia hạn và tiền phạt trễ.
- `LoanStatusHistoryEntity`: lưu trạng thái trước/sau, người thực hiện, lý do và thời điểm cho từng lần chuyển trạng thái.
- `CirculationOperationEntity`: lưu idempotency key, fingerprint yêu cầu và kết quả checkout/return để chống quét trùng.
- `WaitlistEntity`: khóa `Long`, book, user và trạng thái hàng đợi.
- `TransactionEntity`: khóa `Long`, giao dịch cha, số tiền, loại, người dùng/phiếu/chi tiết liên quan, lý do, trạng thái và dedup key.

Gói 9A đã chuẩn hóa state machine `PENDING → RESERVED → BORROWING → OVERDUE/RETURNED`, cùng các nhánh `CANCELLED` và `REJECTED`. Gói 9B đã triển khai checkout/return transactionally trên LoanTicket, LoanDetail và BookCopy; áp dụng tối đa ba tài liệu, hạn và gia hạn mười ngày, chặn quá hạn/nợ, pessimistic lock và idempotency. Lõi 9C đã bổ sung phí trễ 1.000 đồng/ngày, scheduler giữ chỗ/quá hạn, đồng bộ bản sao, ví nợ, giao dịch và thông báo idempotent. Test PostgreSQL thật đã xác nhận commit, rollback, hai checkout đồng thời, migration 14 và các nhánh scheduler.

### Inventory

- `SupplierEntity`, `GoodsReceiptEntity`, `ReceiptDetailEntity`: nhà cung cấp và phiếu nhập.
- `InventoryAuditEntity`: khóa `Long`, người thực hiện UUID, tổng hệ thống, tổng thực tế và trạng thái.
- `InventoryDetailEntity`: đang dùng khóa chuỗi. Trường `auditId` trong code cần được đổi về `Long` hoặc ánh xạ rõ ràng để khớp `InventoryAuditEntity` và ERD mục tiêu.

### Interactive và digital

- `BookProposalEntity` đã có luồng service và controller tương đối đầy đủ.
- Complaint, Review và Wishlist mới ở mức một phần.
- DigitalDocument và DigitalUsageLog mới có entity/repository; chưa có upload, download và authorization hoàn chỉnh.
- Notification đã có service, API danh sách cá nhân/đánh dấu đã đọc và scheduler tạo nhắc sắp hạn, quá hạn, giữ chỗ hết hạn với dedup key.

## 4 Dịch vụ đã triển khai

- `AuthService`, `UserService`
- `BookService`, `BookCopyService`, `LocationService`
- `InventoryService`, `InventoryAuditService`
- `DashboardService`
- `BookProposalService`
- `LoanService` cho reserve, my loans, danh sách/chi tiết phiếu dành cho staff, cancel và gia hạn 10 ngày
- `CirculationStateService` và `CirculationStateMachine` cho duyệt, từ chối, hủy, checkout, return, kiểm tra transition, quy tắc mượn và idempotency
- `CirculationMaintenanceService`, `CirculationScheduler`, `LateFineService` và `NotificationService` cho quá hạn, phí trễ, giải phóng giữ chỗ và nhắc hạn
- `LibrarianQrService` cho phát hành/xác minh QR HMAC có thời hạn và resolve thẻ bạn đọc/phiếu mượn

Sơ đồ thiết kế không dùng hậu tố `ServiceImpl` vì mã nguồn hiện sử dụng trực tiếp các lớp service trên. Nhập sách nằm trong `InventoryController` và `InventoryService`, không có ImportController riêng.

## 5 Thành phần đã chốt và phần phải bổ sung

### Tuần 9

- Phí trễ hạn, scheduler giữ chỗ/quá hạn, thông báo và integration test đã hoàn thành ở lõi 9C.
- Quy tắc mất/hỏng HUIT đã được ghi nhận trong `Docs/CHINH_SACH_MAT_HONG_VA_THU_PHAT_9C.md`; code vẫn chưa có hồ sơ mất/hỏng, tính đền bù, miễn giảm và API thu/đối soát giao dịch.
- Mức sửa chữa nhẹ, phí xử lý kỹ thuật, biểu phí tài liệu không có giá bìa và nguồn/quy tắc làm tròn tỷ giá phải được cấu hình sau khi có số liệu chính thức; không hard-code suy đoán.

### QR thủ thư

- `LibrarianQrService` đã phát hành và xác minh chữ ký HMAC, thời hạn, loại QR và quyền sở hữu.
- `POST /api/v1/qr/resolve` chỉ cho `ADMIN`/`LIBRARIAN`; Mobile kiểm tra role trước khi mở camera.
- QR chỉ resolve định danh; state change phải gọi circulation service.
- Checkout và return dùng idempotency key để chống quét trùng.
- Không lưu mật khẩu, token đăng nhập hoặc dữ liệu cá nhân dư thừa trong QR.

### Tuần 10

- Gói 10A đã tạo `recommendation_subjects`, `department_subject_affinities` và `book_subject_assignments` mà không ghi đè category catalog.
- Taxonomy gồm 13 nhóm, ánh xạ đủ 39 ngành HUIT năm 2026 cùng alias khoa đang có trong database.
- 4.387 sách đã được gắn ít nhất một chủ đề bằng từ khóa ranh giới từ có confidence; không còn dùng phân loại ngẫu nhiên cho tín hiệu chuyên ngành.
- Gói 10B đã có Popularity Baseline time-decay/log normalization tại `GET /books/recommendations` và `GET /books/recommendations/popular`.
- `GET /books/{id}/similar` dùng TF-IDF/Cosine trên title, author, publisher, category và taxonomy chủ đề, giới hạn candidate pool 1.500 sách.
- API 10B trả `method`, `dataSufficient`, `candidateCount`, điểm và lý do; khi lịch sử thưa sẽ trả fallback có nhãn rõ ràng, không giả vờ là kết quả đủ dữ liệu.
- Gói 10C đã thêm `GET /books/recommendations/personalized` bắt buộc JWT, kết hợp mặc định Content/Department/Popularity theo `0,50/0,35/0,15` và trả từng điểm thành phần.
- Hồ sơ sở thích tăng trọng số cho lượt mượn gần đây và sách được gia hạn; mọi sách đã mượn được loại khỏi candidate.
- Sinh viên chưa có lịch sử dùng nhánh `HYBRID_COLD_START` theo chuyên ngành rồi popularity/fallback; sinh viên có lịch sử dùng `HYBRID_PERSONALIZED`.
- Redis cache mặc định 30 phút và khóa có fingerprint ngành/lịch sử để tự thay đổi khi tương tác mượn trả thay đổi; lỗi cache chạy fail-open.
- Gói 10D đã tích hợp Web/Mobile. Gói 10E đã bổ sung evaluator Admin, audit split 80/20 theo thời gian và bộ proxy chủ đề có Precision/Recall/HitRate@10, Coverage cùng p50/p95 latency; metric proxy không được mô tả là hành vi mượn thật.

## 6 Quy ước API và bảo mật

Auth dùng `/api/v1/auth`; phần lớn module còn dùng root path. Đây là nợ kỹ thuật đã xác nhận. Không đổi prefix riêng lẻ mà không sửa SecurityConfig, Nginx, Web, Mobile và test.

Các endpoint ghi dữ liệu phải kiểm tra role ở cả SecurityConfig hoặc method security và service. Riêng QR, chỉ `ADMIN`/`LIBRARIAN` được resolve và chuyển tiếp sang checkout hoặc return. Mobile không được tự thay đổi trạng thái phiếu hoặc bản sao.

## 7 Quality gate

Workflow CI hiện chạy Backend test, Web build test và Mobile analyze test. Integration test dùng PostgreSQL, Redis và Mailpit thật trong Testcontainers. Trước khi đóng tuần 11 cần thêm E2E, concurrency, performance, security và smoke test sau triển khai.
