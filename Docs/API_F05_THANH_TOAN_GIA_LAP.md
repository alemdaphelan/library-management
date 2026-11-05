# F05 — giả lập nạp cọc và trả công nợ

## Bật khi kiểm thử

Backend phải vừa chạy profile `dev` hoặc `test`, vừa có `PAYMENT_MOCK_ENABLED=true`. Profile `prod`/`production` luôn vô hiệu hóa, kể cả khi có đồng thời profile dev/test và cờ bật. Mặc định tắt; không đổi cấu hình riêng trong `.env` của người dùng khi triển khai F05.

Để bật local, đặt hai biến sau trong môi trường chạy backend (Compose đã chuyển tiếp chúng):

```dotenv
SPRING_PROFILES_ACTIVE=dev
PAYMENT_MOCK_ENABLED=true
```

Build/chạy backend từ source mới và chạy Web từ `web-app/`. F05 không tự restart stack. Không cần thông tin VNPAY để dùng giả lập. Dùng tài khoản/dữ liệu kiểm thử: thành công giả lập ghi vào DB dev/test và thay đổi số dư ở đó, không có giao dịch tiền thật.

`GET /api/v1/online-payments/capabilities` (STUDENT) trả `{"mockEnabled":true/false}`. Web chỉ hiện phần **GIẢ LẬP · CHỈ DÙNG KIỂM THỬ** trong Công nợ & Khoản phạt khi backend cho phép. Nếu cờ tắt hoặc không có profile phù hợp, service/controller ghi tiền giả không được đăng ký; API giả không tồn tại.

## Luồng và hợp đồng

Các API dưới đây chỉ có trong chế độ đã bật, chỉ dành cho STUDENT. Chủ tài khoản lấy từ JWT, không nhận userId người khác trong body.

1. `POST /api/v1/mock-payments/sessions`:

   ```json
   {"purpose":"DEPOSIT","amount":50000,"idempotencyKey":"4ef5536e-e64b-4878-b045-1c991c75ed3f"}
   ```

   purpose là DEPOSIT hoặc DEBT; amount nguyên từ 1.000 đến 100.000.000 VNĐ, trả nợ không vượt công nợ hiện tại; key là UUID. Response `OnlinePaymentSessionDTO`, provider MOCK, status PENDING, reference `MOCK-<64 hex>`, hết hạn sau 15 phút. Tạo phiên chưa ghi tiền. Reference xác định từ chủ tài khoản + key; gửi lại cùng payload trả cùng phiên, thay số tiền/mục đích với cùng key trả 409.

2. `GET /api/v1/mock-payments/sessions/{reference}` lấy lại phiên chính chủ; phiên của người khác/không phải MOCK trả 404. Không ghi tiền khi đọc. Phiên đã quá hạn được trả EXPIRED; lần xử lý tiếp theo lưu trạng thái này.
3. `POST /api/v1/mock-payments/sessions/{reference}/finish`, body chỉ `{"outcome":"SUCCESS"}`, hoặc CANCEL/FAIL. Số tiền/mục đích dùng từ phiên đã lưu. Thành công là COMPLETED + paymentTransactionId; hủy CANCELLED, lỗi FAILED, quá hạn EXPIRED. Kết quả cuối đầu tiên được giữ nguyên; retry hoặc đổi lựa chọn không thể ghi thêm tiền hay chuyển hủy/lỗi thành thành công.

Các request thay đổi của cùng bạn đọc dùng PostgreSQL advisory lock, rồi khóa phiên. Tạo đồng thời, hoàn tất đồng thời và nạp lần đầu chưa có wallet được tuần tự hóa trong chế độ giả lập. Ghi ledger và hoàn tất phiên cùng transaction; nếu công nợ thay đổi/không đủ khoản phân bổ, toàn bộ thao tác ghi tiền rollback, phiên còn PENDING để kiểm tra/hủy. Không tự hạ số tiền, ghi số dư âm hoặc chuyển phần dư sang cọc.

Web giữ key/payload/reference theo tài khoản trong sessionStorage khi đang chờ hoặc chưa rõ kết quả. Reload không tự POST; nút Kiểm tra/tiếp tục dùng GET nếu đã biết reference, hoặc gửi lại đúng payload/key nếu chưa nhận được reference. Timeout/lỗi chưa rõ kết quả giữ phiên; không tự tạo key mới. Cấu hình ẩn hoặc storage tạm thời không khả dụng không tạo request tự động; khi storage bị chặn, bảo vệ retry chỉ còn trong bộ nhớ của lượt mở trang.

## Ghi nhận tiền

- DEPOSIT/SUCCESS gọi phần dùng chung của `recordDepositTopUp`: tăng tiền thế chân; không giảm nợ và không phân bổ phí.
- DEBT/SUCCESS gọi phần dùng chung của `recordPayment`: phân bổ vào khoản nợ, giảm nợ, cập nhật hồ sơ, xét khôi phục quyền theo quy định hiện tại; không tăng cọc. Giữ mốc nhận vật lý/tình trạng sách hỏng đã nhận lại khi tất toán hồ sơ (cả luồng thanh toán hiện có).
- Ledger có paymentMethod MOCK, lý do GIẢ LẬP, mã biên nhận tiền tố MOCK; Web lịch sử ghi “GIẢ LẬP kiểm thử”. Luồng thu tiền thông thường vẫn từ chối phương thức MOCK. Callback VNPAY không được xử lý phiên MOCK dù chữ ký hợp lệ.
- CANCEL/FAIL/EXPIRED không ghi transaction tiền, phân bổ hay thông báo thu tiền. SUCCESS vẫn dùng thông báo/biên nhận qua luồng ledger hiện có; thông báo là dữ liệu dev/test của tài khoản thử.

Dùng lại bảng `online_payment_sessions` và ledger hiện có, không thêm/sửa schema hay backfill dữ liệu cũ. Không áp migration mới hoặc tạo tiền giả trên DB local trong lượt F05. Kết quả chạy test/runtime được ghi tại checkpoint F05 trong [KE_HOACH_FIX_LOI_V2.md](KE_HOACH_FIX_LOI_V2.md).

## Mốc source và kiểm tra

Backend: `MockPaymentService`, `MockPaymentController`, `PaymentCapabilitiesController`, `MockPaymentModels`; ledger thêm entry point nội bộ riêng cho mock, API thu tiền thường không mở thêm phương thức. Profile + cờ được áp lên cả controller/service. Web: `my-penalties/mock-payment.{ts,html,css}` và PenaltyService; VNPAY giữ luồng chuyển cổng hiện có. Chưa có Flutter UI giả lập.

Kiểm tra tập trung: MockPaymentModeTest (cờ/profile, kể cả profile hỗn hợp production), MockPaymentIntegrationTest (HTTP/JWT và PostgreSQL/Redis tạm), DebtLedgerServiceTest, OnlinePaymentServiceTest, OnlinePaymentMigrationIntegrationTest; Web MockPayment/MyPenalties/PenaltyService specs. Không dùng kiểm thử UI fixture để thay thế bằng chứng API/ghi tiền thật trên DB tạm.
