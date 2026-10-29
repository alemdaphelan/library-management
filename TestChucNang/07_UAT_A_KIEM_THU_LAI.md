# KIỂM THỬ LẠI GÓI UAT-A

Tài liệu này dùng để xác nhận các lỗi bảo mật và toàn vẹn dữ liệu được sửa sau vòng kiểm thử người dùng đầu tiên. Ghi rõ commit đang kiểm thử vào biên bản nghiệm thu.

## A01 - Chặn truy cập trang cá nhân khi chưa đăng nhập

1. Đăng xuất và xóa dữ liệu đăng nhập của trình duyệt.
2. Truy cập trực tiếp `/profile`, `/history`, `/notifications` và `/suggest`.
3. Xác nhận mọi đường dẫn đều chuyển về `/login` và không hiển thị dữ liệu cá nhân.

Kết quả mong đợi: `PASS` khi cả bốn route đều bị chặn.

## A02 - Không coi role cũ là phiên đăng nhập hợp lệ

1. Đăng nhập, sau đó dùng Developer Tools xóa riêng access token nhưng giữ lại role.
2. Tải lại một route cá nhân.

Kết quả mong đợi: hệ thống chuyển về trang đăng nhập.

## A03 - Gửi đề xuất không bị treo

1. Đăng nhập bằng tài khoản sinh viên.
2. Gửi một đề xuất có tên sách và lý do hợp lệ.
3. Quan sát nút gửi, thông báo kết quả và lịch sử đề xuất.
4. Bấm gửi nhanh nhiều lần trong lúc request đầu tiên chưa hoàn tất.

Kết quả mong đợi: nút thoát trạng thái `Đang gửi`, hiển thị thành công hoặc lỗi cụ thể, và chỉ tạo đúng một đề xuất.

## A04 - API đề xuất bắt buộc xác thực

1. Gọi `POST /proposals` không kèm JWT.
2. Gọi API cũ `POST /feedback/proposals` không kèm JWT.
3. Kiểm tra lại danh sách đề xuất bằng tài khoản Admin.

Kết quả mong đợi: `/proposals` trả `401/403`; API cũ không còn tạo được dữ liệu; không xuất hiện đề xuất gắn với người dùng giả.

## A05 - Chặn đăng ký trùng đầu sách

1. Sinh viên tạo yêu cầu mượn hoặc đặt giữ một đầu sách A.
2. Khi phiếu đầu tiên còn `PENDING`, `RESERVED`, `BORROWING` hoặc `OVERDUE`, đăng ký lại đúng đầu sách A.
3. Hoàn tất/hủy phiếu cũ rồi thử đăng ký lại.

Kết quả mong đợi: lần thứ hai bị từ chối bằng thông báo nêu rõ đầu sách đã có trong phiếu hoạt động; sau khi phiếu cũ hoàn tất hoặc hủy thì có thể đăng ký lại nếu còn đủ điều kiện.

## A06 - Hiển thị đúng lỗi nghiệp vụ mượn sách

Thử lần lượt với tài khoản đạt giới hạn ba tài liệu, còn công nợ, có sách quá hạn, thẻ bị khóa và mượn trùng đầu sách.

Kết quả mong đợi: giao diện hiển thị lý do cụ thể do backend trả về, không chỉ hiện `Không thể gửi yêu cầu mượn`.

## A07 - Hồ sơ tải ngay lần mở đầu tiên

1. Đăng nhập từ trang login.
2. Mở trang quản lý tài khoản ngay, không tải lại trình duyệt.
3. Chuyển qua lại giữa trang chủ và hồ sơ.

Kết quả mong đợi: thông tin hồ sơ tải ngay mỗi lần component được mở; nếu phiên hết hạn phải hiện thông báo rõ ràng.

## Hồi quy tối thiểu

- Admin/Thủ thư vẫn mở được danh sách đề xuất và duyệt/từ chối đề xuất.
- Sinh viên đã đăng nhập vẫn xem được lịch sử mượn và thông báo.
- Mượn đầu sách khác vẫn hoạt động khi tài khoản đủ điều kiện.
- Sau khi trả/hủy tài liệu, bản sao và phiếu cũ không bị thay đổi sai bởi kiểm tra trùng đầu sách.

## Kết quả

| Ca | PASS/FAIL/BLOCKED | Mã lỗi nếu có | Ghi chú |
|---|---|---|---|
| A01 |  |  |  |
| A02 |  |  |  |
| A03 |  |  |  |
| A04 |  |  |  |
| A05 |  |  |  |
| A06 |  |  |  |
| A07 |  |  |  |
