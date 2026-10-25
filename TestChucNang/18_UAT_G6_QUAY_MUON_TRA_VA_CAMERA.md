# KIỂM THỬ GÓI G6 - QUẦY MƯỢN/TRẢ VÀ CAMERA

## Chuẩn bị

1. Dùng tài khoản `ADMIN` hoặc `LIBRARIAN`.
2. Chuẩn bị sinh viên có thẻ `ACTIVE`, không nợ, không quá hạn và chưa đủ giới hạn ba tài liệu.
3. Chuẩn bị ít nhất ba bản sao `AVAILABLE`, một bản sao `RESERVED` thuộc phiếu đã duyệt và hai bản sao `BORROWING` thuộc hai phiếu khác nhau.
4. Để kiểm thử camera, dùng Chrome/Edge trên `localhost` hoặc domain HTTPS và cấp quyền camera. Nếu trình duyệt không hỗ trợ `BarcodeDetector`, ghi `BLOCKED` riêng cho camera; luồng nhập tay/máy quét USB vẫn phải kiểm thử.

## G6-01 - Giao sách từ phiếu trực tuyến

1. Mở `Quầy Mượn - Trả sách` → `Mượn sách` → `Phiếu trực tuyến đã duyệt`.
2. Nhập số phiếu hoặc `HUIT-PM-{id}`.
3. Quét đủ đúng các barcode trong phiếu và hoàn tất.

Kết quả mong đợi: chỉ phiếu `RESERVED` được tải; barcode ngoài phiếu bị từ chối; chỉ khi đủ toàn bộ bản sao nút hoàn tất mới bật; sau giao, phiếu và bản sao chuyển `BORROWING`.

## G6-02 - QR phiếu có chữ ký

1. Từ tài khoản sinh viên lấy QR phiếu mượn còn hạn.
2. Ở quầy bấm `Quét QR`, hướng camera vào mã.
3. Thử lại với ảnh QR bị sửa nội dung hoặc QR đã hết hạn.

Kết quả mong đợi: QR hợp lệ được gửi tới `/api/v1/qr/resolve`, sau đó tải đúng phiếu; QR giả/hết hạn bị Backend từ chối. Frontend không tự tin cậy ticket ID trích từ ảnh.

## G6-03 - Lập phiếu trực tiếp bằng MSSV

1. Chọn `Lập phiếu trực tiếp tại quầy`.
2. Nhập MSSV hoặc quét QR thẻ thư viện.
3. Chọn `Mượn về nhà`, quét từ một đến ba bản sao `AVAILABLE`, nhập tình trạng và xác nhận.

Kết quả mong đợi: hệ thống tạo đúng một phiếu, giao toàn bộ bản sao và chuyển sang `BORROWING`; hạn trả là 10 ngày tính từ lúc giao; thao tác lặp với cùng idempotency key không tạo phiếu thứ hai.

## G6-04 - Đọc tại chỗ

1. Lặp lại G6-03 nhưng chọn `Đọc tại chỗ`.

Kết quả mong đợi: hạn trả là 21:00 cùng ngày; nếu giao sau 21:00, hệ thống dùng hạn an toàn sau một giờ thay vì hạn trong quá khứ.

## G6-05 - Kiểm tra điều kiện mượn

Lần lượt thử bằng tài khoản: thẻ `LOCKED/EXPIRED`, còn công nợ, có tài liệu quá hạn, đã đủ ba tài liệu; sau đó thử barcode không tồn tại, không `AVAILABLE`, trùng barcode và hai bản sao cùng đầu sách.

Kết quả mong đợi: từng trường hợp đều bị từ chối bằng thông báo cụ thể; không tạo phiếu dở dang và không thay đổi trạng thái bản sao.

## G6-06 - Quét barcode bằng camera và phương án dự phòng

1. Bấm `Camera` ở ô barcode, quét Code 128/EAN được in rõ.
2. Từ chối quyền camera rồi thử lại.
3. Dùng máy quét USB hoặc nhập barcode và nhấn Enter.

Kết quả mong đợi: camera nhận mã thì tự kiểm tra và thêm bản sao; khi thiếu quyền/không hỗ trợ có thông báo rõ, giao diện không treo và nhập tay vẫn hoạt động.

## G6-07 - Trả nhiều cuốn khác phiếu

1. Chuyển tab `Trả sách`.
2. Quét hai barcode đang mượn thuộc hai phiếu khác nhau, nhập ghi chú tình trạng và hoàn tất.

Kết quả mong đợi: một giao dịch trả xử lý cả hai; mỗi chi tiết và phiếu liên quan được cập nhật đúng, bản sao về `AVAILABLE` hoặc được chuyển cho hàng đợi theo nghiệp vụ hiện hành.

## G6-08 - Phân quyền

1. Gọi `/loans/manual-checkout`, `/loans/checkout`, `/loans/return` bằng tài khoản sinh viên và khi chưa đăng nhập.
2. Lặp lại bằng `ADMIN/LIBRARIAN`.

Kết quả mong đợi: sinh viên/khách bị chặn; chỉ nhân viên thư viện thực hiện được nghiệp vụ quầy.

## Kết quả

| Ca | PASS/FAIL/BLOCKED | Mã lỗi nếu có | Ghi chú |
|---|---|---|---|
| G6-01 |  |  |  |
| G6-02 |  |  |  |
| G6-03 |  |  |  |
| G6-04 |  |  |  |
| G6-05 |  |  |  |
| G6-06 |  |  |  |
| G6-07 |  |  |  |
| G6-08 |  |  |  |
