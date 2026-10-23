# UAT-E — Trang chủ thật và chống spam giữ chỗ

## Phạm vi

- Trang chủ chỉ giữ các khối có chức năng: tìm kiếm, liên kết đến nghiệp vụ đã triển khai và gợi ý sách từ Backend.
- Đã bỏ các dữ liệu viết cứng không có nguồn quản trị: số ghế trống, tin tức/sự kiện, thông báo thư viện, danh sách sách theo khoa, tiện ích phòng nhóm và mô tả cơ sở vật chất.
- Đã bỏ nút `VN | EN` vì hệ thống chưa có bản dịch; các liên kết `#` trong header/footer được thay bằng route thật và liên kết đặt phòng được loại bỏ.
- Mỗi lần một lượt đặt trực tiếp hoặc lượt hàng đợi đã được thông báo nhưng hết hạn nhận sẽ được tính là một lần không đến nhận. Đủ ba lần trong cùng năm học sẽ khóa mượn, đặt giữ và gia hạn trong ba tháng.

## Ca kiểm thử

| Mã | Thao tác | Kết quả mong đợi | Kết quả | Ghi chú |
|---|---|---|---|---|
| UAT-E01 | Mở trang chủ | Không còn số ghế `482/1.200`, tin/sự kiện giả, sách mẫu theo khoa hoặc mục Đặt phòng nhóm |  |  |
| UAT-E02 | Nhập `K-NN` vào ô tìm kiếm trang chủ và Enter | Chuyển sang trang tìm kiếm, giữ từ khóa và hiển thị kết quả tìm theo nội dung |  |  |
| UAT-E03 | Bấm lần lượt Gia hạn sách, Giỏ mượn, Tài liệu số, Đề xuất mua sách, Hướng dẫn bạn đọc | Mỗi thẻ điều hướng đến đúng chức năng, không có thẻ chỉ để trang trí |  |  |
| UAT-E03b | Kiểm tra header và footer | Không còn nút `VN/EN`, liên kết `#` hoặc liên kết đặt phòng; các liên kết còn lại mở đúng route |  |  |
| UAT-E04 | Đăng nhập và tải trang chủ | Danh sách gợi ý lấy từ API cá nhân hóa; người chưa đăng nhập nhận danh sách phổ biến |  |  |
| UAT-E05 | Để một phiếu đặt trực tiếp hết hạn nhận | Phiếu bị hủy, bản sao được giải phóng và bộ đếm không đến nhận tăng một |  |  |
| UAT-E06 | Để một lượt `NOTIFIED` trong hàng đợi hết 48 giờ | Lượt chuyển `EXPIRED`, sách chuyển cho người tiếp theo và bộ đếm tăng một |  |  |
| UAT-E07 | Tạo đủ ba lần không đến nhận trong cùng năm học | Tài khoản bị khóa mượn/đặt giữ/gia hạn ba tháng và nhận thông báo nêu rõ lý do |  |  |
| UAT-E08 | Thử tạo phiếu hoặc vào hàng đợi trong thời gian khóa | Backend từ chối với thông báo thời hạn khóa; thao tác trả sách và xem công nợ vẫn dùng được |  |  |
| UAT-E09 | Kiểm tra lần vi phạm đầu tiên của năm học mới | Bộ đếm bắt đầu lại từ một, không cộng số lần của năm học trước |  |  |

## Cấu hình mặc định

- `library.circulation.reservation-no-show-threshold=3`
- `library.circulation.reservation-no-show-lock-months=3`
- Năm học bắt đầu từ ngày 01/08 theo cấu hình tuần 9 hiện có.
