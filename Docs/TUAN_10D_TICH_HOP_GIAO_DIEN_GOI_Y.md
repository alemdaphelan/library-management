# TUẦN 10D — TÍCH HỢP GIAO DIỆN GỢI Ý SÁCH

Ngày hoàn thành: 19/10/2026.

## 1. Phạm vi

Gói 10D đưa kết quả Hybrid/Cold-start của 10C lên Web Portal và Flutter Mobile. Không thay đổi nhận diện chính của sản phẩm; toàn bộ phần bổ sung tiếp tục sử dụng nền trắng và màu xanh dương HUIT.

## 2. Web Portal

- Loại bỏ hai endpoint `/home/featured-books` và `/home/recommended-books` không tồn tại.
- Người dùng có token gọi `GET /books/recommendations/personalized?limit=10`.
- Khách chưa đăng nhập hoặc lời gọi cá nhân hóa thất bại dùng `GET /books/recommendations/popular?limit=10`.
- Lời gọi popularity không đính kèm JWT cũ để token hết hạn không làm hỏng fallback công khai.
- Mỗi thẻ hiển thị ảnh bìa, tên, tác giả, phần trăm điểm tổng hợp và lý do gợi ý.
- Tiêu đề/mô tả đổi theo `HYBRID_PERSONALIZED`, `HYBRID_COLD_START` hoặc `POPULARITY_BASELINE`.
- Có trạng thái đang tải, rỗng, lỗi và ảnh bìa dự phòng. Chọn sách chuyển tới route chi tiết theo `bookId`.

## 3. Flutter Mobile

- Bổ sung model/parser typed cho response và service gọi API cá nhân hóa.
- Nếu cá nhân hóa không dùng được, Mobile gọi popularity không kèm Authorization.
- Trang chủ thay danh sách catalog ba cuốn bằng top-K thật, điểm phù hợp và lý do.
- Khi chọn một gợi ý, Mobile tải `GET /books/{bookId}` trước khi mở chi tiết để số bản còn, vị trí và nút mượn dùng dữ liệu catalog đầy đủ.
- Lỗi tải gợi ý không làm mất dữ liệu mượn, thông báo và tổng số sách trên trang chủ.

## 4. Kiểm thử

- Web component test xác nhận render kết quả cá nhân hóa, lý do và điểm phù hợp.
- Web service test xác nhận khách dùng popularity và lỗi personalized tự fallback.
- Angular: **66/66 test qua**, production build thành công.
- Mobile parser test xác nhận mapping response và loại item thiếu `bookId`.
- Flutter: `flutter analyze` không có vấn đề, **3/3 test qua**, đóng gói APK debug thành công.

## 5. Phần còn lại

Gói 10E đã hoàn thành audit dữ liệu thật và đối chứng Popularity/Content/Hybrid trên bộ proxy được ghi nhãn; xem `TUAN_10E_DANH_GIA_THUAT_TOAN_GOI_Y.md`. Dữ liệu vận hành thật vẫn thưa nên kết quả proxy không được trình bày như hành vi thực tế.
