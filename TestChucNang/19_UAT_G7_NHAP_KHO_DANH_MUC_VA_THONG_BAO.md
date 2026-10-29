# KIỂM THỬ GÓI G7 - NHẬP KHO, DANH MỤC VÀ THÔNG BÁO QUẢN TRỊ

## Chuẩn bị

1. Dùng một tài khoản `ADMIN` và một tài khoản `LIBRARIAN`.
2. Chuẩn bị một đầu sách đã có ISBN và giá mặc định trong danh mục nhưng chưa có bản sao.
3. Chuẩn bị một sinh viên hợp lệ để gửi yêu cầu mượn và đề xuất mua sách mới.
4. Ghi số bản sao khả dụng của đầu sách trước khi lập phiếu nhập.

## G7-01 - Tải dữ liệu nhập kho

Mở `Quản lý Nhập kho sách`, làm mới trang và quan sát các thẻ thống kê, danh sách phiếu và nhà cung cấp.

Kết quả mong đợi: màn hình kết thúc trạng thái tải; dữ liệu lấy từ Backend hoặc hiện thông báo lỗi rõ ràng, không treo ở “Đang tải”; khi chưa có phiếu phải hiện trạng thái rỗng.

## G7-02 - Tra ISBN và tự điền tên sách

1. Bấm `Tạo Phiếu Nhập`.
2. Nhập ISBN có trong danh mục rồi rời ô hoặc nhấn Enter.
3. Lặp lại bằng ISBN không tồn tại.

Kết quả mong đợi: ISBN hợp lệ tự điền tên sách và giá mặc định nếu có; ISBN không tồn tại hiện hướng dẫn thêm đầu sách trước và không cho lưu phiếu.

## G7-03 - Lập phiếu và tạo bản sao

Chọn nhà cung cấp, nhập một hoặc nhiều dòng ISBN đã xác nhận, số lượng và đơn giá rồi lưu.

Kết quả mong đợi: tạo đúng một phiếu `COMPLETED`; tổng số lượng/tổng tiền đúng; mỗi đơn vị số lượng tạo một barcode `AVAILABLE`; thống kê và danh sách phiếu tự cập nhật.

## G7-04 - Danh mục sách dùng chức năng thật

1. Dùng bộ lọc `Có sẵn` và `Hết sách`.
2. Thêm một đầu sách, nhập mô tả và URL ảnh bìa hợp lệ.
3. Sửa lại mô tả/URL; thử xóa đầu sách có liên kết bản sao hoặc giao dịch.

Kết quả mong đợi: lọc được thực hiện ở Backend trước phân trang; mô tả và ảnh bìa được lưu/hiển thị thật; không còn nút “Tải ảnh/Cột hiển thị” chỉ để trang trí; lỗi xóa liên kết hiện thông báo rõ.

## G7-05 - Phát thông báo nghiệp vụ cho nhân viên

Lần lượt tạo mới: một đề xuất mua sách, một yêu cầu mượn có bản sẵn sàng và một phiếu nhập kho. Đăng nhập lại bằng cả Admin và Thủ thư rồi mở `Thông báo của tôi`.

Kết quả mong đợi: mỗi tài khoản nhân viên nhận thông báo riêng cho ba nghiệp vụ; không phát trùng khi tải lại trang; thông báo cũ vẫn được lưu trong CSDL.

## G7-06 - Mở đúng đối tượng từ thông báo

1. Bấm thông báo đề xuất sách.
2. Bấm thông báo yêu cầu mượn.
3. Bấm thông báo nhập kho.

Kết quả mong đợi: đề xuất mở đúng drawer của đề xuất; yêu cầu mượn tải đúng số phiếu tại quầy; phiếu nhập được tô nổi trong danh sách. Thông báo đồng thời chuyển sang đã đọc.

## G7-07 - Phân quyền

Gọi API tra ISBN, danh sách/ghi phiếu nhập bằng tài khoản sinh viên và khi chưa đăng nhập; sau đó lặp lại bằng Admin/Thủ thư.

Kết quả mong đợi: sinh viên/khách không được quản trị kho; Admin và Thủ thư thao tác được. Thông báo của người nào chỉ người đó đánh dấu đã đọc được.

## Kết quả

| Ca | PASS/FAIL/BLOCKED | Mã lỗi nếu có | Ghi chú |
|---|---|---|---|
| G7-01 |  |  |  |
| G7-02 |  |  |  |
| G7-03 |  |  |  |
| G7-04 |  |  |  |
| G7-05 |  |  |  |
| G7-06 |  |  |  |
| G7-07 |  |  |  |
