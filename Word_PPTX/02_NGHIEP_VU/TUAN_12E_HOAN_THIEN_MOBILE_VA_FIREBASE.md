# TUẦN 12E — MOBILE VÀ FIREBASE (TÓM TẮT CHO WORD/PPT)

## Nội dung đã triển khai

- Mobile cho phép cập nhật họ tên và số điện thoại qua API thật.
- Catalog có danh sách và bộ lọc sách theo khoa/chuyên ngành, dùng cùng taxonomy với mô hình gợi ý tuần 10.
- Dữ liệu mẫu về người dùng, mã số, khoa, email và tiền phạt đã được loại khỏi Home/Profile/Lịch sử mượn; trường thiếu hiển thị “Chưa cập nhật”.
- URL Backend/Web được truyền theo môi trường bằng `--dart-define`.
- Firebase Cloud Messaging có vòng đời token thiết bị, gửi sau transaction commit, vô hiệu token lỗi, hủy khi đăng xuất và hiển thị notification foreground.
- Thông báo PostgreSQL vẫn là nguồn đối soát và fallback khi Firebase bị tắt hoặc lỗi.
- Gate mã nguồn ngày 21/10/2026 đạt Backend 121/121 test và JAR, Web 69/69 test và production build, Mobile analyze sạch, 5/5 test và APK debug.

## Điểm cần mô tả chính xác

Mã nguồn FCM đã hoàn thành. Phần đổi package ID sang `vn.edu.huit.library`, tạo/cấu hình Firebase và nghiệm thu thiết bị đã bàn giao cho thành viên kiểm thử/triển khai theo `TestChucNang/06_CAU_HINH_FIREBASE_VA_KIEM_THU_PUSH.md`. Chỉ được ghi “đã nghiệm thu end-to-end” sau khi có bằng chứng foreground, background và terminated. Không đưa service-account, API key, JWT hoặc FCM token vào báo cáo/slide.

Kiến trúc: `NotificationService` ghi thông báo idempotent → transaction commit → Firebase Admin → token thiết bị của người dùng → hệ điều hành/Mobile; nếu FCM không sẵn sàng, danh sách thông báo trong app vẫn sử dụng được.

Chi tiết cấu hình, API và giới hạn nằm ở [`Docs/TUAN_12E_HOAN_THIEN_MOBILE_VA_FIREBASE.md`](../../Docs/TUAN_12E_HOAN_THIEN_MOBILE_VA_FIREBASE.md).
