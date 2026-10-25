# KIỂM THỬ GÓI G4 - E-BOOK VÀ ĐIỀU HƯỚNG PORTAL/ADMIN

## Chuẩn bị dữ liệu

1. Đăng nhập bằng `ADMIN` hoặc `LIBRARIAN`.
2. Vào `Quản trị Tài liệu số`, tải lên một tệp PDF thật và liên kết với một đầu sách cụ thể.
3. Đặt quyền tài liệu là `AUTHENTICATED` để sinh viên đọc được.
4. Chuẩn bị thêm một PDF `STAFF_ONLY` và một đầu sách không có PDF để kiểm tra phân quyền/trạng thái rỗng.

Không dùng bản ghi chỉ có metadata nhưng thiếu tệp vật lý trong thư mục lưu trữ.

## G4-01 - Đọc PDF từ trang chi tiết sách

1. Đăng nhập bằng sinh viên.
2. Mở đầu sách đã được liên kết PDF.
3. Bấm `Đọc E-book (PDF)`.

Kết quả mong đợi: hệ thống gọi API tài liệu số bằng JWT, mở PDF thật trong tab trình duyệt mới và ghi nhận lượt truy cập; không còn toast giả `Đang kết nối...`.

## G4-02 - Đầu sách chưa có PDF

1. Mở đầu sách không có tài liệu PDF được công bố.
2. Bấm `Đọc E-book (PDF)`.

Kết quả mong đợi: hiển thị thông báo `Đầu sách này chưa có bản PDF được thư viện công bố`; không mở tab rỗng và không phát sinh request tải tệp sai.

## G4-03 - Kho tài nguyên số

1. Vào `Tài nguyên số & CSDL`.
2. Với PDF, thử `Đọc trực tuyến` rồi thử `Tải tài liệu`.
3. Với EPUB/MP3/PPTX, kiểm tra nút tải xuống.

Kết quả mong đợi: PDF có cả đọc trực tuyến và tải xuống; định dạng chưa hỗ trợ xem trực tiếp vẫn tải được; tên và dung lượng lấy từ Backend.

## G4-04 - Phân quyền tài liệu

1. Đăng nhập sinh viên và kiểm tra danh sách tài liệu gắn với đầu sách.
2. Gọi trực tiếp API preview của tài liệu `STAFF_ONLY` bằng JWT sinh viên.
3. Lặp lại bằng tài khoản `ADMIN/LIBRARIAN`.

Kết quả mong đợi: sinh viên không nhìn thấy và bị chặn `403` khi cố truy cập `STAFF_ONLY`; nhân viên thư viện xem được.

## G4-05 - Điều hướng hai chiều

1. Đăng nhập `ADMIN`, sau đó lặp lại bằng `LIBRARIAN`.
2. Từ giao diện quản trị bấm `Về Cổng Sinh viên`.
3. Từ Portal bấm `Trang quản trị` ở topbar hoặc menu avatar.
4. Đăng nhập sinh viên và kiểm tra không xuất hiện nút quản trị.

Kết quả mong đợi: `ADMIN/LIBRARIAN` chuyển hai chiều mà không cần đăng nhập lại; sinh viên không được hiển thị liên kết và không được phép truy cập route quản trị.

## G4-06 - Trình duyệt chặn tab mới

1. Bật chế độ chặn popup/tab mới của trình duyệt.
2. Bấm đọc PDF.

Kết quả mong đợi: giao diện báo rõ cần cho phép mở tab mới, không treo ở trạng thái tải.

## Kết quả

| Ca | PASS/FAIL/BLOCKED | Mã lỗi nếu có | Ghi chú |
|---|---|---|---|
| G4-01 |  |  |  |
| G4-02 |  |  |  |
| G4-03 |  |  |  |
| G4-04 |  |  |  |
| G4-05 |  |  |  |
| G4-06 |  |  |  |
