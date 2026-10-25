# KIỂM THỬ GÓI G5 - TỪ CHỐI YÊU CẦU VÀ CÔNG NỢ SINH VIÊN

## Chuẩn bị dữ liệu

1. Có một tài khoản sinh viên đang hoạt động.
2. Tạo ít nhất hai yêu cầu mượn và hai đề xuất mua sách ở trạng thái chờ duyệt.
3. Với sinh viên đó, chuẩn bị một khoản phạt quá hạn thanh toán một phần, một hồ sơ mất/hỏng và một giao dịch đã xuất biên nhận.
4. Không sửa trực tiếp trạng thái bằng SQL trong lúc chạy ca kiểm thử; thao tác qua giao diện hoặc API nghiệp vụ.

## G5-01 - Từ chối yêu cầu mượn bắt buộc có lý do

1. Đăng nhập `ADMIN` hoặc `LIBRARIAN`, mở danh sách yêu cầu mượn.
2. Chọn một phiếu đang chờ và bấm từ chối.
3. Để trống lý do rồi xác nhận.
4. Nhập lý do hợp lệ và xác nhận lần nữa.

Kết quả mong đợi: lần đầu hệ thống không gọi API và phiếu vẫn ở trạng thái chờ; lần hai phiếu mới chuyển sang `REJECTED`, lý do được lưu và các bản sao đặt giữ được giải phóng.

## G5-02 - Sinh viên xem lý do phiếu mượn bị từ chối

1. Đăng nhập lại bằng sinh viên của phiếu ở G5-01.
2. Vào `Lịch sử mượn sách` → `Phiếu đã hủy`.

Kết quả mong đợi: phiếu có nhãn `Đã từ chối` và hiển thị đúng nội dung `Lý do từ chối`; phiếu tự hủy chỉ hiển thị trạng thái hủy, không giả thành lý do của thủ thư.

## G5-03 - Từ chối đề xuất mua sách

1. Đăng nhập nhân viên thư viện, mở `Duyệt Đề xuất sách`.
2. Chọn đề xuất đang chờ, bấm từ chối nhưng để trống phản hồi.
3. Nhập lý do rồi xác nhận.
4. Đăng nhập sinh viên gửi đề xuất và mở lịch sử đề xuất.

Kết quả mong đợi: không thể từ chối khi thiếu lý do; chỉ sau xác nhận mới chuyển `REJECTED`; sinh viên thấy nguyên văn `Phản hồi từ thủ thư`.

## G5-04 - Bảo vệ trang công nợ cá nhân

1. Khi chưa đăng nhập, truy cập trực tiếp `/my-penalties`.
2. Sau khi đăng nhập sinh viên A, mở trang từ menu avatar `Công nợ & Khoản phạt`.
3. Thử thay ID người dùng trong URL hoặc gọi API sổ nợ của người khác bằng JWT sinh viên.

Kết quả mong đợi: người chưa đăng nhập bị chuyển tới đăng nhập; trang chỉ dùng API `/my-ledger` và `/my`, không nhận ID từ client; sinh viên không đọc được sổ nợ của tài khoản khác.

## G5-05 - Tổng quan và thanh toán một phần

1. Kiểm tra bốn thẻ tổng quan.
2. Đối chiếu một khoản 10.000 đ đã trả 4.000 đ.
3. Đối chiếu tổng còn phải thanh toán với tổng các dòng `Còn lại`.

Kết quả mong đợi: khoản trên còn 6.000 đ; giao dịch thanh toán âm không bị cộng thành khoản nợ mới; tổng tiền, số khoản mở và số hồ sơ mất/hỏng khớp dữ liệu Backend.

## G5-06 - Hồ sơ mất/hỏng và miễn giảm

1. Mở phần `Hồ sơ mất / hỏng tài liệu`.
2. Kiểm tra mã vạch, phạt quá hạn, sửa chữa, đền bù, phí kỹ thuật, tổng tiền, số đã trả và hạn xử lý.
3. Nếu có hồ sơ đã duyệt miễn giảm, kiểm tra lý do quyết định.

Kết quả mong đợi: các loại phí được tách riêng; phí kỹ thuật không bị nhân hệ số; số còn lại bằng tổng trừ số đã trả; hồ sơ không thuộc tài khoản hiện tại không xuất hiện.

## G5-07 - Lịch sử thanh toán và biên nhận

1. Kiểm tra danh sách lịch sử thanh toán.
2. Đối chiếu mã biên nhận, số tiền, phương thức và thời gian xác nhận với màn hình thu tiền của thủ thư.
3. Làm mới trình duyệt và mở lại trang.

Kết quả mong đợi: dữ liệu vẫn giữ nguyên sau tải lại; tiền thanh toán hiển thị số dương dễ đọc dù dòng sổ cái lưu âm; giao dịch đã có biên nhận không có nút xóa hay sửa từ phía sinh viên.

## G5-08 - Trạng thái rỗng và lỗi API

1. Mở trang bằng sinh viên chưa từng phát sinh công nợ.
2. Tắt Backend hoặc làm API trả lỗi rồi bấm `Làm mới dữ liệu`.

Kết quả mong đợi: khi không nợ hiển thị xác nhận rõ ràng, không phải bảng trắng; khi lỗi có thông báo và nút thử lại, không làm lộ dữ liệu cũ của tài khoản trước.

## Kết quả

| Ca | PASS/FAIL/BLOCKED | Mã lỗi nếu có | Ghi chú |
|---|---|---|---|
| G5-01 |  |  |  |
| G5-02 |  |  |  |
| G5-03 |  |  |  |
| G5-04 |  |  |  |
| G5-05 |  |  |  |
| G5-06 |  |  |  |
| G5-07 |  |  |  |
| G5-08 |  |  |  |
