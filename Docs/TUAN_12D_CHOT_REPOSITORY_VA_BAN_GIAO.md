# TUẦN 12D — CHỐT REPOSITORY VÀ BÀN GIAO KIỂM THỬ

## 1. Thông tin phiên bản

- Ngày chốt kỹ thuật: **20/10/2026**.
- Nhánh làm việc: `feature/week-6-backend-apis`.
- Commit nền trước khi đóng gói: `ebe2c4c`.
- Trạng thái tại thời điểm chốt 12D: nội dung tuần 1–12 đã nằm trong working tree và chờ chủ repository xác nhận. Bản bàn giao tiếp theo được đẩy trên nhánh `feature/week-6-backend-apis`; người kiểm thử phải dùng commit HEAD được gửi kèm.
- Phạm vi: chuẩn bị bản để thành viên khác clone, kiểm thử nghiệm thu và phản hồi lỗi. Triển khai Internet, Word và PowerPoint không thuộc người phụ trách code.

## 2. Môi trường đã dùng để chạy gate

| Thành phần | Phiên bản |
|---|---|
| Hệ điều hành | Windows 11 |
| Java | 17.0.12 LTS |
| Spring Boot | 3.2.5 |
| Node.js | 24.20.0 |
| npm | 11.19.0 |
| Flutter | 3.38.5 stable |
| Dart | 3.10.4 |
| Docker | 29.7.2 |
| Docker Compose | 5.5.1 |

## 3. Kết quả quality gate cuối

| Gate | Lệnh | Kết quả ngày 20/10/2026 |
|---|---|---|
| Backend test | `backend\\mvnw.cmd --batch-mode test` | **PASS — 116 test, 0 failure, 0 error** |
| Backend package | `backend\\mvnw.cmd --batch-mode -DskipTests package` | **PASS — tạo JAR Spring Boot** |
| Web dependency/audit | `npm ci`; `npm audit --audit-level=high` | **PASS — 0 vulnerability** |
| Web test | `npm test -- --watch=false` | **PASS — 69 test** |
| Web production build | `npm run build` | **PASS** |
| Mobile analyze | `flutter analyze` | **PASS — No issues found** |
| Mobile test | `flutter test` | **PASS — 5 test** |
| Mobile APK | `flutter build apk --debug` | **PASS — `build/app/outputs/flutter-apk/app-debug.apk`** |
| Docker build/health | `docker compose up -d --build backend nginx` | **PASS — Backend healthy** |
| Smoke | `scripts/week11-smoke.ps1` | **PASS — health UP, catalog 269.922 sách** |
| Tải cục bộ | 200 request, concurrency 20 | **PASS — 200/200 HTTP 200, p95 681,44 ms, 33,84 req/s** |
| Backup/restore | `backup-postgres.ps1`; `verify-backup-restore.ps1` | **PASS — restore database tạm, 51 bảng public** |
| Preflight repository | `scripts/week12d-preflight.ps1` | **PASS — 0 lỗi, không phát hiện secret** |
| Auth lifecycle | `test_auth.ps1` | **PASS — login, refresh, logout; token đã thu hồi trả HTTP 401** |
| RAG status | `GET /api/v1/admin/rag/status` | **PASS — 4 tài liệu, 7 chunk, Gemini embedding đã cấu hình** |

Các stack trace “forced operation audit failure” và “Gemini timeout/citation” xuất hiện trong log Backend là tình huống lỗi được test chủ động để kiểm chứng rollback/fallback; suite kết thúc `BUILD SUCCESS`.

## 4. Tài khoản demo cục bộ

Các tài khoản dưới đây do migration tạo cho môi trường demo và đã đăng nhập thành công trên bản chốt:

| Role | Định danh đăng nhập | Mật khẩu demo |
|---|---|---|
| Admin | `admin@huit.edu.vn` | `admin123` |
| Librarian | `librarian@huit.edu.vn` | `admin123` |
| Student | `student@student.huit.edu.vn` hoặc MSSV `20012001` | `admin123` |

Đây không phải credential production. Khi triển khai Internet phải tắt seed không cần thiết, đổi hoặc vô hiệu hóa toàn bộ tài khoản demo, đặt secret riêng và không sao chép file `.env` cục bộ lên máy chủ.

## 5. Cấu hình và dữ liệu bàn giao

- Sao chép `.env.example` thành `.env`, sau đó thay toàn bộ giá trị có tiền tố `change_` hoặc `replace_`.
- `DATABASE_SEEDING_ENABLED=true` dùng cho dữ liệu catalog/nhà cung cấp ở môi trường demo; production phải đặt `false` sau khi có phương án dữ liệu thật.
- Liquibase là nguồn sự thật của schema; Hibernate dùng `ddl-auto: validate`.
- Catalog smoke hiện có 269.922 đầu sách trên volume cục bộ. Bản clone mới dựng dữ liệu theo migration và dataset đi kèm, vì vậy thời gian khởi động lần đầu có thể lâu hơn.
- Gemini key chỉ đặt trong `.env` hoặc secret manager. Nếu để trống, RAG vẫn dùng retrieval và fallback cục bộ.
- `.env`, `backups`, `target`, `build`, `node_modules` và `.codex-tmp` bị loại khỏi Git.

## 6. Trình tự dựng và nghiệm thu

```powershell
Copy-Item .env.example .env
# Chỉnh .env, không commit file này.
docker compose up -d --build
.\scripts\week11-smoke.ps1
.\scripts\week12d-preflight.ps1
```

Sau đó:

1. Khởi động Web bằng `npm ci` và `npm start` trong `web-app`.
2. Khởi động Mobile bằng `flutter pub get` và `flutter run` trong `mobile-app`.
3. Ghi commit đang test vào `TestChucNang/04_BIEN_BAN_NGHIEM_THU.md`.
4. Chạy đủ `TestChucNang/02_CHECKLIST_TEST_TOAN_BO_HE_THONG.md`.
5. Mỗi lỗi dùng `TestChucNang/03_MAU_BAO_LOI.md`, kèm bằng chứng đã che token và dữ liệu nhạy cảm.
6. Chỉ triển khai Internet sau khi vòng nghiệm thu trên môi trường độc lập đạt.

## 7. RAG, backup và health

- Health: `GET /actuator/health`.
- RAG Admin: `GET /api/v1/admin/rag/status` và `POST /api/v1/admin/rag/reindex` bằng tài khoản Admin.
- Đánh giá RAG: `scripts/week12b-rag-evaluation.ps1`.
- Backup: `scripts/backup-postgres.ps1`.
- Restore verification chỉ chạy bằng `scripts/verify-backup-restore.ps1`; script tạo database tạm và không được đổi đích sang database chính.

## 8. Cảnh báo không chặn bàn giao

- Angular production build cảnh báo thư viện `qrcode` là CommonJS; build vẫn thành công.
- Flutter báo 15 package có phiên bản mới không tương thích constraint hiện tại; analyzer, test và APK vẫn đạt. Không nâng đồng loạt trước demo.
- Git đang chứa ba CSV khoảng 70 MB; tất cả dưới giới hạn 100 MB/file. `Books_Cleaned.csv` là nguồn đang được Backend/Liquibase sử dụng. Việc tinh gọn lịch sử Git là tối ưu tùy chọn, không phải lỗi chức năng.
- Kết quả RAG trực tuyến phụ thuộc quota và độ sẵn sàng của Gemini; fallback cục bộ là hành vi thiết kế.
- Chưa xác nhận domain, HTTPS, production secret, monitoring và backup trên máy chủ Internet.

## 9. Kết luận

Phần kỹ thuật 12D đã hoàn tất ở mức **sẵn sàng tạo commit và push để kiểm thử độc lập**. Việc còn lại theo quy trình nhóm là: chủ repository yêu cầu commit/push → thành viên kiểm thử clone đúng commit → phản hồi lỗi → thành viên code sửa và chạy hồi quy → thành viên triển khai đưa bản đã nghiệm thu lên Internet.

## 10. Phụ lục cập nhật 12E ngày 21/10/2026

Sau bản chốt 12D, repository đã bổ sung cập nhật hồ sơ Mobile, tìm kiếm theo chuyên ngành, loại dữ liệu Mobile cố định và chuỗi Firebase Cloud Messaging tùy chọn. Gate mới nhất đạt **Backend 121/121 test**, **Web 69/69 test và production build**, **Mobile analyze sạch, 5/5 test và APK debug build thành công**. Trạng thái chi tiết và cấu hình kiểm thử thiết bị nằm trong `TUAN_12E_HOAN_THIEN_MOBILE_VA_FIREBASE.md`; gửi FCM end-to-end vẫn chờ Firebase project/service account và thiết bị thật của người kiểm thử.

## 11. Phụ lục chốt sửa lỗi UAT ngày 24/10/2026

Các phản hồi UAT-A đến UAT-F đã được xử lý ở mức mã nguồn, gồm giỏ mượn nhiều tài liệu, trả sách liên phiếu, đánh giá và tủ sách, tìm kiếm theo nội dung, modal thông báo, cài đặt kênh nhận tin, thẻ thư viện, chống spam giữ chỗ, loại dữ liệu trang chủ giả và quy trình đề xuất mua sách đầy đủ. Khi duyệt đề xuất, hệ thống chỉ tạo đầu sách với **0 bản sao**; bản sao chỉ được tạo qua nghiệp vụ phiếu nhập.

Gate cục bộ cuối:

| Gate | Kết quả ngày 24/10/2026 |
|---|---|
| Backend unit test | **PASS — 122/122 test** |
| Backend package | **PASS — tạo `library-0.0.1-SNAPSHOT.jar`** |
| Backend integration test | **PENDING — 3 test dùng Testcontainers cần Docker daemon** |
| Web test | **PASS — 82/82 test** |
| Web production build | **PASS**; chỉ còn cảnh báo `qrcode` CommonJS không chặn build |
| Web dependency audit | Đã áp dụng toàn bộ bản vá tương thích; còn 5 cảnh báo trong toolchain Angular/Vite chỉ có bản sửa qua nâng major, chưa dùng `--force` để tránh phá vỡ ứng dụng |
| Kiểm tra diff | **PASS — không có whitespace error** |
| Rà secret đã track | **PASS — không phát hiện API key/Gemini key trong file được Git theo dõi** |

Người nhận bản bàn giao cần chạy lại toàn bộ Backend suite trong môi trường Docker, sau đó thực hiện lần lượt các checklist `07_UAT_A_KIEM_THU_LAI.md` đến `12_UAT_F_DE_XUAT_MUA_SACH_VA_QUY_DINH.md`. Firebase foreground, background và terminated vẫn là kiểm thử bắt buộc trên thiết bị Android thật.
