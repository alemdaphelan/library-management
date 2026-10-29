# UAT-D — Thông báo, trạng thái tài khoản và thẻ thư viện

## Phạm vi

Gói này thay phần thông báo/cài đặt giả lập bằng dữ liệu thật. Thông báo của sinh viên được mở bằng modal trên thanh đầu trang; cổng sinh viên không còn trang dashboard thông báo riêng. Thẻ thư viện cũng mở bằng modal và đọc thông tin tài khoản, ví, công nợ từ Backend.

## Điều kiện

- Chạy migration `21-uat-d-notification-preferences.yaml`.
- Có một tài khoản sinh viên, một tài khoản Admin/Thủ thư và ít nhất một ví của sinh viên.
- Nếu kiểm tra Email, cấu hình SMTP thật. Nếu kiểm tra Push, hoàn tất tài liệu `06_CAU_HINH_FIREBASE_VA_KIEM_THU_PUSH.md`.
- SMS không thuộc gói này vì repository chưa cấu hình nhà cung cấp SMS; giao diện không được phép báo lưu SMS thành công giả.

## Ca kiểm thử

| Mã | Thao tác | Kết quả mong đợi | Kết quả | Ghi chú |
|---|---|---|---|---|
| UAT-D01 | Đăng nhập sinh viên, quan sát thanh đầu trang | Có nút Thông báo và số lượng chưa đọc; không còn mục THÔNG BÁO trên thanh menu chính |  |  |
| UAT-D02 | Bấm nút Thông báo | Modal mở tại trang hiện tại, tải đúng thông báo của tài khoản; không điều hướng sang dashboard |  |  |
| UAT-D03 | Bấm một thông báo chưa đọc rồi mở lại modal | Thông báo chuyển đã đọc qua API và bộ đếm giảm |  |  |
| UAT-D04 | Bấm “Đánh dấu tất cả đã đọc” | Tất cả dòng chưa đọc được cập nhật, bộ đếm về 0 |  |  |
| UAT-D05 | Hồ sơ → Cài đặt thông báo, tắt Email/bật Push rồi lưu và tải lại trang | Giá trị vẫn giữ nguyên sau reload vì được lưu trong PostgreSQL |  |  |
| UAT-D06 | Tắt Push rồi tạo một sự kiện thông báo nghiệp vụ | Thông báo vẫn có trong modal nhưng Firebase không gửi tới thiết bị |  |  |
| UAT-D07 | Bật Email rồi tạo sự kiện thông báo với SMTP đã cấu hình | Email được gửi tới email của tài khoản; nếu SMTP lỗi, thông báo trong hệ thống vẫn tồn tại |  |  |
| UAT-D08 | Admin duyệt một đề xuất sách | Sinh viên nhận thông báo “Đề xuất được duyệt” trong modal và qua kênh đang bật |  |  |
| UAT-D09 | Admin từ chối đề xuất mà không nhập lý do | Backend từ chối yêu cầu; khi nhập lý do hợp lệ, sinh viên nhận thông báo kèm lý do |  |  |
| UAT-D10 | Mở trang hồ sơ | Hiển thị trạng thái thẻ thật, hạn thẻ và lý do/thời hạn khóa nếu có |  |  |
| UAT-D11 | Bấm “Thẻ thư viện” | Modal hiển thị đúng MSSV, chủ thẻ, khoa, ngày lập/hết hạn, tiền thế chân, công nợ và trạng thái |  |  |
| UAT-D12 | Kiểm thử với thẻ LOCKED hoặc EXPIRED | Modal và hồ sơ thể hiện đúng trạng thái, không tô xanh như ACTIVE |  |  |

## Hồi quy bắt buộc

- Truy cập trực tiếp `/profile` khi chưa đăng nhập vẫn bị AuthGuard chuyển tới đăng nhập.
- Menu avatar vẫn mở được Tủ sách cá nhân, lịch sử mượn và đổi mật khẩu.
- Trang thông báo Admin `/admin/notifications` vẫn hoạt động; thay đổi modal chỉ áp dụng cổng sinh viên.
- Mobile tiếp tục đọc `GET /api/v1/notifications` và đánh dấu đã đọc như trước.
