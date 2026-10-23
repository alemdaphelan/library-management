# KIỂM THỬ LẠI GÓI UAT-B

Gói UAT-B xử lý nhóm lỗi mượn/trả: tạo một phiếu có nhiều tài liệu, giới hạn tối đa ba tài liệu đang hoạt động, giữ bản sao đến giờ đóng cửa ngày nhận và trả nhiều cuốn thuộc các phiếu khác nhau trong một giao dịch.

## Chuẩn bị dữ liệu

- Một tài khoản sinh viên đang hoạt động, không nợ và không có sách quá hạn.
- Ít nhất bốn đầu sách khác nhau, mỗi đầu sách có một bản sao `AVAILABLE`.
- Một tài khoản Librarian hoặc Admin để thao tác tại quầy.
- Ghi lại mã commit, tài khoản thử nghiệm, barcode và dữ liệu đã tạo trong biên bản.

## B01 - Giỏ mượn chỉ nhận tối đa ba đầu sách khác nhau

1. Đăng nhập bằng tài khoản sinh viên.
2. Mở chi tiết sách A và chọn `Thêm vào giỏ mượn` hai lần.
3. Thêm tiếp sách B và C.
4. Thử thêm sách D.

Kết quả mong đợi: sách A không bị thêm trùng; giỏ có đúng A, B, C; sách D bị từ chối bằng thông báo đã đạt giới hạn ba tài liệu.

## B02 - Tạo một phiếu cho nhiều tài liệu

1. Mở `Giỏ mượn` từ thanh điều hướng.
2. Chọn hình thức `Mượn về`, ngày nhận hợp lệ và tạo phiếu.
3. Mở lịch sử mượn hoặc màn hình quản lý phiếu của thủ thư.

Kết quả mong đợi: chỉ sinh một mã phiếu, phiếu chứa toàn bộ ba barcode; các bản sao chuyển sang `RESERVED`; hạn mượn dự kiến là 10 ngày và sẽ được tính lại khi thủ thư checkout.

## B03 - Chặn khi tổng số tài liệu đang hoạt động vượt ba

1. Chuẩn bị tài khoản đang có hai tài liệu ở một trong các trạng thái `PENDING`, `RESERVED`, `BORROWING` hoặc `OVERDUE`.
2. Thêm hai đầu sách khác vào giỏ và tạo phiếu.

Kết quả mong đợi: Backend từ chối toàn bộ yêu cầu; giao diện nêu rõ người dùng chỉ được đặt giữ hoặc mượn tối đa ba tài liệu; không khóa dở bất kỳ bản sao nào trong giỏ.

## B04 - Không tạo phiếu dở khi một sách hết bản sẵn sàng

1. Thêm hai đầu sách vào giỏ, trong đó một đầu sách không còn bản `AVAILABLE`.
2. Tạo phiếu.

Kết quả mong đợi: không tạo phiếu và không giữ cuốn còn lại; thông báo liệt kê đầu sách hết bản và hướng dẫn bỏ khỏi giỏ để đăng ký hàng đợi riêng.

## B05 - Giữ sách đến giờ đóng cửa của ngày nhận

1. Tạo phiếu trực tiếp cho một ngày thư viện mở cửa.
2. Kiểm tra `locked_until`/hạn nhận hiển thị trên kết quả.
3. Thử chọn ngày đã qua, ngày nghỉ hoặc thao tác sau giờ đóng cửa của chính ngày nhận.

Kết quả mong đợi: hạn nhận là giờ đóng cửa của ngày được chọn; ngày không hợp lệ bị từ chối. Quy tắc 48 giờ chỉ áp dụng cho lượt được gọi từ hàng đợi, không áp dụng cho giỏ mượn trực tiếp.

## B06 - Checkout cả phiếu nhiều tài liệu

1. Thủ thư duyệt phiếu nhiều tài liệu.
2. Quét đủ các barcode thuộc phiếu rồi hoàn tất checkout.
3. Thử checkout thiếu hoặc quét barcode ngoài phiếu.

Kết quả mong đợi: quét đủ thì tất cả chi tiết và bản sao chuyển sang `BORROWING`; quét thiếu hoặc sai bị từ chối và không cập nhật nửa chừng.

## B07 - Trả trước một tài liệu trong phiếu nhiều cuốn

1. Với phiếu đang mượn A và B, chỉ quét trả B.
2. Kiểm tra trạng thái phiếu, A, B và bản sao B.

Kết quả mong đợi: B chuyển `RETURNED`, bản sao B sẵn sàng hoặc được chuyển cho hàng đợi; A vẫn `BORROWING`; phiếu chưa chuyển `RETURNED` cho đến khi mọi chi tiết đã trả/hủy.

## B08 - Trả một lần các cuốn thuộc nhiều phiếu

1. Chuẩn bị phiếu #1 đang mượn A và phiếu #2 đang mượn C.
2. Ở quầy trả sách, quét barcode A rồi C.
3. Kiểm tra giao diện có hiển thị đúng mã phiếu cạnh từng cuốn và hoàn tất giao dịch.

Kết quả mong đợi: một thao tác trả nhận cả hai barcode; mỗi chi tiết được cập nhật trong đúng phiếu; phiếu nào đã trả đủ thì chuyển `RETURNED`, phiếu còn tài liệu chưa trả vẫn giữ trạng thái đang mượn.

## B09 - Hồi quy Mobile và mượn một cuốn

1. Tạo yêu cầu mượn một cuốn từ Mobile hoặc nút `Mượn ngay / Vào hàng đợi` trên Web.
2. Duyệt, checkout, gia hạn và trả như luồng cũ.

Kết quả mong đợi: API mượn một cuốn vẫn hoạt động; Mobile không bắt buộc dùng giỏ; không phát sinh thay đổi màu chủ đạo xanh dương - trắng.

## Kết quả

| Ca | PASS/FAIL/BLOCKED | Mã lỗi nếu có | Ghi chú |
|---|---|---|---|
| B01 |  |  |  |
| B02 |  |  |  |
| B03 |  |  |  |
| B04 |  |  |  |
| B05 |  |  |  |
| B06 |  |  |  |
| B07 |  |  |  |
| B08 |  |  |  |
| B09 |  |  |  |
