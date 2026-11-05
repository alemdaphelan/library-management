# Hợp đồng và kiểm tra lỗi Mobile trong 5.docx

Baseline: `main/b88ac60` + thay đổi local đang giữ. Kế hoạch/checkpoint: [KE_HOACH_FIX_MOBILE_20261105.md](KE_HOACH_FIX_MOBILE_20261105.md).

## Số dư, giá và nhận trả

- `GET /api/v1/transactions/my-ledger` tiếp tục trả **List giao dịch**. Không thêm object wallet để tránh phá Web. Mobile lấy `depositBalance`/`totalDebt` từ `GET /api/v1/library-card`, không tính lại số dư bằng cách cộng danh sách giao dịch. Lỗi tải hiển thị lỗi; thiếu số tiền hiển thị chưa có dữ liệu.
- `LoanItemDTO` bổ sung `bookValue`, `bookValueSource`, `bookValueCurrency`, `priceKnown`, `returnable`. Dùng chung trong `/loans/my-loans`, chi tiết phiếu và QR. Giá là **giá trị hiện tại**, do `BookValuationService` xác định; không phải số tiền đã thu hoặc chứng từ giá lịch sử.
- Điều kiện trả: item BORROWING/OVERDUE, chưa có `returnDate` hoặc `physicalReturnAt`. DAMAGED/LOST/SETTLED không mở nhận trả lần nữa. `PAID` của hồ sơ không tự chứng minh đã nhận sách. Báo mất của bạn đọc chưa được xử lý vẫn cho trả nếu tìm lại được sách.
- Mobile retry trong cùng lần quét dùng lại idempotency key và payload đã gửi, timeout 20 giây. Quét lại đọc trạng thái mới; khóa server và idempotency hiện có vẫn là chốt chống trừ cọc hai lần. Chưa lưu thao tác pending qua lúc tắt ứng dụng.
- Chi tiết sách dùng `/loans/my-eligibility?bookId=...` hiện giá, cọc và hạn mức. Không coi lỗi/thiếu giá là giá 0.

## Đề xuất theo ngành

- `GET /proposals/majors`: yêu cầu đăng nhập, trả List `{majorId, majorName, majorCode, facultyId, sourceReference}` từ `academic_majors`.
- `POST /proposals`: thêm `majorId` tùy chọn để tương thích client cũ. Nếu gửi mã, backend xác thực với danh mục chính thức và lưu `majorId`, snapshot `majorName` và `majorSourceReference`; mã không hợp lệ trả 400. Client không được tự gửi tên/nguồn để thay danh mục.
- Mobile mới bắt buộc chọn ngành trước khi gửi; bỏ `genre` và `urgencyLevel` khỏi request/form. Backend vẫn đọc trường cũ và mặc định urgency NORMAL để tương thích Web/client trước đây.
- `GET /proposals/my`, `/proposals/admin` và phản hồi tạo/duyệt chứa snapshot ngành. Lịch sử cũ giữ nguyên, không biến thể loại tình cảm/hành động thành ngành học.
- Thủ thư và Admin duyệt tại Web `/admin/suggestions`, thẻ **Từng đề xuất**. Bộ lọc ngành lấy các mã ngành thực sự có trong danh sách, kết hợp trạng thái và từ khóa; có lựa chọn đề xuất cũ chưa gán ngành. Không lọc theo department của người gửi. Tab tổng hợp B08 tiếp tục dùng hợp đồng riêng.
- Duyệt tạo/liên kết đầu sách; bản sao vật lý chỉ có sau nhập kho. Người gửi xem trạng thái và phản hồi trong lịch sử Mobile.

## Push FID

- Flutter Firebase Messaging 17 dùng đăng ký Installation ID: bật `firebase_messaging_installation_id_enabled` (Android), `FirebaseMessagingInstallationIdEnabled` (iOS). Đăng ký listener trước `register()`, giới hạn chờ, không chặn `runApp`.
- `POST /api/v1/notifications/devices` thêm `identifierType: TOKEN|FID`, thiếu trường mặc định TOKEN cho thiết bị cũ. Mobile gửi FID; cache token cũ không tự đổi thành FID. Migration 41 backfill TOKEN. Firebase Admin 9.11.0 dùng `setFid` cho FID, `setToken` cho TOKEN.
- SMTP/FCM vẫn là delivery bất đồng bộ sau commit; chưa có outbox bảo đảm giao lại. Cần Firebase project/config và quyền thông báo thật để nghiệm thu delivery. [Hướng dẫn Firebase chính thức](https://firebase.google.com/docs/cloud-messaging/flutter/get-started).

## Migration và giá demo

- **41**: loại định danh push; **42**: ngành đề xuất, snapshot nguồn, FK và index. Liquibase master includeAll sẽ nhận khi backend chạy migration. Chưa áp vào DB ứng dụng trong task này.
- **43** chỉ chạy khi chỉ định context `demo` rõ ràng (`@demo`). Nếu chạy backend trên DB demo, thêm tham số `--spring.liquibase.contexts=demo` vào lệnh khởi động thông thường. Không bật cho DB thật.
- 43 chỉ đổi các sách vật lý có `price_source=CONFIGURED_FALLBACK` và `price_status=ESTIMATED`, thành 15–65 nghìn VND theo ID sách, nguồn `DEMO_GENERATED`. Từng sách giữ giá ổn định qua retry/restart; không random lại mỗi lần xem. Giá catalog/manual vẫn giữ nguyên, kể cả 150k; không sửa ledger, khoản phạt hoặc cọc đã ghi. Migration dữ liệu này không tự có rollback giá trước đó; cần backup nếu chạy trên DB có dữ liệu cần giữ.

## Kiểm tra trên máy có Flutter

Người dùng đã cho phép Flutter tạm để tự kiểm tra UI. Analyzer, 55/55 test và build Flutter Web preview đã PASS; SDK/cache/server tạm đã dọn. Ảnh, giới hạn và hướng dẫn xem lại ở [checkpoint UI](MOBILE_UI_20261105/README.md). Không có Android Studio/emulator được cài trong task này. Trên máy đã có SDK phù hợp pubspec, chạy `flutter analyze`, `flutter test` trong `mobile-app`; thêm build Android/iOS theo nền tảng có sẵn. Test mới tập trung sheet hồ sơ, API số dư dạng List, ngành chính thức và retry nhận trả.

Kiểm tra thủ công sau khi backend/migration và app cùng được cập nhật:

1. Mở/sửa/đóng hồ sơ nhiều lần với bàn phím mở; lưu `+84 356996264`, kiểm tra server lưu `0356996264`; thử lỗi mạng và tên trống.
2. Xem sách/đăng ký/phiếu/QR: giá hiện tại thống nhất. So cọc/nợ Mobile với API thẻ và sổ giao dịch sau một khoản sửa chữa 50k; lỗi API không hiện số dư 0 giả.
3. Phiếu có nhiều cuốn: nhận trả một cuốn hỏng, thanh toán rồi quét lại. Cuốn đó không còn editor trả, các cuốn đang mượn vẫn trả được. Thử retry khi mất mạng; không thêm phạt hoặc trừ cọc lần hai.
4. Chọn ngành CNTT, gửi đề xuất; Web lọc mã CNTT và duyệt/từ chối; Mobile lịch sử hiện ngành/nguồn/phản hồi. Thử API ngành lỗi và đề xuất cũ.
5. Đăng nhập, cấp quyền thông báo, kiểm tra đăng ký FID và nhận push khi foreground/background; mất kết nối FCM không ngăn vào app.

`INSTALL_FAILED_INSUFFICIENT_STORAGE` thuộc thiết bị/emulator. Giải phóng bộ nhớ hoặc dùng thiết bị có dung lượng rồi cài lại. Không tự Wipe Data/gỡ app vì mất dữ liệu; task này không chạy emulator hay xóa dữ liệu thiết bị.
