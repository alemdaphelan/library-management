# HUIT Library Management System

Hệ thống quản lý thư viện dành cho sinh viên, thủ thư và quản trị viên tại Trường Đại học Công Thương Thành phố Hồ Chí Minh. Sản phẩm gồm Web Angular, Mobile Flutter và Backend Spring Boot. Phạm vi chính tập trung vào tài khoản, danh mục sách, kho, mượn trả, tài liệu số, gợi ý sách và kiểm thử triển khai.

## Phạm vi chính thức

### Đã có nền tảng hoạt động

- Xác thực JWT, refresh token, đăng xuất, OTP và khôi phục mật khẩu.
- Phân quyền Admin, Librarian và Student.
- Quản lý sinh viên, sách, thể loại, bản sao và barcode.
- Quản lý nhà cung cấp, phiếu nhập và vị trí tầng, dãy kệ, ngăn.
- Dashboard dữ liệu thật, đề xuất sách và kiểm kê barcode.
- Đặt giữ, xem phiếu, hủy và gia hạn ở mức cơ bản.
- Nền tảng state machine tuần 9A: trạng thái chuẩn, duyệt/từ chối, lịch sử chuyển trạng thái và optimistic locking.
- Lõi tuần 9B: checkout/return transaction, giới hạn ba cuốn, hạn và gia hạn mười ngày, chặn quá hạn/nợ, idempotency và khóa chống double-loan.
- Lõi tuần 9C: phí trễ 1.000 đồng/ngày, scheduler quá hạn/giữ chỗ, giao dịch nợ và thông báo nhắc hạn idempotent.
- Chính sách mất/hỏng HUIT, đền bù, phí kỹ thuật, thu tiền từng phần, điều chỉnh giao dịch và miễn/giảm.
- Mobile dùng API thật cho phiếu mượn, thông báo, vị trí, đề xuất, gợi ý, QR, cập nhật hồ sơ và tìm sách theo chuyên ngành.
- Firebase Cloud Messaging có đăng ký token thiết bị, gửi push sau transaction, vô hiệu token lỗi và hiển thị foreground notification; cấu hình thật được cấp bằng secret/`--dart-define` khi nghiệm thu thiết bị.
- Thủ thư quét QR thẻ hoặc phiếu để checkout/return có chữ ký và thời hạn.
- Upload, đọc, phân quyền và ghi log sử dụng tài liệu số.
- Gợi ý Popularity, TF-IDF/Cosine và Hybrid có cold-start, cache và bộ metric đánh giá.
- Chatbot RAG qua Backend, PostgreSQL/pgvector, nguồn trích dẫn, Gemini gateway và fallback cục bộ có căn cứ.
- Docker Compose, Liquibase, Testcontainers và GitHub Actions.
- HTTP E2E, kiểm thử tải/bảo mật, health/smoke và backup/restore cục bộ của tuần 11.

### Việc còn lại của nhóm

- Cấp cấu hình Firebase của dự án test và chạy nghiệm thu push trên thiết bị thật ở ba trạng thái foreground/background/terminated.
- Tạo commit/push bản bàn giao, kiểm thử độc lập, sửa lỗi phản hồi, sau đó mới triển khai Internet.
- Hoàn thiện Word/PowerPoint từ gói tài liệu đã bàn giao.

### Không thuộc phạm vi

- Đặt phòng học, nhận trả phòng và xử lý vi phạm phòng.
- Kiosk tự phục vụ được tạm hoãn.
- Đồng bộ trường thời gian thực, giao ca và tài chính nâng cao chỉ xem xét sau khi các luồng bắt buộc hoàn tất.

## Công nghệ

- Web: Angular 21, TypeScript 5.9 và RxJS.
- Mobile: Flutter, Dart SDK 3.10.
- Backend: Java 17, Spring Boot 3.2, Spring Security, JPA và Liquibase.
- Dữ liệu chính: PostgreSQL + pgvector; Redis dùng cho OTP, token bị thu hồi và cache.
- Kiểm thử: JUnit 5, Mockito, Testcontainers, Angular tests và Flutter widget tests.
- Hạ tầng phát triển: Docker Compose, Nginx và Mailpit.

PostgreSQL JPA là cơ chế tìm kiếm sách chính. Elasticsearch, Kafka và MongoDB có cấu hình hoặc dependency phục vụ hướng mở rộng nhưng chưa được tính là luồng nghiệp vụ hoàn tất. Chiến lược tìm kiếm được ghi trong `Docs/ADR-001-CHIEN-LUOC-TIM-KIEM-SACH.md`.

## Cấu trúc repository

```text
KhoaLuan/
├── backend/       Spring Boot API, migration và test
├── web-app/       Angular Portal và giao diện quản trị
├── mobile-app/    Flutter Mobile App
├── Docs/          Đề cương, Use Case, sơ đồ và bảng tiến độ
├── TestChucNang/  Hướng dẫn, checklist và biểu mẫu nghiệm thu độc lập
├── Word_PPTX/     Gói tài liệu, sơ đồ và số liệu cho báo cáo/slide
└── docker-compose.yml
```

## Chạy hệ thống

### Toàn bộ hạ tầng và Backend

Tạo file `.env` phù hợp với `docker-compose.yml`, sau đó chạy tại thư mục gốc:

```bash
docker compose up -d --build
```

Có thể sao chép `.env.example` thành `.env` và thay toàn bộ mật khẩu/secret minh họa trước khi chạy.

Chatbot vẫn chạy ở chế độ retrieval/fallback cục bộ khi để trống `GEMINI_API_KEY`. Để bật Gemini generation và embedding, đặt key ở `.env`; không thêm key vào Angular hoặc commit vào repository. Xem [`Docs/TUAN_12A_CHATBOT_RAG_GEMINI.md`](Docs/TUAN_12A_CHATBOT_RAG_GEMINI.md) và [`Docs/TUAN_12B_DANH_GIA_CHATBOT_RAG.md`](Docs/TUAN_12B_DANH_GIA_CHATBOT_RAG.md).

- Backend trực tiếp: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Mailpit: `http://localhost:8025`

### Web

```bash
cd web-app
npm install
npm start
```

### Mobile

```bash
cd mobile-app
flutter pub get
flutter run
```

Thiết bị thật hoặc bản triển khai dùng `--dart-define=API_BASE_URL=...` và các biến `FIREBASE_*`; xem [`Docs/TUAN_12E_HOAN_THIEN_MOBILE_VA_FIREBASE.md`](Docs/TUAN_12E_HOAN_THIEN_MOBILE_VA_FIREBASE.md). Không đưa service-account JSON vào Mobile hoặc Git.

### Quality gate

```bash
cd backend
./mvnw test

cd ../web-app
npm test -- --watch=false
npm run build

cd ../mobile-app
flutter analyze
flutter test
```

### Kiểm tra vận hành Tuần 11

```powershell
.\scripts\week11-smoke.ps1
node .\scripts\week11-load-test.mjs --requests 200 --concurrency 20
.\scripts\backup-postgres.ps1 -OutputPath .\backups\huit_library.dump
.\scripts\verify-backup-restore.ps1 -BackupPath .\backups\huit_library.dump
```

## Tài liệu tiến độ

- `Docs/CHOT_PHAM_VI_VA_TRUY_VET_TUAN_1_DEN_5.md`: phạm vi và ma trận truy vết tuần 1 đến tuần 5.
- `Docs/BANG_DANH_GIA_TIEN_DO_CHI_TIET_TUAN_6_DEN_10.md`: đánh giá và kế hoạch tuần 1 đến tuần 12; tên file cũ được giữ để không làm hỏng liên kết.
- `Docs/CHINH_SACH_MAT_HONG_VA_THU_PHAT_9C.md`: quy tắc HUIT đã xác nhận và backlog triển khai phần cuối 9C.
- `Docs/TUAN_11_KIEM_THU_TICH_HOP_VA_VAN_HANH_CUC_BO.md`: kết quả E2E, tải, bảo mật, smoke và backup/restore tuần 11.
- `Docs/TUAN_12A_CHATBOT_RAG_GEMINI.md`: kiến trúc, nguồn tri thức, cấu hình, API, an toàn và kết quả kiểm chứng RAG.
- `Docs/TUAN_12B_DANH_GIA_CHATBOT_RAG.md`: bộ đánh giá 14 ca, metric retrieval/grounding/refusal/latency, kết quả và giới hạn.
- `Docs/TUAN_12C_GOI_BAN_GIAO_MINH_CHUNG_KY_THUAT.md`: mục lục bàn giao, role/API, truy vết, sơ đồ và kịch bản ảnh cho người làm Word/PPT.
- `Docs/TUAN_12C_KHUNG_WORD_PPT_THEO_MAU_2025.md`: ánh xạ minh chứng vào Mở đầu, 5 chương, Kết luận và khung 16 slide theo mẫu chính thức của khoa.
- `Docs/TUAN_12C_NGUON_SO_LIEU_VA_CACH_TAI_TAO.md`: phân loại bằng chứng, checksum, lệnh tái tạo và quy tắc diễn giải metric.
- `Docs/TUAN_12D_CHOT_REPOSITORY_VA_BAN_GIAO.md`: phiên bản công cụ, tài khoản demo, gate cuối, cách dựng và giới hạn của bản bàn giao.
- `Docs/TUAN_12E_HOAN_THIEN_MOBILE_VA_FIREBASE.md`: cập nhật hồ sơ Mobile, lọc chuyên ngành, loại dữ liệu mẫu và kiến trúc/configuration FCM.
- `Docs/analysis_class_diagram.mmd`, `Docs/design_class_diagram.mmd`, `Docs/huit_library_erd.mmd`: sơ đồ đã đồng bộ theo phạm vi mới.
- `Docs/system_architecture.mmd`, `Docs/local_deployment.mmd`, `Docs/circulation_state_machine.mmd`, `Docs/recommendation_pipeline.mmd`, `Docs/rag_pipeline.mmd`, `Docs/rag_query_sequence.mmd`: nguồn Mermaid cho kiến trúc, triển khai và các pipeline chính.

Trước khi tạo commit bàn giao, chạy `scripts/week12d-preflight.ps1` để kiểm tra secret, file build/tạm và cấu hình Compose.
