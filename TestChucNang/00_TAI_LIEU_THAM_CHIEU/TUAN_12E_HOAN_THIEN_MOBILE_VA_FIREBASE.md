# THAM CHIẾU KIỂM THỬ 12E — MOBILE VÀ FIREBASE

Bản triển khai 12E bổ sung cập nhật hồ sơ Mobile, tìm kiếm theo chuyên ngành, loại dữ liệu hồ sơ cố định và Firebase Cloud Messaging.

Gate mã nguồn ngày 21/10/2026 đạt Backend 121/121 test và JAR, Web 69/69 test và production build, Mobile analyze sạch, 5/5 test và APK debug.

Người kiểm thử/triển khai nhận phần đổi package ID sang `vn.edu.huit.library`, tạo/cấu hình Firebase và kiểm thử thiết bị theo `../06_CAU_HINH_FIREBASE_VA_KIEM_THU_PUSH.md`. Sau đó thực hiện các ca `NOTI-04` đến `NOTI-07` và `MOB-08` đến `MOB-10` trong checklist. FCM chỉ được đánh dấu đạt khi đã nhận push trên thiết bị thật ở foreground, background và terminated; trạng thái code/build không thay thế bằng chứng này.
