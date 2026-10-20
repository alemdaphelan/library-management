# HƯỚNG DẪN DỰNG MÔI TRƯỜNG KIỂM THỬ

## 1. Điều kiện trước khi chạy

- Git.
- Docker Desktop và Docker Compose.
- Node.js/npm phù hợp với `web-app/package.json`.
- Flutter SDK phù hợp với `mobile-app/pubspec.yaml` nếu kiểm thử Mobile.
- Trình duyệt Chrome hoặc Edge bản hiện hành.
- Android Emulator hoặc thiết bị Android nếu kiểm thử Mobile.
- File `.env` cục bộ được tạo từ `.env.example`.
- Bộ tài khoản demo được thành viên code bàn giao sau khi hoàn tất 12D.

Không sử dụng API key hoặc mật khẩu production. Không gửi file `.env` cho người khác.

## 2. Ghi nhận phiên bản bàn giao

Chạy tại thư mục gốc:

```powershell
git branch --show-current
git rev-parse HEAD
git status --short
docker version
docker compose version
node --version
npm --version
flutter --version
```

Ghi kết quả cần thiết vào `04_BIEN_BAN_NGHIEM_THU.md`. Repository dùng để nghiệm thu phải không có sửa đổi ngoài file kết quả/ảnh kiểm thử của người kiểm thử.

## 3. Chuẩn bị biến môi trường

```powershell
Copy-Item .env.example .env
```

Mở `.env` và thay các giá trị minh họa. Yêu cầu tối thiểu:

- `POSTGRES_PASSWORD` không giữ giá trị mẫu.
- `JWT_SECRET` dài tối thiểu 32 ký tự.
- `GEMINI_API_KEY` có thể để trống để test retrieval/fallback cục bộ; chỉ điền key riêng của môi trường test nếu cần test Gemini trực tuyến.
- FCM có thể để `FIREBASE_PUSH_ENABLED=false` trong các ca không kiểm tra push. Để kiểm tra push thật, người triển khai phải cấp Firebase project test và service account qua secret, tuyệt đối không commit file JSON.
- Không commit `.env`.

## 4. Khởi động Backend và hạ tầng

```powershell
docker compose up -d --build
docker compose ps
```

Kiểm tra smoke:

```powershell
.\scripts\week11-smoke.ps1
```

Địa chỉ:

| Thành phần | Địa chỉ |
|---|---|
| Backend | `http://localhost:8080` |
| Health | `http://localhost:8080/actuator/health` |
| Swagger | `http://localhost:8080/swagger-ui/index.html` |
| Mailpit | `http://localhost:8025` |
| Nginx gateway | `http://localhost` |

MongoDB, Kafka và Elasticsearch có container nhưng không phải đường dữ liệu chính để nghiệm thu catalog/circulation/recommender.

## 5. Khởi động Web

Mở terminal mới:

```powershell
Set-Location web-app
npm ci
npm start
```

Truy cập `http://localhost:4200`. Web phát triển gọi Backend tại `http://127.0.0.1:8080`.

## 6. Khởi động Mobile

```powershell
Set-Location mobile-app
flutter pub get
flutter devices
flutter run
```

- Android Emulator gọi Backend qua `http://10.0.2.2:8080`.
- Flutter Web và desktop gọi `http://localhost:8080`.
- Thiết bị Android thật không dùng được `localhost` của máy tính; cần cấu hình địa chỉ LAN phù hợp trước khi nghiệm thu thiết bị thật.
- Trước khi cấu hình Firebase, thực hiện đầy đủ `06_CAU_HINH_FIREBASE_VA_KIEM_THU_PUSH.md` và đổi package ID sang `vn.edu.huit.library`.
- Với thiết bị thật, truyền `API_BASE_URL`, `WEB_PORTAL_URL` và các định danh client `FIREBASE_*` bằng `--dart-define`; xem `../Docs/TUAN_12E_HOAN_THIEN_MOBILE_VA_FIREBASE.md`.
- Backend test push đặt `FIREBASE_PUSH_ENABLED=true`, `FIREBASE_PROJECT_ID` và `GOOGLE_APPLICATION_CREDENTIALS` trỏ đến service-account JSON nằm ngoài repository.

## 7. Kiểm tra nhanh trước khi test

- Health trả `UP`.
- Catalog trả danh sách sách.
- Trang đăng nhập Web mở được.
- Mailpit mở được.
- Đăng nhập được ít nhất ba role Student, Librarian và Admin bằng tài khoản demo đã bàn giao.
- Không có lỗi migration trong log Backend.
- RAG status/reindex chỉ chạy bằng Admin.

Tài khoản demo cục bộ đã xác minh:

| Role | Định danh | Mật khẩu |
|---|---|---|
| Admin | `admin@huit.edu.vn` | `admin123` |
| Librarian | `librarian@huit.edu.vn` | `admin123` |
| Student | `student@student.huit.edu.vn` hoặc `20012001` | `admin123` |

Không dùng các tài khoản này ở môi trường Internet. Xem thêm `05_THONG_TIN_BAN_GIAO_12D.md`.

Nếu một điều kiện chưa đạt, dừng test chức năng và báo lỗi môi trường với log liên quan.

## 8. Dừng hệ thống

```powershell
docker compose down
```

Không thêm tùy chọn xóa volume nếu chưa được chủ repository cho phép, vì việc đó xóa dữ liệu kiểm thử cục bộ.

## 9. Thu thập log an toàn

```powershell
docker compose logs --since 15m backend
docker compose ps
```

Trước khi đính kèm, kiểm tra log không chứa JWT, password, API key, OTP, email hoặc mã sinh viên thật.

## 10. Lệnh chạy quality gate

Backend:

```powershell
Set-Location backend
.\mvnw.cmd test
```

Web:

```powershell
Set-Location web-app
npm ci
npm test -- --watch=false
npm run build
```

Mobile:

```powershell
Set-Location mobile-app
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
```

Benchmark và vận hành:

```powershell
node .\scripts\week11-load-test.mjs --requests 200 --concurrency 20
.\scripts\backup-postgres.ps1 -OutputPath .\backups\huit_library.dump
.\scripts\verify-backup-restore.ps1 -BackupPath .\backups\huit_library.dump
```

Chỉ chạy lệnh backup/restore ở thư mục gốc. Script restore dùng database tạm; không thay tên database đích thành database chính.
