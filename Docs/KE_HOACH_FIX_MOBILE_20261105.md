# Sửa Mobile theo file 5

Trước khi nghiệm thu hoặc triển khai: đọc [những phần chưa kiểm thử/chưa triển khai](CHUA_KIEM_THU_20261105.md).

Nguồn: `C:/Users/Lenovo/Downloads/5.docx`, đọc ngày 05/11/2026, gồm nội dung chữ và 7 ảnh. Hash nguồn lưu ở `.codex-tmp/mobile-fixes-20261105/source.json`. Baseline `main/b88ac60` cùng các fix local và hòa giải merge; giữ nguyên các thay đổi từ task trước.

SHA-256 nguồn: `9d423d3cfa79ae73bf16aa72b196692b6c943f9d78d97a713271d793816f2f5a`.

## Batch và phạm vi

| Batch | Lỗi và kết quả cần đạt | Phạm vi |
|---|---|---|
| M01 | FCM báo API disabled; đổi SĐT có khoảng trắng/+84 gây lỗi đóng sheet. Giữ khởi động không phụ thuộc FCM, sửa vòng đời controller và chuẩn hóa số trước khi gửi. | Mobile cấu hình push, hồ sơ và test |
| M02 | Hiện giá trị sách khi đăng ký, chi tiết phiếu/quét QR; hiện đúng cọc còn lại và tổng nợ. Giá demo rẻ có nguồn rõ thay cho một mức fallback 150k. Không đổi số tiền lịch sử hoặc giá thực đã biên mục. | Mobile, DTO/API giá trị và sổ tiền, cấu hình/seed demo |
| M03 | Màn trả không cho gửi lại tài liệu đã nhận/đã xử lý; danh sách và chứng cứ phải cập nhật sau thao tác. Retry không phát sinh hồ sơ hoặc trừ cọc hai lần. Không tự coi PAID là đã nhận sách. | Quầy QR Mobile và hợp đồng nhận trả |
| M04 | Đề xuất chọn ngành chính thức, bỏ mức cần thiết; lưu ngành và lịch sử đề xuất, giải thích nơi duyệt. Màn thủ thư/Admin duyệt có lọc ngành. | Mobile form/history, backend đề xuất/ngành, Web màn duyệt, migration nếu cần |
| M05 | Chốt kiểm tra hồi quy, hợp đồng, cập nhật context và hướng dẫn kiểm tra thiết bị. | Các phần bị ảnh hưởng |

Ảnh ADB `INSTALL_FAILED_INSUFFICIENT_STORAGE` là lỗi dung lượng thiết bị/emulator; không sửa bằng cách xóa dữ liệu tự động hoặc đổi applicationId để né cài đè. Sẽ ghi hướng dẫn xử lý riêng.

## Ràng buộc kiểm tra

Người dùng xác nhận máy chưa cài Flutter vì tốn bộ nhớ. Không tải/cài Flutter hoặc khởi chạy emulator. Chạy backend/Web bằng công cụ hiện có, thêm test Mobile có thể chạy qua CI/máy có SDK; ghi rõ phần chưa chạy, không gọi đó là PASS. Không tự push, deploy hay áp migration vào DB ứng dụng.

## Checkpoint

- Đã đọc đủ chữ và 7 ảnh nguồn, xác định các module/caller chính.
- M01: đã sửa cấu hình FID trên Android/iOS, khởi động không chờ push, lưu loại định danh và chọn đúng FID/token ở Firebase Admin. Sheet hồ sơ sở hữu controller theo State, chuẩn hóa `+84 356996264` thành `0356996264`, giới hạn thời gian PUT. Test backend PASS; widget test đã thêm nhưng chưa chạy SDK.
- M02: Mobile lấy số dư từ `/api/v1/library-card`, đọc `/api/v1/transactions/my-ledger` đúng dạng List; lỗi tải không giả thành 0. Chi tiết sách/phiếu/QR hiển thị giá hiện tại. Migration 43 chỉ đổi giá `CONFIGURED_FALLBACK/ESTIMATED` trong context **demo được chỉ định rõ**, mức 15–65k, nguồn `DEMO_GENERATED`; giữ giá catalog/manual, tài liệu số và tiền lịch sử.
- M03: QR/backend chỉ cho trả item BORROWING/OVERDUE chưa có ngày trả/nhận thực tế; bỏ DAMAGED/LOST. Mobile không mở editor cho item đã xử lý; retry trong cùng lần quét giữ nguyên payload và idempotency key, khóa chỉnh sửa sau khi gửi, timeout 20 giây. Quét lại để đọc trạng thái server; không suy ra nhận sách từ PAID.
- M04: API `/proposals/majors` cho tài khoản đã đăng nhập đọc danh mục ngành chính thức; đề xuất lưu `majorId`, snapshot tên/nguồn. Mobile chọn ngành bắt buộc, bỏ thể loại/ưu tiên khỏi form và request mới. Web `/admin/suggestions` lọc theo mã ngành kết hợp trạng thái/từ khóa; tách đề xuất cũ chưa gán ngành, không tự ánh xạ thể loại cũ.
- M05: hoàn tất kiểm tra backend/Web, XML/plist/YAML và cập nhật context/hợp đồng. Flutter analyze/test/build và nghiệm thu FCM/thiết bị **chưa chạy** vì máy không có SDK. Chưa áp migration 41–43 vào DB ứng dụng; chưa push/deploy. Giá demo mới chỉ được kiểm chứng trên DB test, chưa làm đổi giá ở DB ứng dụng.

## Kết quả kiểm tra 05/11/2026

- Backend gate: **76/76 PASS** gồm PushDeviceService, FirebasePushNotificationService, LoanService, LibrarianQrService, BookProposalService, MobileFixMigrationIntegrationTest và 36 test Week6IntegrationTest. Có kiểm tra concurrent inspection retry, rollback và snapshot tiền/phạt/cọc.
- Backend bổ sung: **24/24 PASS**, gồm chạy lại 23 LoanServiceTest sau khi thêm assert giá/source/known và 1 HTTP integration mới cho ngành chính thức, đề xuất/lịch sử, phân quyền, dữ liệu số dư/ledger và đăng ký FID/loại không hợp lệ. Không cộng 24 vào 76 như các test độc lập vì 23 test trùng.
- Migration 41/42/43 chạy trên PostgreSQL Testcontainers cô lập: thiết bị cũ thành TOKEN, proposal cũ không bị gán ngành giả, FK chặn mã ngành lạ, context rỗng không đổi giá; context demo tạo 15–65k, giữ giá catalog/digital và giao dịch 50k. Không dùng DB ứng dụng.
- Web **343/343 PASS**, production build PASS. Còn cảnh báo cũ về budget CSS home/history và qrcode CommonJS; không phát sinh build error.
- Android Manifest XML, iOS plist/FID flags, 3 file migration YAML và liên kết Markdown nội bộ hợp lệ; `git diff --check` PASS. Đây không phải Flutter analyzer/build.
- Bổ sung Mobile tests: `edit_profile_sheet_test.dart`, `penalties_balance_test.dart`, `return_eligibility_test.dart`; sửa fixture/test đề xuất/ngành, phone và notification flow. Tất cả cần chạy lại bằng Flutter SDK trên máy khác/CI; chưa khẳng định PASS.
- Log gate trong `.codex-tmp/mobile-fixes-20261105/{backend-regression,backend-contract,web-tests,web-build}.log`. Thư mục này ignored; checkpoint/hợp đồng này là nguồn tiếp tục task.
- Kết thúc: `docker ps` không có container đang chạy; không khởi động Web/backend server hoặc emulator. HEAD vẫn `b88ac60`, không có unmerged paths; giữ nguyên nhánh main và toàn bộ thay đổi chưa commit từ trước.

## Khi tiếp tục

M01–M05 đã có code và kiểm tra khả dụng. **Cập nhật lượt kiểm tra UI sau khi được phép tải SDK tạm:** [ảnh/kết quả Mobile](MOBILE_UI_20261105/README.md): Flutter analyzer sạch lỗi, 55/55 test PASS (10 ca layout), build Flutter Web preview PASS. Sửa thêm overflow công nợ, sheet mượn, form/dialog QR. Phần “chưa chạy Flutter” bên trên là checkpoint trước lượt này. Chưa nghiệm thu camera/FCM/Android/iOS hoặc tương tác trình duyệt; công cụ điều khiển trình duyệt đã dừng vì không xác định chắc URL Windows. Không suy ra PASS thiết bị từ widget test.

Đọc [hợp đồng và hướng dẫn thiết bị](API_FIX_MOBILE_20261105.md), áp migration backend đúng DB và chỉ bật context demo khi cần giá giả lập. Migration 41–43 vẫn chưa áp DB ứng dụng. Đối chiếu giá nguồn thật nếu vẫn còn sách 150k có nguồn CATALOG; không tự sửa thành giá demo. Không chạy dịch vụ/emulator chỉ để đọc checkpoint.
