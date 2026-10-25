# KIỂM THỬ GÓI G8 - HỒ SƠ BẠN ĐỌC VÀ THẺ

## Chuẩn bị

1. Dùng một tài khoản `ADMIN`, một tài khoản `LIBRARIAN` và một tài khoản `STUDENT`.
2. Chọn ít nhất một sinh viên có thẻ thư viện, phiếu mượn và dữ liệu công nợ/phạt để kiểm tra hồ sơ tổng hợp.
3. Chuẩn bị thêm một sinh viên chưa có thẻ hoặc chưa phát sinh giao dịch để kiểm tra trạng thái rỗng.

## G8-01 - Danh sách, tìm kiếm và lọc bạn đọc

Mở `Hồ sơ Bạn đọc & Thẻ`, tìm theo họ tên, MSSV, email hoặc khoa và thử bộ lọc trạng thái thẻ.

Kết quả mong đợi: danh sách lấy dữ liệu thật từ Backend; kết quả tìm kiếm/lọc đúng; trạng thái rỗng và lỗi tải được trình bày rõ, không treo ở trạng thái đang tải.

## G8-02 - Xem hồ sơ tổng hợp

Từ một dòng sinh viên, bấm `Xem hồ sơ`.

Kết quả mong đợi: hiển thị đúng thông tin định danh, khoa, trạng thái tài khoản, thẻ thư viện, ngày lập/hết hạn, tiền thế chân, công nợ, số lần vi phạm, trạng thái khóa, các phiếu mượn gần đây, sổ công nợ và hồ sơ mất/hỏng. Không dùng số liệu mô phỏng.

## G8-03 - Trạng thái khóa và số liệu nghiệp vụ

Mở hồ sơ của sinh viên đang bị khóa hoặc có công nợ/quá hạn.

Kết quả mong đợi: lý do và thời hạn khóa (nếu có) được hiển thị; số phiếu đang mượn/quá hạn và tổng công nợ khớp dữ liệu nghiệp vụ; các danh sách không phát sinh dữ liệu hiển thị trạng thái rỗng hợp lý.

## G8-04 - Mở nghiệp vụ liên quan

Trong hồ sơ, bấm mở một phiếu mượn hoặc chuyển sang trang quản lý khoản phạt.

Kết quả mong đợi: hệ thống điều hướng đến đúng khu vực nghiệp vụ; với phiếu mượn phải tải đúng số phiếu được chọn, không yêu cầu nhập lại thủ công.

## G8-05 - Phân quyền Thủ thư

Đăng nhập bằng `LIBRARIAN`, mở danh sách và hồ sơ chi tiết.

Kết quả mong đợi: Thủ thư được xem hồ sơ tổng hợp để phục vụ tại quầy nhưng không thấy nút thêm, sửa hoặc xóa tài khoản/thẻ.

## G8-06 - Phân quyền Admin và ràng buộc dữ liệu

Đăng nhập bằng `ADMIN`, thử cập nhật trạng thái/thông tin thẻ và thử xóa một sinh viên đã có lịch sử nghiệp vụ.

Kết quả mong đợi: Admin có chức năng quản trị đã được cấp; thay đổi hợp lệ được lưu; thao tác xóa làm mất liên kết lịch sử bị Backend từ chối và giao diện hiện lỗi rõ ràng, không xóa cứng dữ liệu nghiệp vụ.

## G8-07 - Mở đúng hồ sơ từ thông báo

Từ thông báo có loại tham chiếu `USER`, bấm vào thông báo.

Kết quả mong đợi: điều hướng tới `Hồ sơ Bạn đọc & Thẻ`, tự mở đúng sinh viên theo mã tham chiếu và đánh dấu thông báo đã đọc.

## G8-08 - Bảo vệ API hồ sơ

Gọi `GET /api/v1/users/students/{mssv}/profile` khi chưa đăng nhập, bằng sinh viên, bằng Thủ thư và bằng Admin.

Kết quả mong đợi: khách/sinh viên bị từ chối; Admin và Thủ thư đọc được; MSSV không tồn tại hoặc không thuộc tài khoản sinh viên trả lỗi nghiệp vụ rõ ràng.

## Kết quả

| Ca | PASS/FAIL/BLOCKED | Mã lỗi nếu có | Ghi chú |
|---|---|---|---|
| G8-01 |  |  |  |
| G8-02 |  |  |  |
| G8-03 |  |  |  |
| G8-04 |  |  |  |
| G8-05 |  |  |  |
| G8-06 |  |  |  |
| G8-07 |  |  |  |
| G8-08 |  |  |  |
