# Kiểm tra giao diện Mobile — 05/11/2026

Tiếp tục từ [checkpoint sửa 5.docx](../KE_HOACH_FIX_MOBILE_20261105.md). Người dùng đã cho phép dùng **Flutter tạm**, không cài Android Studio/emulator, dọn SDK/cache và tiến trình kiểm tra khi xong. Không áp migration hoặc gửi giao dịch lên DB ứng dụng trong lượt UI này.

Danh sách nghiệm thu còn chờ trước khi phát hành: [Những phần chưa kiểm thử / chưa triển khai](../CHUA_KIEM_THU_20261105.md).

## Phát hiện và sửa

| Màn | Bằng chứng trước sửa | Kết quả sửa |
|---|---|---|
| Công nợ | Mã hồ sơ/barcode dài gây RenderFlex tràn ngang 105px tại 320px | Cho mã hồ sơ xuống dòng trong phần có giới hạn chiều rộng; cọc/nợ xếp dọc khi màn hẹp và chữ lớn, tiêu đề được xuống hai dòng |
| Xác nhận mượn | Sheet tràn đáy 285px ở chữ thường và 1418px ở chữ 1.8× | Nội dung cuộn, tôn trọng SafeArea; toàn bộ giá/hạn mức và nút xác nhận có thể cuộn tới |
| Thủ thư nhận trả | Dropdown tình trạng tràn ngang 40px/210px | Giới hạn chiều rộng dropdown, nhãn chọn ngắn và menu đủ mô tả; kiểm tra cả phí sửa chữa và nhánh đền bù bằng tiền |
| Nhập QR thủ công | Dialog tràn đáy khi chữ lớn | Cho dialog cuộn, giữ thao tác xác thực/hủy |
| Hồ sơ | Kiểm tra lại vòng đời sheet và bàn phím | Đóng/mở/lưu hai lần không exception; ở chữ lớn và inset bàn phím 280px vẫn cuộn tới nút lưu |

## Phạm vi kiểm tra

- Flutter **3.47.9**, Dart **3.13.7**, engine Windows của SDK tạm chính thức. Không thay PATH hệ thống.
- `flutter analyze --no-pub`: **No issues found**.
- `flutter test --no-pub --concurrency=2 --reporter=expanded`: **55/55 PASS**, gồm **10 ca layout** ở 320×640, chữ 1.0×/1.8×, dropdown ngành dài, form thủ thư, công nợ, sheet mượn, hồ sơ và QR.
- Kiểm tra layout dùng widget thật trong `lib/`, dữ liệu giả trong `test/support/mobile_ui_fixture.dart`, camera giả `UiFakeScanner`. Không thay thế kiểm thử kết nối API thật, nhận push hoặc quét camera trên Android/iOS. Inset bàn phím được giả lập trong widget test; ảnh không phải bàn phím Android thật.
- Lệnh build/serve để xem cùng widget với dữ liệu giả: `flutter run -d web-server -t test/ui_preview.dart --web-port 8765`. Entry này nằm trong **test**, không phải `lib/main.dart` và không dùng làm bản phát hành.
- Ảnh PNG bên dưới được chụp từ engine Flutter, đã kiểm tra trực tiếp. Không phải mockup HTML hay Angular thu nhỏ. Banner chéo góc phải là debug banner.

## Ảnh đã lưu

- [Công nợ — chữ thường](penalties-1.0.png), [chữ 1.8×](penalties-1.8.png)
- [Thủ thư nhận trả — chữ thường](librarian-return-1.0.png), [chữ 1.8×](librarian-return-1.8.png)
- [Đề xuất ngành — chữ thường](proposal-1.0.png), [chữ 1.8×](proposal-1.8.png)
- [Sheet mượn — chữ thường](borrow-sheet-1.0.png), [chữ 1.8×](borrow-sheet-1.8.png) — nội dung cuộn, ảnh chụp một viewport
- [Hồ sơ khi giả lập bàn phím mở](profile-keyboard.png), [QR trả sách chữ lớn](ticket-qr.png)

## Checkpoint kết quả

Analyzer và 55 test đã PASS. Build Flutter Web của `test/ui_preview.dart` **PASS**, đầu ra tạm `.codex-tmp/mobile-fixes-20261105/preview-web` đã được dọn sau kiểm tra. Lượt đầu gặp lỗi ghi shader với output tương đối trên Windows; chạy lại output tuyệt đối thành công. Không sửa shader hoặc SDK để bỏ qua lỗi.

**Chưa ghi nhận PASS thao tác trong trình duyệt**: IAB không attach được webview; công cụ điều khiển cửa sổ không đọc được trạng thái rồi dừng vì không xác định đủ chắc URL Windows để thực thi chính sách. Đã dừng Computer Use theo yêu cầu công cụ; không tiếp tục điều khiển hoặc dùng cách khác để vượt chặn. Các ảnh trên là engine Flutter trong widget test, không phải ảnh xác nhận từ trình duyệt hay thiết bị Android/iOS.

Đã xác nhận xóa SDK/cache tạm tại `C:/Users/Lenovo/AppData/Local/Temp/codex-library-mobile-ui-20261105`, dừng server preview (không còn listener 8765), dọn `.dart_tool`, build, bundle Web và ảnh tạm trùng lặp. Giữ ảnh trong thư mục này cùng source/test/checkpoint. Không có emulator/server backend được khởi chạy. Log trong `.codex-tmp/mobile-fixes-20261105/{flutter-tests-final,flutter-analyze-final,flutter-preview-build}.log`.

Flutter tạm tự điều chỉnh 6 dependency do SDK pin và thêm cấu hình loại trừ analyzer. Đã khôi phục `pubspec.lock` gốc từ `.codex-tmp/mobile-fixes-20261105/before-ui-pubspec.lock` và gỡ block exclude chỉ do SDK thêm; không ép project/đồng đội nâng SDK ngoài phạm vi task. Bản dependency đã dùng kiểm tra lưu trong `.codex-tmp/mobile-fixes-20261105/ui-sdk-resolved-pubspec.lock`. Kết quả trên áp dụng Flutter 3.47.9 cùng dependency do SDK đó resolve; máy SDK khác cần chạy pub get/test theo SDK của máy đó. Không khôi phục các file Dart hoặc thay đổi Mobile đã sửa.
