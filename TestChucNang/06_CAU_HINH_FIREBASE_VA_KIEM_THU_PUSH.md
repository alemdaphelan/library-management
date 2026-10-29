# BÀN GIAO CẤU HÌNH FIREBASE VÀ KIỂM THỬ PUSH

## 1. Người phụ trách

Hạng mục này được bàn giao cho thành viên **kiểm thử/triển khai**. Thành viên code đã hoàn tất chuỗi FCM trong mã nguồn; người nhận bàn giao chịu trách nhiệm chốt cấu hình ngoài hệ thống, tạo bản build nghiệm thu và lưu bằng chứng trên thiết bị thật.

Package ID đã được nhóm thống nhất dùng khi triển khai là:

```text
vn.edu.huit.library
```

Repository hiện vẫn giữ giá trị Flutter mặc định `com.example.mobile_app`. Người triển khai phải đổi đồng bộ trước khi đăng ký Android App trên Firebase, rồi ghi thay đổi vào commit triển khai. Không đăng ký Firebase với package ID cũ.

## 2. Việc phải thực hiện

1. Đổi `namespace` và `applicationId` trong `mobile-app/android/app/build.gradle.kts` thành `vn.edu.huit.library`.
2. Đổi package của `MainActivity.kt` và đặt file vào đúng cây thư mục `android/app/src/main/kotlin/vn/edu/huit/library/`.
3. Chạy `flutter analyze`, `flutter test` và `flutter build apk --debug`; chỉ tiếp tục khi cả ba đạt.
4. Tạo Firebase Project bằng tài khoản nhóm hoặc tài khoản được nhóm bàn giao lâu dài.
5. Thêm Android App với package ID chính xác `vn.edu.huit.library`.
6. Cấu hình các định danh Firebase client khi chạy/build Mobile theo `Docs/TUAN_12E_HOAN_THIEN_MOBILE_VA_FIREBASE.md`.
7. Tạo service account cho Backend. Đặt JSON bên ngoài repository, ví dụ `.secrets/firebase-service-account.json`, và dùng `GOOGLE_APPLICATION_CREDENTIALS`; không commit hoặc gửi nội dung khóa qua chat.
8. Bật `FIREBASE_PUSH_ENABLED=true`, đặt `FIREBASE_PROJECT_ID`, khởi động lại Backend và kiểm tra health.
9. Cài APK lên điện thoại thật, đăng nhập, cho phép notification và xác nhận token đã đăng ký với Backend.
10. Chạy các ca `NOTI-04` đến `NOTI-07` trong checklist.

Nếu dùng FlutterFire CLI thay cho cấu hình `--dart-define`, phải kiểm tra kỹ file được sinh ra, không làm mất cơ chế fallback khi Firebase chưa được cấu hình và không commit credential server.

## 3. Ba trạng thái bắt buộc

| Trạng thái | Cách đặt ứng dụng | Kết quả cần có |
|---|---|---|
| Foreground | Ứng dụng đang mở trên màn hình | Local notification hiển thị đúng tiêu đề/nội dung |
| Background | Thu nhỏ hoặc chuyển sang ứng dụng khác | Notification xuất hiện trong khay hệ thống |
| Terminated | Vuốt ứng dụng khỏi danh sách gần đây, không dùng Force stop | Notification vẫn xuất hiện; bấm vào mở ứng dụng |

Android có thể chặn notification sau khi người dùng chọn **Force stop** trong Settings. Trường hợp đó không được dùng làm ca terminated thông thường.

## 4. Bằng chứng phải bàn giao

- Commit/nhánh và APK đã dùng để test.
- Model điện thoại, phiên bản Android và thời điểm test.
- Ảnh hoặc video của ba trạng thái; che email, MSSV, token và dữ liệu cá nhân.
- Log Backend xác nhận gửi thành công nhưng không chứa service-account, JWT hay FCM token đầy đủ.
- Kết quả `NOTI-04` đến `NOTI-07` và lỗi phát hiện được ghi theo `03_MAU_BAO_LOI.md`.

Chỉ đánh dấu FCM “nghiệm thu end-to-end” khi đủ bằng chứng trên. Nếu thiếu quyền Firebase, service account hoặc thiết bị, ghi `BLOCKED`, không ghi `FAIL`.

## 5. Ranh giới trách nhiệm

- Thành viên kiểm thử/triển khai: package ID, Firebase Project/App, credential môi trường, build nghiệm thu, test thiết bị và triển khai Internet.
- Thành viên code: chỉ tiếp nhận và sửa lỗi mã nguồn có bước tái hiện rõ ràng; không còn hạng mục chức năng mới bắt buộc theo đề cương.
- Thành viên Word/PPTX: dùng kết quả nghiệm thu đã che thông tin nhạy cảm để hoàn thiện báo cáo và slide.
