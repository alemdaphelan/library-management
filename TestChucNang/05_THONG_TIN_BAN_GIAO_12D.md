# THÔNG TIN BẢN BÀN GIAO 12D

## Phiên bản cần ghi khi nhận repository

- Nhánh chuẩn bị: `feature/week-6-backend-apis`.
- Commit nền trước khi đóng gói: `ebe2c4c`.
- Commit chính thức để test: dùng commit mới nhất do chủ repository gửi trên nhánh `feature/week-6-backend-apis`; ghi lại bằng `git rev-parse HEAD` trước khi test.
- Ngày chạy gate nền 12D: **20/10/2026**; gate bổ sung Mobile/Firebase 12E: **21/10/2026**.

Không bắt đầu test nếu người gửi chưa cung cấp nhánh và commit chính thức. Không tự suy đoán hoặc kiểm thử một working tree khác commit bàn giao.

## Tài khoản demo cục bộ

| Vai trò | Đăng nhập | Mật khẩu |
|---|---|---|
| Admin | `admin@huit.edu.vn` | `admin123` |
| Librarian | `librarian@huit.edu.vn` | `admin123` |
| Student | `student@student.huit.edu.vn` hoặc `20012001` | `admin123` |

Các tài khoản này chỉ dùng cho local/test. Không sử dụng khi đưa hệ thống lên Internet.

## Gate đã đạt trước bàn giao

- Backend: 121 test pass và JAR build thành công; refresh token bị thu hồi trả HTTP 401.
- Web: npm audit không có vulnerability, 69 test pass và production build thành công.
- Mobile: analyze sạch, 5 test pass và APK debug build thành công.
- FCM: mã nguồn và cấu hình tùy chọn đã hoàn tất; gửi thật trên thiết bị vẫn phải chạy các ca NOTI-04 đến NOTI-07 với Firebase project/service account riêng.
- Docker Backend healthy; smoke health/catalog đạt.
- 200 request tải cục bộ đều HTTP 200; p95 681,44 ms.
- Backup/restore PostgreSQL đạt với 51 bảng.
- Preflight không phát hiện secret hoặc file build bị Git theo dõi.

Chi tiết và giới hạn: `../Docs/TUAN_12D_CHOT_REPOSITORY_VA_BAN_GIAO.md`.

## Việc của người kiểm thử

1. Clone đúng commit chính thức.
2. Tạo `.env` riêng từ `.env.example`; không nhận `.env` từ người khác.
3. Thực hiện `06_CAU_HINH_FIREBASE_VA_KIEM_THU_PUSH.md`, gồm đổi package ID thành `vn.edu.huit.library`, tạo Firebase Project/App và cấu hình môi trường.
4. Dựng hệ thống theo `01_HUONG_DAN_DUNG_MOI_TRUONG_TEST.md`.
5. Chạy `02_CHECKLIST_TEST_TOAN_BO_HE_THONG.md` và ghi đủ PASS/FAIL/BLOCKED/N/A, gồm bằng chứng push foreground/background/terminated.
6. Dùng `03_MAU_BAO_LOI.md` cho từng lỗi.
7. Chốt `04_BIEN_BAN_NGHIEM_THU.md` trước khi triển khai Internet.

## Phụ lục cập nhật G1-G8 ngày 25/10/2026

Sau vòng phản hồi tiếp theo, mã nguồn đã bổ sung bảo vệ các trang cá nhân, hồ sơ và thẻ thư viện, tra cứu khả dụng/đặt giữ trong ngày, điều kiện đánh giá và tìm theo nội dung, đọc PDF thật, lý do từ chối và module công nợ riêng cho sinh viên. Sinh viên mở `Công nợ & Khoản phạt` từ menu avatar để xem các dòng ghi nợ, thanh toán một phần, hồ sơ mất/hỏng và lịch sử biên nhận; không có quyền sửa hoặc xóa giao dịch. G8 bổ sung hồ sơ bạn đọc tổng hợp cho nhân viên gồm thẻ, tiền thế chân, công nợ, vi phạm, phiếu mượn và hồ sơ phạt; Thủ thư chỉ xem, Admin giữ quyền quản trị.

Gate cục bộ mới nhất sau G8: **Backend unit test 137/137**, **Web test 107/107**, **Web production build PASS**. Ba integration test Testcontainers vẫn phải chạy lại khi Docker daemon hoạt động. Thực hiện tiếp các checklist `13_UAT_G1_DANG_NHAP_HO_SO_VA_THE.md` đến `20_UAT_G8_HO_SO_BAN_DOC_VA_THE.md` trên đúng commit được bàn giao.
