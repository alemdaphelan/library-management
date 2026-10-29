# UAT H6B - Camera QR/barcode đa trình duyệt

## Mục tiêu

Xác nhận quầy thủ thư quét được QR phiếu mượn/thẻ bạn đọc và barcode bản sao trên trình duyệt có hoặc không có API `BarcodeDetector` gốc. Hệ thống vẫn phải giữ đường lui bằng máy quét USB hoặc nhập mã thủ công.

## Điều kiện

- Chạy Web bằng `localhost` hoặc HTTPS; trình duyệt không cấp camera cho HTTP thường trên máy khác.
- Đăng nhập tài khoản `ADMIN` hoặc `LIBRARIAN`.
- Có một phiếu ở trạng thái `RESERVED` và barcode bản sao tương ứng.
- Cấp quyền camera khi trình duyệt hỏi.

## Ca kiểm thử

1. Mở **Quản lý Mượn - Trả**, chọn **Phiếu trực tuyến đã duyệt**, bấm **Quét QR**.
2. Đưa QR phiếu vào giữa khung. Kỳ vọng: camera tự đóng, phiếu đúng được tải và chỉ chấp nhận phiếu `RESERVED`.
3. Bấm **Camera** tại ô barcode, quét barcode bản sao. Kỳ vọng: camera tự đóng, barcode được kiểm tra với dữ liệu Backend và thêm đúng một lần.
4. Lặp lại trên Chrome/Edge không có `BarcodeDetector` gốc. Kỳ vọng: bộ giải mã ZXing dự phòng vẫn đọc được mã, không còn thông báo “trình duyệt chưa hỗ trợ”.
5. Từ chối quyền camera. Kỳ vọng: hiện lỗi rõ ràng; nhập tay và máy quét USB vẫn hoạt động.
6. Mở camera rồi đóng bằng nút `×` hoặc bấm ra ngoài. Kỳ vọng: đèn camera tắt, luồng camera không tiếp tục chạy nền.
7. Quét QR của phiếu đã giao/đã trả/đã hủy. Kỳ vọng: Backend từ chối và không tạo giao dịch lần hai.

## Lưu ý nghiệm thu

- Camera thật không thể được xác nhận hoàn toàn bằng unit test/jsdom; bắt buộc chạy ca 1-6 trên thiết bị có camera.
- Nếu camera không mở khi dùng IP LAN qua HTTP, triển khai HTTPS hoặc kiểm thử tại `localhost`; đây là yêu cầu bảo mật của trình duyệt, không phải lỗi giải mã.
