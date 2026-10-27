# UAT-H6 — Thanh toán trực tuyến VNPAY và đồng bộ trạng thái

## Điều kiện chuẩn bị

1. Đăng ký tài khoản VNPAY sandbox và lấy `TmnCode`, `HashSecret` từ cổng sandbox chính thức.
2. Cấu hình các biến `VNPAY_*` theo `.env.example`; không commit giá trị bí mật.
3. Với kiểm thử IPN từ Internet, `VNPAY_RETURN_URL` phải là URL HTTPS công khai trỏ đến Backend. Khi chỉ chạy localhost, có thể kiểm tra luồng trình duyệt qua Return URL nhưng IPN phía máy chủ có thể không gọi tới được.
4. Chuẩn bị một sinh viên có công nợ thật, ví/thẻ thư viện và ít nhất một dòng nợ đang mở.

## H5-01 — Không giả lập khi chưa cấu hình

Để `VNPAY_ENABLED=false`, mở `Công nợ & Khoản phạt`, nhập số tiền và bấm thanh toán.

Kết quả mong đợi: hệ thống hiển thị modal nói rõ cổng thanh toán chưa được cấu hình; không tạo giao dịch thu, không đổi công nợ hay tiền thế chân.

## H5-02 — Thanh toán một phần công nợ

1. Bật cấu hình sandbox, chọn `Thanh toán công nợ` và nhập số tiền nhỏ hơn tổng nợ.
2. Hoàn tất thanh toán thành công trên VNPAY sandbox.
3. Quay về trang công nợ và tải lại.

Kết quả mong đợi: có biên nhận VNPAY; tiền được phân bổ FIFO vào từng khoản nợ; tổng nợ giảm đúng số tiền; callback gọi lặp không sinh giao dịch thứ hai.

## H5-03 — Thanh toán hết công nợ

Thanh toán đúng toàn bộ số còn nợ.

Kết quả mong đợi: công nợ bằng 0. Quyền mượn chỉ được khôi phục nếu thời hạn khóa đã hết và sinh viên đã trả đủ tài liệu; thanh toán không được bỏ qua khóa kỷ luật hay thẻ hết hạn.

## H5-04 — Nạp tiền thế chân

Chọn `Nạp tiền thế chân`, thanh toán 50.000 VNĐ và quay lại trang.

Kết quả mong đợi: tiền thế chân tăng đúng 50.000 VNĐ, có giao dịch `DEPOSIT_TOPUP` và biên nhận VNPAY; công nợ không tự giảm.

## H5-05 — Hủy/thất bại và chống sửa callback

1. Hủy giao dịch trên cổng VNPAY.
2. Thử sửa `vnp_Amount`, `vnp_TxnRef` hoặc `vnp_SecureHash` trên Return URL.

Kết quả mong đợi: giao dịch hủy không ghi nhận khoản thu; callback sai chữ ký/sai số tiền bị từ chối; ví và sổ công nợ giữ nguyên.

## Bằng chứng cần lưu

- Mã commit, cấu hình môi trường đã che bí mật, mã tham chiếu VNPAY.
- Ảnh trước/sau của công nợ, tiền thế chân và lịch sử biên nhận.
- Log callback/IPN đã che chữ ký và mọi thông tin nhạy cảm.
