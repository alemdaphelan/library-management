# KIỂM THỬ GÓI G3 - ĐÁNH GIÁ VÀ TÌM KIẾM NỘI DUNG

## Chuẩn bị

- Khởi động lại Backend để Liquibase áp dụng changeset `24-uat-g3-review-policy-search-content.yaml`.
- Chuẩn bị hai tài khoản sinh viên: một tài khoản chưa từng mượn cuốn đang kiểm thử và một tài khoản đã được thủ thư checkout cuốn đó.
- Không dùng phiếu mới chỉ ở trạng thái `PENDING` hoặc `RESERVED` làm bằng chứng đã mượn.

## G3-01 - Chặn đánh giá khi chưa từng mượn

1. Đăng nhập bằng tài khoản chưa từng được checkout đầu sách.
2. Mở trang chi tiết sách và quan sát khu vực đánh giá.
3. Nếu gọi API trực tiếp, gửi `POST /books/{id}/reviews` với `rating` từ 1 đến 5.

Kết quả mong đợi: giao diện nêu rõ chỉ được đánh giá sau khi thủ thư giao mượn; API trả `403` cùng lý do, không tạo dòng trong bảng `reviews`.

## G3-02 - Gửi đánh giá hợp lệ

1. Dùng tài khoản đang mượn hoặc đã trả đầu sách sau một lần checkout thật.
2. Chọn số sao, nhập nhận xét và gửi.
3. Tải lại trang chi tiết.

Kết quả mong đợi: đánh giá được lưu đúng người dùng, điểm trung bình và số lượt đánh giá cập nhật theo CSDL; tải lại không mất dữ liệu.

## G3-03 - Không nuốt yêu cầu đánh giá lần hai

1. Với tài khoản đã đánh giá ở G3-02, quan sát lại khu vực đánh giá.
2. Gọi lại `POST /books/{id}/reviews` bằng Swagger/Postman để kiểm tra cả lớp Backend.

Kết quả mong đợi: giao diện hiển thị `Bạn đã đánh giá tài liệu này`; API trả `409` với lý do tương ứng; đánh giá cũ không bị ghi đè.

## G3-04 - Xóa đánh giá của chính mình

1. Bấm `Xóa đánh giá của tôi`.
2. Xác nhận số lượt và điểm trung bình được tải lại.

Kết quả mong đợi: chỉ xóa được đánh giá của tài khoản hiện tại; sau khi xóa, tài khoản đã có lịch sử mượn được phép gửi đánh giá mới.

## G3-05 - Tìm kiếm theo nội dung K-NN/TTNT

1. Mở `Tìm kiếm & tra cứu`.
2. Lần lượt tìm `K-NN`, `TTNT`, `machine learning` và `data mining`.
3. Mở một kết quả để đọc phần tóm tắt nội dung.

Kết quả mong đợi: `K-NN` và `TTNT` trả về sách thuộc nhóm `CNTT - Dữ liệu - AI`, kể cả khi từ khóa không nằm trong tên sách. Các ISBN mẫu được bổ sung mô tả khi tồn tại trong dữ liệu nhập là `0201533774`, `1558600655` và `0471253847`.

## Kết quả

| Ca | PASS/FAIL/BLOCKED | Mã lỗi nếu có | Ghi chú |
|---|---|---|---|
| G3-01 |  |  |  |
| G3-02 |  |  |  |
| G3-03 |  |  |  |
| G3-04 |  |  |  |
| G3-05 |  |  |  |
