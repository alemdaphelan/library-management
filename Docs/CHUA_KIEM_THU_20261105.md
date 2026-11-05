# Những phần chưa kiểm thử / chưa triển khai — 05/11/2026

Phạm vi bàn giao: các fix local F01–F10, hòa giải `main/b88ac60`, tài liệu đã rà soát và sửa Mobile theo `5.docx`. Không coi việc push code là triển khai ứng dụng hay áp migration.

## Còn chờ kiểm tra

1. **Android/iOS thật:** chưa build APK/AAB hoặc bản iOS trong lượt sửa Mobile này; chưa cài/chạy trên điện thoại. Chưa kiểm tra quyền camera/thông báo, quét QR bằng camera, bàn phím hệ điều hành, vòng đời foreground/background/đóng mở app và khác biệt nền tảng.
2. **FCM thực tế:** chưa xác nhận nhận push với Firebase project thật, FID refresh, đổi tài khoản trên cùng thiết bị, mất mạng hoặc nhận thông báo khi app đã đóng. Test backend xác nhận chọn đúng FID/token; điều này chưa chứng minh giao push thành công.
3. **Thao tác trình duyệt:** bản Flutter Web preview build PASS, nhưng chưa nghiệm thu tương tác trong trình duyệt. IAB không attach được; công cụ Windows dừng vì không xác định chắc URL để thực thi chính sách. Không tiếp tục điều khiển sau khi công cụ dừng. Ảnh bàn giao là ảnh engine Flutter trong widget test.
4. **E2E Mobile → backend/DB ứng dụng:** các ca UI dùng dữ liệu/API/camera giả, chưa xác nhận toàn luồng trên thiết bị kết nối môi trường thật: đổi hồ sơ, đăng ký/quét/nhận trả, phạt 50k, số dư cọc/nợ, retry sau khi tắt app và đề xuất → Web duyệt → Mobile nhận phản hồi. Retry QR hiện chỉ giữ key/payload trong cùng lần quét, chưa lưu qua lúc tắt app.
5. **Migration/dữ liệu live:** migration 41–43 chưa áp vào DB ứng dụng; giá demo 15–65k chưa có hiệu lực trên DB đó. 43 cần context `demo` chỉ định rõ và chỉ đổi giá dự phòng; giá CATALOG/manual có thể vẫn là 150k. Cần đối chiếu `databasechangelog` trước khi áp cả các migration incoming khác; không suy ra DB đã đồng bộ từ kết quả Testcontainers. Chưa kiểm tra nâng cấp/rollback trên bản sao đầy đủ của DB ứng dụng.
6. **Dịch vụ ngoài/live probe:** chưa nghiệm thu mới SMTP, VNPAY hoặc Firebase trên môi trường thật cho lượt này. Live-read-only probe tùy chọn của ShelfLocationIntegrationTest vẫn skipped trong gate merge.
7. **Toàn bộ backend sau sửa Mobile:** gate merge đã kiểm tra 417 test (416 PASS, 1 skipped, gồm các lượt chạy lại fixture). Sau thay đổi Mobile/API, đã chạy gate liên quan 76/76 rồi 24/24 (23 test trùng, 1 HTTP contract mới); chưa chạy lại toàn bộ backend lần nữa trên bản cuối. Không cộng các lượt chạy trùng thành số test độc lập.
8. **SDK/dependency:** UI được kiểm tra bằng Flutter 3.47.9/Dart 3.13.7 tạm, có 6 dependency do SDK resolve/pin. Đã trả lockfile gốc để không ép đồng đội nâng SDK. Cần `flutter pub get`, analyze/test/build lại theo SDK của môi trường đích; chưa chứng minh mọi phiên bản Flutter thỏa min constraint đều cho cùng kết quả.

## Đã xác nhận để tránh hiểu nhầm

- Flutter analyzer sạch lỗi; **55/55 test Mobile PASS**, gồm 10 ca layout tại 320×640, chữ 1.0×/1.8× và inset bàn phím giả lập. Build Flutter Web preview PASS. Đã xem ảnh render và sửa overflow công nợ, sheet mượn, form/dialog QR.
- Web **343/343 test và production build PASS**; backend/migration trên PostgreSQL cô lập như số liệu ở trên. Không có kết quả test live được suy ra từ fixture.
- SDK/cache/server tạm đã dọn; không cài Android Studio/emulator trong task. Không deploy, không áp migration vào DB ứng dụng.
- Chi tiết/ảnh/hash: [checkpoint UI](MOBILE_UI_20261105/README.md), [kế hoạch Mobile](KE_HOACH_FIX_MOBILE_20261105.md), [hợp đồng API](API_FIX_MOBILE_20261105.md), [checkpoint merge](MERGE_MAIN_20261105.md).
