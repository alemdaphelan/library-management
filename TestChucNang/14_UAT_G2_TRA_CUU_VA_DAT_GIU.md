# UAT-G2 — TRA CỨU KHẢ DỤNG VÀ ĐẶT GIỮ TRONG NGÀY

Checklist này kiểm tra bản sửa G2 gồm bộ lọc bản sao có sẵn, hủy đặt giữ, hạn nhận trong ngày và cách phân loại lịch sử phiếu.

## Chuẩn bị

- Một tài khoản sinh viên đang hoạt động và đủ điều kiện mượn.
- Một đầu sách A có ít nhất một bản sao `AVAILABLE`.
- Một đầu sách B không có bản sao `AVAILABLE`.
- Một tài khoản Admin/Librarian để đối chiếu trạng thái bản sao.
- Ghi lại mã commit, book ID, barcode và ticket ID dùng kiểm thử.

## G2-01 — Bộ lọc “Có sẵn” chạy trước phân trang

1. Bảo đảm có ít nhất một đầu sách có bản `AVAILABLE`, kể cả khi đầu sách đó không nằm ở trang đầu của danh mục.
2. Mở “Tìm kiếm & tra cứu”, chọn “Có sẵn”.
3. Chuyển trang và tìm kiếm bằng từ khóa nếu danh mục có nhiều dữ liệu.

Kết quả mong đợi:

- Tổng kết quả và tổng số trang được tính từ những đầu sách thực sự có bản `AVAILABLE`.
- Không xuất hiện màn hình trắng kèm phân trang của toàn bộ danh mục.
- Mỗi thẻ hiện số bản sẵn sàng trên tổng số bản sao.
- Nếu không có kết quả, giao diện hiện thông báo rõ ràng và gợi ý bỏ bộ lọc.

## G2-02 — Bộ lọc “Hết bản sẵn sàng”

1. Chọn “Hết bản sẵn sàng”.
2. Đối chiếu một đầu sách không có bản `AVAILABLE` với Database/API.

Kết quả mong đợi: chỉ các đầu sách có số bản sẵn sàng bằng 0 xuất hiện; nhãn không gọi nhầm tất cả chúng là “Đang mượn” vì bản sao có thể đang đặt giữ, hỏng hoặc bảo trì.

## G2-03 — Hủy phiếu và trả bản sao về kho khả dụng

1. Tạo một phiếu trực tiếp cho sách A, ghi lại barcode.
2. Kiểm tra bản sao chuyển sang `RESERVED`.
3. Từ tab “Chờ nhận sách tại quầy”, hủy phiếu.
4. Mở lại trang tra cứu và chọn “Có sẵn”.

Kết quả mong đợi:

- Phiếu chuyển `CANCELLED`, chi tiết chuyển `CANCELLED`.
- Bản sao chuyển `AVAILABLE`, xóa `locked_by_user` và `locked_until`.
- Sách A xuất hiện lại trong bộ lọc “Có sẵn” mà không cần sửa Database thủ công.
- Nếu đầu sách có người đang chờ hợp lệ, bản sao được chuyển ngay cho người đầu hàng và tiếp tục `RESERVED`; trường hợp này không được xem là lỗi.

## G2-04 — Có thể hủy cả phiếu PENDING và RESERVED

1. Hủy một phiếu chưa được thủ thư duyệt (`PENDING`).
2. Tạo phiếu khác, cho thủ thư duyệt thành `RESERVED`, sau đó hủy từ tài khoản sinh viên.

Kết quả mong đợi: cả hai trường hợp đều hủy được trước khi checkout; phiếu `BORROWING`, `OVERDUE` hoặc `RETURNED` không có nút hủy và Backend từ chối nếu gọi API trực tiếp.

## G2-05 — Hạn nhận chỉ trong ngày tạo phiếu

1. Trong giờ mở cửa, mở form mượn một cuốn và giỏ mượn nhiều cuốn.
2. Kiểm tra trường ngày nhận.
3. Tạo phiếu và đối chiếu `pickupDeadline` với `book_copies.locked_until`.
4. Gọi API thủ công với ngày nhận là ngày mai.

Kết quả mong đợi:

- Giao diện chỉ cho ngày hôm nay.
- Hạn nhận là giờ đóng cửa hôm nay, không phải hạn trả sau 10 ngày.
- API từ chối ngày khác hôm nay bằng thông báo nghiệp vụ rõ ràng.
- Thời hạn mượn 10 ngày chỉ được tính lại khi thủ thư checkout sách.

## G2-06 — Quy tắc 48 giờ chỉ dành cho hàng đợi

1. Cho sách B hết bản và đăng ký hàng đợi.
2. Trả một bản sao để hệ thống gọi người đầu hàng.

Kết quả mong đợi: lượt `NOTIFIED` trong hàng đợi vẫn được giữ 48 giờ và xử lý ngày nghỉ/giờ đóng cửa; thay đổi G2 không rút lượt hàng đợi xuống còn trong ngày.

## G2-07 — Tách lịch sử hoàn trả và phiếu đã hủy

1. Chuẩn bị một phiếu `RETURNED`, một `CANCELLED` và một `REJECTED`.
2. Mở trang lịch sử.

Kết quả mong đợi:

- “Lịch sử hoàn trả” chỉ chứa tài liệu `RETURNED`.
- Tab “Phiếu đã hủy” chứa `CANCELLED` và `REJECTED`.
- `RESERVED` nằm trong “Chờ nhận sách tại quầy”; `OVERDUE` vẫn nằm trong nhóm đang mượn.
- “Tất cả phiếu mượn” hiển thị đúng tên từng trạng thái.

## Ghi kết quả

Ghi từng ca là `PASS`, `FAIL` hoặc `BLOCKED`. Với lỗi khả dụng, bắt buộc đính kèm response `GET /books?...&availability=AVAILABLE`, trạng thái `book_copies`, ticket ID và barcode; che JWT trước khi gửi.
