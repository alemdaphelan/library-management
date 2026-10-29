# TUẦN 12E — HOÀN THIỆN MOBILE VÀ FIREBASE

## 1. Phạm vi bổ sung

Gói này xử lý các điểm còn thiếu so với đề cương trên Mobile mà không thay đổi quyết định công nghệ Backend:

- cập nhật họ tên và số điện thoại từ Mobile qua API hồ sơ thật;
- tìm kiếm sách theo khoa/chuyên ngành dựa trên taxonomy tuần 10;
- loại dữ liệu mẫu về MSSV, mã cán bộ, khoa, email và tiền phạt khỏi giao diện Mobile;
- hỗ trợ cấu hình URL Backend/Web bằng `--dart-define`;
- tích hợp Firebase Cloud Messaging từ Backend đến thiết bị Mobile;
- vẫn giữ thông báo trong PostgreSQL làm nguồn đối soát khi FCM bị tắt hoặc gửi lỗi.

## 2. Luồng Firebase Cloud Messaging

1. Mobile khởi tạo Firebase từ các biến `FIREBASE_*` được truyền lúc build.
2. Sau đăng nhập, Mobile xin quyền nhận thông báo, đăng ký FCM installation/token và gửi về `POST /api/v1/notifications/devices`.
3. Backend lưu token theo người dùng trong `push_device_registrations`.
4. Khi `NotificationService.createOnce` tạo thông báo mới thành công, sự kiện push chỉ được xử lý sau khi transaction commit.
5. Firebase Admin gửi tiêu đề, nội dung và metadata tham chiếu đến mọi thiết bị đang hoạt động của người dùng.
6. Token không hợp lệ hoặc đã hủy đăng ký được vô hiệu hóa; đăng xuất Mobile gọi `DELETE /api/v1/notifications/devices`.
7. Thông báo foreground được hiển thị bằng local notification channel; thông báo nền/terminated do FCM và hệ điều hành xử lý.

## 3. Cấu hình an toàn

Backend mặc định để `FIREBASE_PUSH_ENABLED=false`, vì vậy không có service account thì hệ thống vẫn khởi động và thông báo trong app vẫn hoạt động. Khi kiểm thử FCM thật:

```text
FIREBASE_PUSH_ENABLED=true
FIREBASE_PROJECT_ID=<firebase-project-id>
GOOGLE_APPLICATION_CREDENTIALS=<duong-dan-tuyet-doi-den-service-account.json>
```

Docker có thể nhận `FIREBASE_SERVICE_ACCOUNT_JSON_BASE64` từ secret manager thay cho file. Không ghi service-account JSON vào `.env` được commit và không đưa file này lên Git.

Mobile nhận cấu hình lúc chạy/build:

```powershell
flutter run `
  --dart-define=API_BASE_URL=https://api.example.com `
  --dart-define=WEB_PORTAL_URL=https://library.example.com `
  --dart-define=FIREBASE_API_KEY=<client-api-key> `
  --dart-define=FIREBASE_APP_ID=<firebase-app-id> `
  --dart-define=FIREBASE_MESSAGING_SENDER_ID=<sender-id> `
  --dart-define=FIREBASE_PROJECT_ID=<project-id> `
  --dart-define=FIREBASE_STORAGE_BUCKET=<bucket>
```

Các định danh Firebase phía client không thay thế service-account phía Backend. Khóa Gemini trước đây cũng không phải service-account FCM.

## 4. API và dữ liệu mới

- `POST /api/v1/notifications/devices`: đăng ký hoặc gán lại thiết bị cho người đang đăng nhập.
- `DELETE /api/v1/notifications/devices`: vô hiệu hóa token khi đăng xuất.
- `GET /books/departments`: danh sách khoa/chuyên ngành đã cấu hình.
- `GET /books?...&department=<key>`: lọc sách theo các chủ đề tương thích của chuyên ngành, có thể kết hợp từ khóa và thể loại.
- `PUT /api/v1/auth/me`: Mobile cập nhật họ tên và số điện thoại, có validation hai phía.

Migration `19-add-firebase-push-devices.yaml` tạo bảng token thiết bị, unique token và index theo người dùng/trạng thái.

## 5. Trạng thái và giới hạn nghiệm thu

- Mã nguồn Firebase/FCM, API token thiết bị, giao diện cập nhật hồ sơ, bộ lọc chuyên ngành và loại dữ liệu giả: **đã triển khai**.
- Chế độ không có Firebase config: **đã thiết kế fallback**, không chặn build/test hoặc thông báo trong app.
- Gate mã nguồn ngày 21/10/2026: **Backend 121/121 test và JAR; Web 69/69 test và production build; Mobile analyze sạch, 5/5 test và APK debug build thành công**.
- Gửi push end-to-end trên điện thoại thật: **đã bàn giao cho thành viên kiểm thử/triển khai**. Người nhận đổi package ID từ `com.example.mobile_app` sang package đã chốt `vn.edu.huit.library`, tạo Firebase project/app, cấu hình service account và lưu bằng chứng notification foreground/background/terminated theo `TestChucNang/06_CAU_HINH_FIREBASE_VA_KIEM_THU_PUSH.md`.
- iOS cần bật Push Notifications và Background Modes trong signing/capabilities của Apple khi đóng gói; Android cần đăng ký đúng application ID với Firebase.

Ba tài liệu học tập `Bai08_NotificationVaFile.pdf`, `Bai11_Flutter_Firebase.pdf` và `Hướng dẫn kết nối FireBase.pptx` được dùng để đối chiếu quy trình. Cấu hình production vẫn phải tuân theo project Firebase thật và không dùng dữ liệu minh họa trong tài liệu.
