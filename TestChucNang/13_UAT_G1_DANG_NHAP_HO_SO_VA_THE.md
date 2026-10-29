# UAT-G1 — ĐĂNG NHẬP, HỒ SƠ, THẺ THƯ VIỆN VÀ ĐIỀU HƯỚNG ROLE

Checklist này dùng để kiểm tra lại nhóm lỗi G1 sau khi nhận bản sửa. Hãy chạy bằng cửa sổ ẩn danh hoặc xóa `localStorage` trước khi bắt đầu để tránh phiên đăng nhập cũ ảnh hưởng kết quả.

## Dữ liệu cần chuẩn bị

- Một tài khoản `STUDENT` đã được cấp thẻ thư viện.
- Một tài khoản `STUDENT` chưa được cấp thẻ thư viện (`card_status` chưa có giá trị).
- Một tài khoản `LIBRARIAN`.
- Một tài khoản `ADMIN`.
- Backend đã chạy đủ migration, đặc biệt migration tạo bảng cài đặt thông báo.

## G1-01 — Khách chưa đăng nhập

1. Xóa dữ liệu đăng nhập của trình duyệt hoặc mở cửa sổ ẩn danh.
2. Mở trang chủ Portal.
3. Kiểm tra thanh điều hướng.
4. Nhập trực tiếp URL `/profile`, `/history`, `/suggest`, `/borrow-cart` và `/saved-books`.

Kết quả mong đợi:

- Thanh điều hướng có nút đăng nhập.
- Không hiện các mục riêng tư như lịch sử, đề xuất, hồ sơ, thẻ thư viện hoặc khu vực quản trị.
- Khi truy cập trực tiếp các URL riêng tư, hệ thống chuyển đến `/login` và không để lộ dữ liệu tài khoản.

## G1-02 — Phiên đăng nhập hết hạn hoặc token sai

1. Đăng nhập rồi thay access token trong `localStorage` bằng token sai hoặc token đã hết hạn.
2. Tải lại Portal và thử mở `/profile`.

Kết quả mong đợi:

- Hệ thống không coi token sai/hết hạn là phiên hợp lệ.
- Người dùng được chuyển về trang đăng nhập; không còn thấy nội dung riêng tư từ phiên cũ.

## G1-03 — Hồ sơ sinh viên tải ngay lần đầu

1. Đăng nhập bằng tài khoản `STUDENT`.
2. Mở trang hồ sơ mà không reload thủ công.
3. Chuyển qua lại giữa tab thông tin cá nhân và cài đặt thông báo.

Kết quả mong đợi:

- Có trạng thái đang tải rõ ràng, sau đó tên, MSSV, khoa và thông tin liên hệ xuất hiện ngay.
- Không cần reload trang để dữ liệu hiện ra.
- Lỗi cài đặt thông báo, nếu có, không làm mất nội dung hồ sơ cá nhân.
- Khi mở tab cài đặt thông báo, dữ liệu mới được tải; nếu lỗi có thông báo và nút thử lại.

## G1-04 — Cài đặt thông báo

1. Trong hồ sơ, mở tab cài đặt thông báo.
2. Thay đổi từng kênh được hỗ trợ và lưu.
3. Tải lại trang rồi mở lại tab.

Kết quả mong đợi:

- Không hiển thị công tắc giả trước khi tải dữ liệu thành công.
- Lưu thành công có phản hồi rõ ràng.
- Cấu hình vừa lưu vẫn đúng sau khi tải lại.

Nếu API báo thiếu bảng/cột, đánh dấu `BLOCKED` và đính kèm log migration thay vì kết luận lỗi giao diện.

## G1-05 — Tài khoản chưa được cấp thẻ

1. Đăng nhập bằng tài khoản sinh viên chưa có `card_status`.
2. Mở mục “Thẻ thư viện”.

Kết quả mong đợi:

- Hiện thông báo “Bạn chưa được cấp thẻ thư viện”.
- Không dùng MSSV để giả làm số thẻ.
- Không hiện ngày lập, ngày hết hạn hoặc trạng thái “Chưa xác định” như thể đã có thẻ.

## G1-06 — Tài khoản đã được cấp thẻ

1. Đăng nhập bằng tài khoản sinh viên đã có thẻ.
2. Mở mục “Thẻ thư viện”.

Kết quả mong đợi:

- Hiện đúng chủ sở hữu, mã thẻ, trạng thái, ngày lập, ngày hết hạn, tiền thế chân và công nợ.
- Dữ liệu phải khớp Backend/Database, không phải dữ liệu cố định.

## G1-07 — Chuyển đổi Portal và trang quản trị

1. Đăng nhập bằng `ADMIN`, từ Portal mở menu tài khoản và chọn vào khu vực quản trị.
2. Từ giao diện quản trị chọn “Về Cổng Sinh viên”.
3. Lặp lại với `LIBRARIAN`.

Kết quả mong đợi:

- `ADMIN` và `LIBRARIAN` đi được hai chiều giữa Portal và giao diện quản trị.
- `STUDENT` không thấy liên kết và không truy cập được `/admin`.

## G1-08 — Phân quyền quản lý tài khoản

1. Mở “Hồ sơ Bạn đọc & Thẻ” bằng `LIBRARIAN`.
2. Mở cùng trang bằng `ADMIN`.
3. Kiểm tra một sinh viên chưa được cấp thẻ.

Kết quả mong đợi:

- `LIBRARIAN` chỉ xem và tra cứu, không có thao tác tạo/sửa/xóa tài khoản.
- `ADMIN` có các thao tác quản trị được hệ thống cho phép.
- Sinh viên chưa có thẻ hiển thị “Chưa cấp thẻ”, không phải “Chưa xác định”.

## G1-09 — Đăng xuất

1. Đăng nhập bằng một tài khoản bất kỳ.
2. Bấm đăng xuất trong lúc Backend hoạt động; sau đó lặp lại khi Backend tạm ngắt.
3. Dùng nút Back của trình duyệt để thử quay lại trang riêng tư.

Kết quả mong đợi:

- Dữ liệu phiên bị xóa và giao diện chuyển về trạng thái khách ngay lập tức ở cả hai trường hợp.
- Không thể quay lại xem nội dung riêng tư bằng nút Back.

## Ghi kết quả

Ghi từng ca là `PASS`, `FAIL` hoặc `BLOCKED`. Với `FAIL`, đính kèm URL, role, bước tái hiện, ảnh/video, response API và console log đã che token hoặc dữ liệu nhạy cảm.
