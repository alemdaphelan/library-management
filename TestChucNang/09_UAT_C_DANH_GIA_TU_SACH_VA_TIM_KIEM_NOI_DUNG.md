# KIỂM THỬ LẠI GÓI UAT-C

Gói UAT-C thay các số liệu sách giả bằng dữ liệu thật, hoàn thiện đánh giá, tủ sách cá nhân và tìm kiếm theo nội dung/chủ đề.

## C01 - Lượt xem được ghi nhận thật

1. Chọn một đầu sách và ghi lại `viewCount` từ API `GET /books/{id}`.
2. Mở lại trang chi tiết hai lần.
3. Gọi lại API hoặc quan sát chỉ số trên trang.

Kết quả mong đợi: mỗi lần tải chi tiết hợp lệ tăng một lượt; tải danh sách tìm kiếm không tự tăng lượt xem.

## C02 - Lượt mượn trong năm lấy từ phiếu thật

1. Chọn đầu sách chưa có lượt checkout hợp lệ trong năm hiện tại.
2. Tạo phiếu nhưng chưa checkout và kiểm tra `borrowCount`.
3. Thủ thư checkout, sau đó tải lại trang chi tiết.

Kết quả mong đợi: phiếu `PENDING/RESERVED` không được tính; sau checkout chỉ số tăng theo dữ liệu `loan_details`, không tạo số ngẫu nhiên.

## C03 - Đánh giá sách

1. Khi chưa đăng nhập, xem danh sách đánh giá và thử gửi đánh giá.
2. Đăng nhập, chọn từ 1 đến 5 sao, nhập nhận xét rồi gửi.
3. Sửa lại số sao/nhận xét và gửi lần nữa.
4. Xóa đánh giá của chính mình.

Kết quả mong đợi: danh sách xem được công khai; gửi yêu cầu đăng nhập; mỗi tài khoản chỉ có một đánh giá trên một đầu sách và lần gửi sau cập nhật dòng cũ; điểm trung bình/số lượt thay đổi đúng; người dùng chỉ xóa được đánh giá của mình.

## C04 - Lưu sách thật và tủ sách cá nhân

1. Đăng nhập và bấm `Lưu tủ sách` trên trang chi tiết.
2. Tải lại trang; kiểm tra nút vẫn ở trạng thái `Đã lưu`.
3. Mở avatar trên thanh điều hướng, chọn `Tủ sách cá nhân`.
4. Bỏ lưu từ trang tủ sách và tải lại.

Kết quả mong đợi: sách được lưu trong CSDL, không tạo trùng khi bấm nhiều lần; trang tủ sách hiển thị đúng tên, tác giả, ảnh và số bản sẵn sàng; bỏ lưu cập nhật ngay.

## C05 - Tủ sách và thao tác lưu bắt buộc đăng nhập

1. Đăng xuất rồi truy cập trực tiếp `/saved-books`.
2. Đăng xuất rồi bấm lưu tại trang chi tiết.
3. Gọi `GET/POST/DELETE /users/wishlist` không có JWT.

Kết quả mong đợi: giao diện chuyển hoặc yêu cầu đăng nhập; API trả `401/403`; không có dữ liệu người dùng giả được tạo.

## C06 - Tìm kiếm theo nội dung/chủ đề

Thử các truy vấn không nhất thiết nằm nguyên văn trong tên sách, chẳng hạn `K-NN`, `machine learning`, `trí tuệ nhân tạo`, `chuỗi cung ứng` và `an toàn thực phẩm`.

Kết quả mong đợi: kết quả có thể khớp tên, tác giả, ISBN, nhà xuất bản, mô tả, thể loại hoặc taxonomy chủ đề tuần 10. Ví dụ `K-NN` phải ánh xạ được tới nhóm `CNTT - Dữ liệu - AI` thay vì chỉ tìm chuỗi trong tên sách.

## C07 - Không còn số liệu ngẫu nhiên trên trang chi tiết

1. Tải cùng một đầu sách ở hai trình duyệt.
2. So sánh điểm đánh giá, số đánh giá, lượt mượn và lượt xem với API/CSDL.

Kết quả mong đợi: các số liệu khớp dữ liệu thật; chỉ lượt xem thay đổi do mở trang; không còn công thức dựa trên mã sách.

## Kết quả

| Ca | PASS/FAIL/BLOCKED | Mã lỗi nếu có | Ghi chú |
|---|---|---|---|
| C01 |  |  |  |
| C02 |  |  |  |
| C03 |  |  |  |
| C04 |  |  |  |
| C05 |  |  |  |
| C06 |  |  |  |
| C07 |  |  |  |
