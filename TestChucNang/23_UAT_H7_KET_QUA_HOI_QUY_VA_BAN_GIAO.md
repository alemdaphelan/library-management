# H7 - Kết quả hồi quy và bàn giao kiểm thử

Ngày chốt cục bộ: **27/10/2026**
Nhánh bàn giao: **`feature/week-6-backend-apis`**

## Kết quả gate tự động

| Thành phần | Lệnh kiểm tra | Kết quả |
|---|---|---|
| Backend | `mvnw test` | PASS — 176/176 test |
| Backend package | `mvnw --batch-mode -DskipTests package` | PASS |
| Web | `npm test -- --watch=false` | PASS — 122/122 test |
| Web production | `npm run build` | PASS |
| Web dependencies | `npm audit --audit-level=high` | PASS — 0 lỗ hổng |
| Mobile static analysis | `flutter analyze` | PASS — không có lỗi |
| Mobile test | `flutter test` | PASS — 7/7 test |
| Mobile APK | `flutter build apk --debug` | PASS |
| Repository preflight | `scripts/week12d-preflight.ps1` | PASS — 0 lỗi |
| Docker Compose | `docker compose --env-file .env.example config --quiet` | PASS |

## Kết quả rà soát mã nguồn

- Không phát hiện API key, access token hoặc private key trong phần bàn giao.
- Không theo dõi `.env`, thư mục build, `node_modules` hoặc dữ liệu sinh tự động.
- Không còn `window.alert`/`window.confirm` trong luồng Web; thao tác xác nhận dùng modal dùng chung.
- Không phát hiện dữ liệu mock trong mã chạy của Backend, Web và Mobile. Các từ `mock` còn lại thuộc unit test hoặc tiêu đề sách trong bộ dữ liệu nhập ban đầu.
- Dữ liệu sách mẫu trong Liquibase/CSV là dữ liệu khởi tạo môi trường phát triển, không phải kết quả giả được gắn cứng trên giao diện.

## Cảnh báo không chặn bàn giao

- Ba file dữ liệu sách CSV lớn hơn 50 MB đã tồn tại trong repository; preflight chỉ cảnh báo, không làm thất bại gate.
- Web build cảnh báo thư viện `qrcode` dùng CommonJS; build production vẫn thành công.
- Camera QR/barcode phải kiểm tra bằng thiết bị thật trên `localhost` hoặc HTTPS.
- VNPAY cần bộ thông tin sandbox thật: `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, Return URL và IPN URL.
- Firebase Push cần `google-services.json` của dự án Firebase thật và kiểm tra foreground/background/terminated trên thiết bị.

## Phạm vi thành viên kiểm thử cần chạy lại

1. Làm theo `02_CHECKLIST_TEST_TOAN_BO_HE_THONG.md`.
2. Chạy thêm `21_UAT_H6_THANH_TOAN_TRUC_TUYEN_VNPAY.md` khi đã có cấu hình sandbox.
3. Chạy `22_UAT_H6B_CAMERA_QR_BARCODE_DA_TRINH_DUYET.md` trên máy có camera.
4. Ghi đúng commit nhận bàn giao vào biên bản, không kiểm thử dựa trên thư mục cũ hoặc cache trình duyệt cũ.
5. Mọi lỗi phải kèm tài khoản/role, bước tái hiện, request/response liên quan và ảnh hoặc video.
