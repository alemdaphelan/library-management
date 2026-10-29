# UAT-F — Đề xuất mua sách và quy định cuối cùng

## Phạm vi

- Phiếu đề xuất lưu đầy đủ: tên, tác giả, ISBN, nhà xuất bản, năm xuất bản, phiên bản, giá dự kiến, đường dẫn tham khảo, thể loại, lý do và mức độ cần thiết.
- Admin/Thủ thư mở chi tiết bằng cách bấm vào từng dòng, duyệt hoặc từ chối; từ chối bắt buộc nhập lý do.
- Khi duyệt, hệ thống tạo đầu sách trong danh mục với 0 bản sao. Sách chỉ có bản sao và có thể mượn sau khi lập phiếu nhập kho.
- Sinh viên nhận kết quả trong ứng dụng và qua email/Firebase Push theo cài đặt đã bật.
- Trang hướng dẫn thống nhất quy tắc 10 ngày, gia hạn một lần 10 ngày, giới hạn 3 tài liệu, mức phạt theo nhãn và không còn nghiệp vụ đặt phòng.

## Chuẩn bị

1. Chạy migration `23-uat-final-proposal-workflow.yaml`.
2. Có một tài khoản sinh viên và một tài khoản Admin/Thủ thư.
3. Bật email hoặc Firebase Push nếu muốn kiểm tra kênh ngoài ứng dụng.

## Ca kiểm thử

| Mã | Thao tác | Kết quả mong đợi | Kết quả | Ghi chú |
|---|---|---|---|---|
| UAT-F01 | Sinh viên gửi đề xuất với toàn bộ trường hợp lệ | Nút hết trạng thái đang gửi, đề xuất xuất hiện đúng một lần trong lịch sử với đầy đủ dữ liệu |  |  |
| UAT-F02 | Gửi năm xuất bản dưới 1000, giá âm hoặc mức ưu tiên ngoài LOW/NORMAL/HIGH bằng API | Backend trả lỗi validation, không tạo đề xuất |  |  |
| UAT-F03 | Mở `/admin/suggestions`, bấm vào một dòng | Ngăn chi tiết hiện đủ dữ liệu đề xuất, người gửi và đường dẫn tham khảo |  |  |
| UAT-F04 | Bấm Từ chối khi chưa nhập lý do | Giao diện không gửi yêu cầu và nhắc phải nhập lý do |  |  |
| UAT-F05 | Nhập lý do rồi từ chối | Trạng thái thành REJECTED; sinh viên nhận thông báo kèm lý do |  |  |
| UAT-F06 | Duyệt một đề xuất có ISBN chưa tồn tại | Trạng thái thành APPROVED, trả về `approvedBookId`; danh mục có đầu sách mới với 0 bản sao |  |  |
| UAT-F07 | Tìm đầu sách vừa duyệt trên Web | Sách có thể được tìm thấy nhưng hiển thị hết bản/chưa có bản sao và chưa thể mượn |  |  |
| UAT-F08 | Lập phiếu nhập cho đầu sách vừa duyệt | Sau khi hoàn tất nhập, bản sao mới xuất hiện trong kho theo quy trình nhập sách |  |  |
| UAT-F09 | Gửi lại thao tác duyệt cho đề xuất đã duyệt | Không tạo đầu sách trùng; kết quả cũ được giữ nguyên |  |  |
| UAT-F10 | Mở trang Hướng dẫn bạn đọc | Không còn đặt phòng, quy tắc 14/21 ngày hoặc mức phạt 2.000đ; nội dung khớp quy định đã chốt |  |  |

## Ngoài phạm vi

- Không áp dụng hạn mức mượn dựa trên tiền thế chân.
- Không gửi SMS; các kênh hỗ trợ là thông báo trong ứng dụng, email và Firebase Push.
- Không triển khai đặt phòng học nhóm.
