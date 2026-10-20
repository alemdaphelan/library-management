# Chính sách mất/hỏng, công nợ và xử lý vi phạm tuần 9

> **Trạng thái ngày 18/10/2026:** Đã chốt nghiệp vụ và triển khai Backend/Web; có migration, phân quyền, unit/component test và integration test PostgreSQL thật.
>
> **Nguồn:** Quy định HUIT và các quyết định nghiệp vụ do người dùng cung cấp trong quá trình rà soát tuần 9. Không triển khai đặt phòng hoặc tiền thế chân.

## 1. Quy tắc đang áp dụng

### 1.1. Quá hạn và gia hạn

- Phạt riêng từng tài liệu, từ ngày dương lịch kế tiếp ngày phải trả.
- Mượn về: 1.000 đồng/tài liệu/ngày; đọc tại chỗ: 5.000 đồng/tài liệu/ngày.
- Báo mất trễ: phí quá hạn dừng ở ngày báo mất; tổng nghĩa vụ gồm phạt quá hạn đã phát sinh, đền bù và phí kỹ thuật.
- Mỗi tài liệu chỉ được gia hạn một lần, thêm 10 ngày từ hạn cũ.
- Chặn gia hạn khi quá hạn, còn nợ, thẻ không hoạt động, đang bị khóa lưu thông hoặc đầu sách có người khác chờ.
- Trả và gia hạn theo từng tài liệu; chỉ thủ thư hoàn tất trả sau khi quét barcode và kiểm tra tình trạng.

### 1.2. Mất và hư hỏng

| Trường hợp | Cách xử lý |
|---|---|
| Hư nhẹ: vấy bẩn, rách hoặc mất 1–10 trang | Thủ thư nhập phí sửa chữa theo biểu phí và bắt buộc ghi lý do |
| Hư nặng: rách bìa, trên 10 trang, rộp ướt, mối mọt | Xử lý như mất tài liệu |
| Còn lưu hành | Mua đúng tài liệu thay thế; thủ thư xác minh và cấp barcode mới |
| Cũ, hết lưu hành, có giá bìa | Việt ngữ 5 lần; ngoại văn 3 lần sau quy đổi tỷ giá có lưu nguồn |
| Không có giá bìa | Ánh xạ biểu phí do Admin cấu hình theo loại và ngôn ngữ |

- Phí kỹ thuật 50.000 đồng/cuốn áp dụng cho mất/hư nặng, kể cả tự mua sách thay thế; chỉ cộng một lần, không nhân 3 hoặc 5.
- Hồ sơ có hạn xử lý 30 ngày từ ngày báo mất.
- Sách thay thế không được đóng hồ sơ chỉ vì đã trả phí kỹ thuật. Thủ thư phải xác nhận nội dung kiểm tra và barcode mới; hệ thống tạo bản sao mới trong kho.

### 1.3. Thu tiền và đối soát

- Thu tiền mặt hoặc chuyển khoản; cho phép thanh toán nhiều đợt.
- Mỗi biên nhận lưu người thu, phương thức và phân bổ cụ thể vào phạt quá hạn, đền bù, sửa chữa hoặc phí kỹ thuật.
- Quyền mượn, gia hạn và đặt giữ chỉ được mở khi không còn nợ đến hạn; nếu đang chịu khóa ba tháng thì còn phải trả hết sách.
- Không hard delete giao dịch đã phát hành. Thủ thư tạo yêu cầu điều chỉnh, Admin duyệt; hệ thống sinh giao dịch ngược và giữ audit trail.

### 1.4. Vi phạm và khóa quyền lưu thông

- Bộ đếm ba lần chỉ tính lỗi quá hạn; một phiếu có nhiều tài liệu quá hạn trong cùng đợt chỉ tính một lần.
- Đủ ba lần trong một năm học: khóa mượn, đặt giữ và gia hạn trong ba tháng lịch.
- Trong thời gian khóa, người dùng vẫn đăng nhập, xem nợ/thông báo và trả hoặc đền bù.
- Hết ba tháng chỉ mở quyền nếu đã trả hết sách và hết nợ; bộ đếm đặt lại theo năm học mới.
- Mang tài liệu ra ngoài khi checkout chưa thành công được lưu ở hồ sơ vi phạm riêng `UNAUTHORIZED_REMOVAL`, không cộng vào bộ đếm quá hạn.

### 1.5. Hàng đợi và thông báo

- Hàng đợi FIFO, không cho một người xếp nhiều lượt cho cùng đầu sách.
- Người đầu hàng được giữ bản sao trong 48 giờ dương lịch từ lúc thông báo; nếu hết hạn vào lúc đóng cửa/ngày nghỉ thì dời đến lần mở cửa kế tiếp.
- Lịch thông báo: trước hạn một ngày, đúng ngày hạn; quá hạn ngày 1, 3, 7 và mỗi bảy ngày tiếp theo; hồ sơ đền bù ngày 20, 25, 29; thông báo khi khóa/mở quyền.

### 1.6. Miễn giảm

- Thủ thư tiếp nhận hồ sơ; Admin ghi nhận quyết định bên ngoài, không tạo role Giám đốc/Ban Giám hiệu trong hệ thống.
- `PENDING_REVIEW` dừng nhắc nợ và đóng băng hồ sơ nhưng vẫn chặn mượn/gia hạn/đặt giữ.
- Lưu người quyết định, số văn bản, ngày, số tiền miễn giảm và lý do; quyết định tạo khoản điều chỉnh thay vì sửa/xóa giao dịch cũ.

## 2. Bằng chứng triển khai

- Liquibase changeset `15-complete-week9-policy.yaml` tạo hồ sơ phạt, biểu phí, vi phạm, phân bổ phiếu thu và các khóa/index cần thiết.
- Backend có `PenaltyCaseService`, `DebtLedgerService`, `OverdueViolationService`, `WaitlistService` và API phân quyền theo Admin/Librarian.
- Web `/admin/penalties` hỗ trợ lập hồ sơ, thu tiền, xem biên nhận phân bổ, tiếp nhận sách thay thế, xin/duyệt miễn giảm, cấu hình biểu phí và duyệt điều chỉnh.
- Portal sinh viên hiển thị công nợ và hồ sơ đền bù kể cả khi quyền mượn bị khóa.
- Mobile/Web gia hạn đúng từng `detailId`, tối đa một lần và thêm 10 ngày.

## 3. Kết quả kiểm thử

- Backend: migration và toàn bộ test đều chạy trên bản mã hiện tại; số cuối cùng được ghi trong bảng tiến độ chính.
- Web: các test tuần 9 tiếp tục qua trong bộ kiểm thử hiện tại 63/63; production build thành công.
- Mobile: `flutter analyze` không có lỗi; 1/1 widget test qua.
- Các ca đã bao phủ: ngày dương lịch, hệ số 5 lần, phí kỹ thuật, tiếp nhận sách thay thế, thanh toán một phần, giao dịch điều chỉnh ngược, khóa ba tháng, FIFO 48 giờ/ngày nghỉ và migration PostgreSQL.

## 4. Ghi chú vận hành

- Admin phải cấu hình biểu phí tài liệu không có giá bìa trước khi thủ thư lập hồ sơ nộp tiền cho loại đó.
- Thủ thư chịu trách nhiệm xác minh thực tế về nhan đề, tác giả, năm xuất bản, loại bìa và giá trị cuốn thay thế; nội dung xác minh được lưu trong hồ sơ.
- Nguồn tỷ giá ngoại văn phải được nhập và lưu cùng hồ sơ để có thể đối soát.
