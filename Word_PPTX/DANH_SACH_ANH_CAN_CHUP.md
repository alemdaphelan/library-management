# DANH SÁCH ẢNH CẦN CHỤP CHO WORD VÀ POWERPOINT

Ảnh phải được chụp trên đúng commit đã được thành viên kiểm thử nghiệm thu. Dùng dữ liệu demo, không dùng dữ liệu cá nhân thật.

## 1. Ảnh ưu tiên cao

| Mã | Màn hình | Nội dung phải thấy | Vị trí gợi ý |
|---|---|---|---|
| IMG-01 | Trang đăng nhập | Logo/tên hệ thống và form đăng nhập; không hiện mật khẩu | Chương 4 hoặc slide chức năng |
| IMG-02 | Tra cứu và chi tiết sách | Kết quả, bản sao/vị trí, nút đặt giữ và sách tương tự | Chương 4 |
| IMG-03 | Trang chủ sinh viên | Gợi ý cá nhân hóa kèm lý do | Chương 4/slide recommender |
| IMG-04 | Lịch sử/đang mượn | Trạng thái và gia hạn từng tài liệu | Chương 4 |
| IMG-05 | Dashboard | Số liệu thật và sách phổ biến | Chương 4 |
| IMG-06 | Quầy mượn–trả | Phiếu, barcode, duyệt/checkout/return | Chương 4 |
| IMG-07 | QR thủ thư Mobile | Kết quả quét và hành động do server xác định | Chương 4/slide QR |
| IMG-08 | Mất/hỏng và thu phạt | Breakdown phí, trạng thái hồ sơ và thanh toán | Chương 4 |
| IMG-09 | Kiểm kê | Kỳ kiểm kê và kết quả quét barcode | Chương 4 |
| IMG-10 | Tài liệu số | Danh sách/tải ở Portal và quản trị upload | Chương 4 |
| IMG-11 | Chatbot RAG | Câu trả lời, citation và danh sách nguồn | Chương 4/slide RAG |
| IMG-12 | Chatbot từ chối | Câu ngoài phạm vi bị từ chối có kiểm soát | Chương 5 |

## 2. Ảnh minh chứng kiểm thử

| Mã | Nội dung | Nguồn |
|---|---|---|
| TEST-01 | Quality gate cuối Backend/Web/Mobile | Terminal hoặc CI của commit nghiệm thu |
| TEST-02 | Health và smoke | `week11-smoke.ps1` |
| TEST-03 | HTTP E2E chính | Kết quả test/log đã che dữ liệu nhạy cảm |
| TEST-04 | Benchmark | Biểu đồ tạo từ JSON, ưu tiên hơn ảnh terminal |
| TEST-05 | Recommender | Biểu đồ so sánh ba phương pháp từ JSON |
| TEST-06 | RAG | Biểu đồ metric 14 ca từ JSON |

## 3. Quy tắc chụp

- Dùng cùng kích thước cửa sổ và tỷ lệ zoom cho các ảnh Web.
- Cắt phần thừa nhưng giữ tiêu đề trang và dữ liệu chứng minh chức năng.
- Không chụp DevTools chứa token/request header.
- Che email, MSSV, số điện thoại, JWT, API key, password và OTP.
- Không dùng ảnh lỗi console làm ảnh giao diện trong báo cáo.
- Tên file dạng `IMG-06-circulation-checkout.png`.
- Caption hình đánh số theo chương và đặt dưới hình.
- Ghi commit/ngày chụp trong danh sách ảnh nội bộ, không nhất thiết đưa vào caption báo cáo.

## 4. Ảnh không nên dùng

- Màn hình trống hoặc chỉ có dữ liệu mock.
- Chức năng đặt phòng/kiosk đã loại khỏi phạm vi.
- Kết quả Gemini không có nguồn.
- Ảnh của bản cũ trước nghiệm thu.
- Ảnh chứa khóa hoặc tài khoản thật.
