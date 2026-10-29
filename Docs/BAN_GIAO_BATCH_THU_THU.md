# Checkpoint các batch thủ thư

Checkpoint mới nhất: **B02 hoàn tất; dừng trước B03**. Chi tiết B02 ở cuối tài liệu; hợp đồng form/API tại `Docs/API_B02_KIEM_TRA_TRA_SACH.md`.

## B01 ngày 29/10/2026

Trạng thái khi chốt B01: **B01 HOÀN TẤT**, lúc đó chưa bắt đầu B02. Baseline: `34d942b` cùng các thay đổi chưa commit của B01; trước B01 chỉ có ba file context/kế hoạch chưa theo dõi trong Git.

Đã sửa luồng giữ chỗ đơn/batch để gọi state machine PENDING -> RESERVED trong transaction, audit actor hệ thống (`changedBy`/`processedBy` null, lý do rõ ràng), thông báo giữ chỗ, khóa bản sao khi nhận giữ từ waitlist và thứ tự khóa batch theo bookId.

Đã bỏ hàng chờ/modal/nút duyệt của thủ thư; phiếu cũ được gọi API tương thích để kiểm tra lại điều kiện trước khi tải vào quầy. GET vẫn chỉ đọc, không sửa trạng thái. Luồng trả/phạt chưa thay đổi.

Kết quả trên mã nguồn cuối ngày 29/10/2026:

| Kiểm tra | Kết quả |
|---|---|
| Backend theo bộ chọn B01 bên dưới | **PASS 78/78**, 0 lỗi, 0 bỏ qua; gồm PostgreSQL/Redis/Mailpit Testcontainers và HTTP E2E |
| Web với Vitest một worker | **PASS 124/124**, đủ 40 file test |
| Web production build | **PASS**, còn warning CSS budget portal history/home và qrcode CommonJS |
| Diff/tài liệu | `git diff --check` PASS; kiểm tra UTF-8 và code fence PASS |

Đã dừng tại B01. Khi người dùng gọi B02, đọc phần B02 của kế hoạch và diff hiện tại rồi tiếp tục, không chạy lại các batch đã hoàn thành nếu không có thay đổi liên quan.

### Các file chức năng đã thay đổi

- `backend/src/main/java/com/huit/library/modules/circulation/service/LoanService.java`: tự duyệt hai luồng, dùng khóa nhận hold waitlist, giữ deadline, thứ tự khóa batch, thông báo staff mới.
- `backend/src/main/java/com/huit/library/modules/circulation/service/CirculationStateService.java`: chuyển trạng thái/audit hệ thống và kiểm tra lại phiếu cũ; tương thích khi gọi approve trên RESERVED hợp lệ.
- `backend/src/main/java/com/huit/library/modules/circulation/controller/LoanController.java`: mô tả hợp đồng OpenAPI mới; giữ đường dẫn và phân quyền.
- `backend/src/main/java/com/huit/library/modules/notification/service/EmailNotificationService.java`: tiêu đề email giữ chỗ cho LOAN_APPROVED/LOAN_RESERVED.
- `web-app/src/app/pages/admin/circulation/circulation.{ts,html,css}`: bỏ queue/modal duyệt; tự kiểm tra phiếu PENDING cũ khi tải; giữ quét/giao, mượn tại quầy, trả sách hiện tại.
- `web-app/src/app/pages/admin/borrows/borrows.{ts,html}`: nhãn giữ chỗ, liên kết mở tại quầy, không duyệt tay; giữ từ chối có lý do.
- `web-app/src/app/pages/admin/notifications/notifications.ts`, `web-app/src/app/services/circulation.service.ts`: nhãn thông báo/hướng dẫn API tương thích.
- Test: `LoanServiceTest`, `CirculationStateServiceTest`, `Week6IntegrationTest`, `circulation.spec.ts`, `borrows.spec.ts`.
- Context/kế hoạch: `PROJECT_CONTEXT.md`, `Docs/KE_HOACH_BATCH_THU_THU.md`, checkpoint này. Không có migration/schema mới.

### Hợp đồng bàn giao cho người phụ trách Sinh viên và Admin

| Luồng | Hành vi sau B01 |
|---|---|
| `POST /loans/reserve` thành công giữ sách | `status=RESERVED`, giữ nguyên các trường cũ; không cần chờ thủ thư duyệt |
| Luồng đơn hết bản sẵn sàng | WAITLIST như trước; không tạo phiếu RESERVED giả. Hold waitlist hết hạn không được hồi sinh |
| `POST /loans/reserve-batch` | Thành công trả RESERVED, tất cả bản sao đã giữ; có thể nhận hold waitlist hợp lệ của chính người dùng. Thiếu tài liệu thì lỗi 409 và không tạo một phần phiếu; người dùng vào hàng đợi riêng. Các mảng bookIds/bookTitles/barcodes cùng thứ tự tăng bookId; ghép dữ liệu bằng định danh, không giả định thứ tự yêu cầu ban đầu |
| Chi tiết chờ giao | Ticket RESERVED nhưng item `returnStatus=PENDING` là đúng: chưa giao sách. Không biến item thành BORROWING ở bước giữ chỗ |
| Thời hạn | Vẫn theo chính sách cũ. Nếu batch có nhiều hold thì giữ deadline sớm nhất áp dụng; không tự kéo dài lượt hàng đợi |
| Thông báo | Staff nhận `LOAN_RESERVED` với `referenceType=LOAN_TICKET`; người đọc vẫn nhận `LOAN_APPROVED` để giữ tương thích, nội dung nói rõ hệ thống tự giữ chỗ |
| Phiếu PENDING cũ | Web quầy gọi POST `/{id}/approve` để kiểm tra thẻ/nợ/quá hạn/số lượng/bản sao/hạn giữ rồi chuyển. Lỗi không cho giao và không đổi trạng thái. GET không tự duyệt |
| Tương thích approve | Vẫn ADMIN/LIBRARIAN; không dùng cho Student. RESERVED hợp lệ trả trạng thái hiện tại, không thêm audit/thông báo lần nữa. PENDING hợp lệ ghi actor hệ thống null với lý do rõ ràng |
| Checkout | Vẫn do thủ thư quét barcode và gọi `/loans/checkout`; RESERVED không có nghĩa đã nhận sách |

Người phụ trách Sinh viên cập nhật các nhãn “chờ duyệt/đã duyệt” thành “đang giữ chỗ/chờ nhận”, và không thêm thao tác gọi approve cho Student. Không sửa màn hình role Sinh viên/Mobile trong B01. Phê duyệt đề xuất mua sách, cấu hình Admin và các batch khác không bị đổi.

### Kiểm tra và cách tái chạy

Tại `backend/`, sử dụng JDK đang có `C:\Program Files\Java\jdk-21` (biên dịch release 17 theo POM), Docker hoạt động:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\mvnw.cmd --batch-mode '-Dtest=LoanServiceTest,Circulation*Test,WaitlistServiceTest,ReservationNoShowServiceTest,LibrarianQrServiceTest,LoanAuthorizationContractTest,Week6IntegrationTest' test
```

Bộ chọn trên gồm ca reservation mới, eligibility/legacy/audit, locks/idempotency/checkout/return, migration circulation và HTTP E2E. E2E hiện đi thẳng đặt -> QR -> checkout, không gọi duyệt. Test PostgreSQL thật kiểm tra hai người tranh bản cuối chỉ có một RESERVED, và batch không tạo phiếu một phần khi có cuốn không sẵn sàng.

Tại `web-app/`: `npm.cmd run build` và `npm.cmd test -- --watch=false` là các lệnh chuẩn. Trong phiên này runner chuẩn có worker thoát/timeout trước khi chạy test; thử import jsdom độc lập lúc đầu mất khoảng 3 phút, sau đó đã nạp được bình thường. Chưa kết luận nguyên nhân của độ trễ; không thay dependency hoặc cấu hình production để xử lý.

Lượt PASS dùng file tạm `.codex-tmp/b01-vitest.config.mjs` (được Git ignore) với nội dung sau:

```javascript
export default { test: { maxWorkers: 1, pool: 'threads', fileParallelism: false } };
```

Chạy từ `web-app/`: `npm.cmd test -- --watch=false --runner-config=../.codex-tmp/b01-vitest.config.mjs`. Toàn bộ 40 file/124 test chạy, không loại test. Build có warning CSS budget ở portal history/home và CommonJS qrcode; build vẫn thành công. Tests có cảnh báo sourcemap thiếu nguồn của package ZXing.

### Checklist kiểm tra thủ công khi nhận bản chạy

1. Sinh viên hợp lệ đặt một cuốn/nhiều cuốn: thấy RESERVED; barcode ở RESERVED, chưa BORROWING.
2. Thủ thư mở quầy: không có tab/nút duyệt. Tải phiếu, quét đủ barcode, checkout thành BORROWING.
3. Mở phiếu PENDING cũ từ danh sách hoặc QR: còn hạn/đủ điều kiện được chuẩn bị tự động; thẻ khóa/nợ/hết hạn/sai người giữ bị từ chối. Không bulk-migrate dữ liệu đang chạy.
4. Hai tài khoản tranh một bản cuối: chỉ một người giữ được; người kia vào WAITLIST ở luồng đơn. Người được thông báo hàng đợi nhận đúng bản của mình, không lấy bản của người khác.
5. Từ chối giữ chỗ phải có lý do, nhả bản sao theo service hiện tại. Đề xuất mua sách vẫn có luồng duyệt riêng.
6. Mở thông báo mới đi đúng phiếu ở quầy; kiểm tra lại nhãn ở client Sinh viên do người phụ trách role đó cập nhật.

Chưa chạy nghiệm thu camera/thiết bị thật hoặc triển khai/restart server người dùng. Mã nguồn chưa commit/push. Chỉ chuyển sang B02 khi người dùng yêu cầu.


## B02 ngày 29/10/2026

Trạng thái: **B02 HOÀN TẤT — DỪNG TRƯỚC B03**. Kế thừa toàn bộ diff B01 chưa commit. Baseline vẫn là HEAD `34d942b` cộng diff B01–B02, chưa commit/push.

### Đã hoàn thành

- API mới `POST /loans/return-inspection` cho ADMIN/LIBRARIAN. Nhận 1–3 `loanDetailId`, NORMAL/DAMAGED/LOST; báo mất không cần quét một barcode vật lý. Giữ `/loans/return` tương thích cho client hiện có.
- `ReturnInspectionService` điều phối trong transaction: kiểm tra cả lượt, dùng `PenaltyCaseService`/`LateFineService` và ledger/cọc hiện có; không trả thường trước khi lập hồ sơ phạt. Item mất/hỏng không bị chuyển AVAILABLE hoặc cấp cho waitlist.
- Trả biên nhận theo từng tài liệu với mã/trạng thái hồ sơ, phí trễ mới/lũy kế, phí phạt mới, cọc đã cấn, nợ tăng và số dư bạn đọc; không giả rằng mọi tài liệu đã hoàn tất trả.
- Snapshot JSON của biên nhận lưu cùng transaction trong `circulation_operations.result_payload`. Replay trả nguyên snapshot gốc kể cả sau nạp tiền/khép hồ sơ. Cùng key khác nội dung trả 409; hai request cùng key đồng thời chỉ ghi một hồ sơ và cấn cọc một lần.
- Thêm migration 28 là cột TEXT nullable, không sửa lịch sử migration và không backfill bản ghi cũ. Đã đưa vào master test circulation và kiểm tra trên PostgreSQL thật.
- Dùng chung helper nhận trả thường/khép phiếu của `CirculationStateService`; luồng cũ cũng khóa ticket -> detail -> copy. Projection `ReturnTarget` đọc định danh trước khi chờ khóa, tránh dùng entity trạng thái cũ. Chi tiết SETTLED được tính là đã hoàn tất khi trả tài liệu còn lại.
- Sửa validation thiếu damageSeverity thành 400 thay vì NullPointerException. Thiếu biểu phí/đền bù không hợp lệ làm rollback cả lượt, gồm phiếu phạt/cọc/nợ/late fine/waitlist/thông báo đã xử lý trước đó.

### File thuộc B02

- Mới: `backend/src/main/java/com/huit/library/modules/circulation/controller/ReturnInspectionController.java`.
- Mới: `backend/src/main/java/com/huit/library/modules/circulation/service/ReturnInspectionService.java`.
- Mới: DTO `ReturnInspectionRequestDTO`, `ReturnInspectionResponseDTO`, `ReturnTarget` trong `modules/circulation/dto/`.
- Sửa: `modules/circulation/service/CirculationStateService.java`, `modules/circulation/repository/LoanDetailRepository.java`, `modules/circulation/entity/CirculationOperationEntity.java`, `modules/penalty/service/PenaltyCaseService.java`.
- Migration: `backend/src/main/resources/db/changelog/changesets/28-return-inspection-receipts.yaml`; test master `backend/src/test/resources/db/changelog/circulation-test-master.yaml`.
- Test: mới `ReturnInspectionServiceTest`; bổ sung `Week6IntegrationTest`, `CirculationMigrationIntegrationTest`, `LoanAuthorizationContractTest`; cập nhật stub theo khóa mới trong `CirculationTransactionServiceTest`.
- Tài liệu: `Docs/API_B02_KIEM_TRA_TRA_SACH.md`, checkpoint này, kế hoạch batch và `PROJECT_CONTEXT.md`. Không sửa thêm file Web/Mobile trong B02; các diff Web hiện có thuộc B01.

### Kết quả kiểm tra mới nhất

**PASS 89/89 Backend tests**, 0 lỗi/0 bỏ qua, ngày 29/10/2026. Lệnh từ `backend/`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\mvnw.cmd --batch-mode '-Dtest=ReturnInspectionServiceTest,Circulation*Test,LoanServiceTest,LoanAuthorizationContractTest,PenaltyCaseServiceTest,LateFineServiceTest,DebtLedgerServiceTest,WaitlistServiceTest,Week6IntegrationTest' test
```

Phạm vi bằng chứng:

1. Migration cột receipt nullable trên PostgreSQL thật; không yêu cầu backfill operation cũ.
2. Lượt hỗn hợp bình thường/hỏng/mất: chỉ cuốn thường được trả/promote FIFO; hai cuốn sự cố ở MAINTENANCE/LOST, phiếu chưa bị khép sai.
3. Hỏng nhẹ 200.000/cọc 50.000 -> cấn 50.000, tăng nợ 150.000; tiền và mã hồ sơ không thay đổi khi replay sau một giao dịch nạp cọc thật.
4. Thay đổi thứ tự items/cách viết thập phân không tính lại tiền; đổi nội dung với cùng key hoặc key khác cho sự cố đã ghi nhận bị chặn.
5. Lỗi thiếu biểu phí ở item cuối rollback cả trả thường, phí trễ, hồ sơ trước đó, ví, operation, promotion FIFO và thông báo.
6. Hai request cùng key chạy hai thread dùng PostgreSQL: một biên nhận, một hồ sơ, cọc áp dụng đúng một lần; một response replayed=false và một true.
7. HTTP với JWT: Student bị 403, Librarian được dùng; thiếu severity bị 400; LOST được nhận bằng loanDetailId, không có trường quét barcode.
8. Sách thay thế đủ phí nhưng chưa nghiệm thu giữ WAITING_REPLACEMENT và ngày trả null. Sau nghiệm thu là SETTLED; API trả cũ hoàn tất cuốn khác khép đúng phiếu, bản sao gốc vẫn LOST. Replay vẫn giữ snapshot trước nghiệm thu.
9. Unit test replay sau đóng quầy, key thuộc loại thao tác khác, receipt hỏng không được chạy lại nghiệp vụ, dữ liệu trùng/thiếu. Hồi quy circulation, kế toán, hàng đợi và HTTP E2E B01 đi kèm vẫn PASS.

`git diff --check` được kiểm tra lại khi chốt. Không chạy lại Web/Mobile vì B02 không đổi client; kết quả 124 Web test/build ở mục B01 là bằng chứng của lượt B01, không phải một lượt chạy mới của B02.

### Bước tiếp theo khi được gọi B03

Đọc `Docs/API_B02_KIEM_TRA_TRA_SACH.md` trước khi sửa quầy. Thay quét-là-trả-ngay bằng form xác nhận tình trạng; chọn báo mất từ phiếu khi không có sách để quét; gửi một request inspection với key giữ nguyên khi retry. Không gọi return thường rồi tạo penalty bằng request thứ hai. UI lấy số tiền từ receipt và dùng mã hồ sơ để mở phần xử lý phạt.

Migration 28 đã được kiểm thử trong Testcontainers; chưa chạy trực tiếp trên DB của người dùng, chưa restart/deploy server. Chưa làm form B03 và chưa nghiệm thu thiết bị thật. Dừng tại B02 cho đến khi người dùng yêu cầu batch tiếp theo.


## B03 ngày 29/10/2026

Trạng thái: **B03 HOÀN TẤT — DỪNG TRƯỚC B04**. Baseline vẫn HEAD `34d942b` cộng toàn bộ diff B01–B03 chưa commit/push. B03 không thay Backend, schema hoặc Flutter.

### Đã hoàn thành

- Form riêng trong tab Trả sách: quét camera/nhập barcode chỉ thêm tài liệu; xác nhận tình trạng mới gọi một request inspection. Đã bỏ `completeAutomaticReturn` và POST trả thường sau quét; giao sách B01 vẫn giữ nguyên.
- Tra cứu bằng mã phiếu hoặc MSSV/họ tên; chọn đúng tài liệu để báo mất không cần barcode vật lý. Danh sách phiếu đang mượn/quá hạn có liên kết mở quầy trả theo phiếu.
- Tối đa ba tài liệu/lượt, chặn trùng và tài liệu đã có sự cố/đã trả. Kiểm tra `itemStatus` thực của chi tiết bên cạnh `status` của phiếu; SETTLED/LOST/DAMAGED không được nhận thành lượt trả mới chỉ vì phiếu còn BORROWING.
- Trường động theo bình thường/hỏng nhẹ/hỏng nặng/mất, sửa chữa/thay thế/tiền, còn lưu hành, giá bìa và tỷ giá ngoại văn. Payload bỏ trường ẩn không áp dụng; không tính mức phạt ở frontend.
- Khóa form và chuyển tab khi gửi/chưa rõ kết quả; request JSON/UUID được giữ nguyên khi retry, kể cả sau giờ đóng quầy. SessionStorage theo tài khoản trong cùng tab giúp phục hồi sau reload mà không tự gửi. Phản hồi 200 thiếu biên nhận vẫn giữ lượt để đối chiếu; lỗi 4xx xác định có nút sửa riêng. Hủy trước gửi không thay đổi dữ liệu.
- Biên nhận dùng số tiền server: phí mới (không cộng phí trễ lũy kế lần nữa), đã cấn cọc, nợ tăng; từng tài liệu có trạng thái thật/mã hồ sơ/link xử lý. Số dư theo từng bạn đọc được gắn với phiếu tương ứng; cảnh báo snapshot không phải số dư cập nhật sau này. LOST/MAINTENANCE không thông báo về kho.
- Điều chỉnh CSS bố cục Web nhân viên ở màn hình hẹp để sidebar thu gọn không ép form; nút menu vẫn ẩn/hiện được. Đây là hỗ trợ Web responsive, không phải nâng cấp Flutter.
- Cập nhật context, kế hoạch và hướng dẫn/hợp đồng bàn giao Mobile: [HUONG_DAN_B03_QUAY_TRA_SACH.md](HUONG_DAN_B03_QUAY_TRA_SACH.md).

### File thuộc B03

- Mới: `web-app/src/app/pages/admin/circulation/return-inspection.{ts,html,css}`, `return-inspection.form.ts`, hai file spec tương ứng.
- Mới: `web-app/src/app/services/return-inspection.models.ts`.
- Sửa: `circulation.{ts,html,css,spec.ts}`, `services/circulation.service.{ts,spec.ts}`, `pages/admin/borrows/borrows.html`.
- CSS responsive và nhãn nút điều hướng: `layouts/admin-layout/admin-layout.{css,html}`.
- Tài liệu: hướng dẫn B03, API B02 (liên kết client mới), checkpoint, kế hoạch và `PROJECT_CONTEXT.md`. Các diff Backend/B01 còn lại kế thừa nguyên từ các batch trước.

### Kết quả kiểm tra mới nhất

| Kiểm tra ngày 29/10/2026 | Kết quả |
|---|---|
| Toàn bộ Web specs, không loại test | **PASS 147/147**, 42 file |
| Web production build sau chỉnh CSS | **PASS** |
| Edge headless với API fixture cô lập | **PASS**, desktop 1440px và Web hẹp 390px |
| `git diff --check` | **PASS** |

Lệnh từ `web-app/` (cấu hình một worker giống B01, lưu trong `.codex-tmp/` được ignore):

```powershell
npm.cmd test -- --watch=false --runner-config=../.codex-tmp/b01-vitest.config.mjs
npm.cmd run build
```

23 test tăng so với B01: staging/hủy/không trả ngay, camera forwarding, tra cứu phiếu/người mượn, trạng thái chi tiết, form động/validation, gửi trùng, frozen retry sau lỗi/đóng quầy, reload và cách ly tài khoản, chỉnh lượt bị từ chối, biên nhận/tiền/link và phản hồi thiếu dữ liệu. Hồi quy các Web specs cũ cũng PASS. Build còn warning CSS budget cũ ở portal home/history và CommonJS qrcode; test có warning sourcemap ZXing thiếu nguồn.

Browser QA dùng bản production, hồ sơ tạm, chặn toàn bộ API sang fixture: báo mất từ phiếu, chưa có POST trước xác nhận, đúng một POST inspection, receipt 200.000/cấn 50.000/nợ 150.000 và link hồ sơ #77, không thông báo AVAILABLE, không lỗi runtime. Đã xem ảnh desktop/form hẹp/receipt và kiểm tra chiều rộng form/nội dung không bị sidebar ép hoặc cắt, nút thu gọn sidebar hoạt động. Script/log/ảnh kiểm tra nằm trong `.codex-tmp/b03-*`, chỉ là bằng chứng cục bộ được Git ignore; fixture không nằm trong UI sản phẩm.

Chưa chạy UI xuyên suốt với Backend/DB thật hoặc nghiệm thu camera thiết bị thật. Backend không đổi nên không chạy lại Maven; 89 test ở B02 là bằng chứng lịch sử riêng. Backend chạy phục vụ Web phải có API B02 và migration 28; task không restart/deploy server hoặc cập nhật DB người dùng. Dev server tạm dùng kiểm tra đã dừng.

### Điểm tiếp tục

**Dừng tại B03; B04–B16 chưa làm.** Khi người dùng gọi B04, đọc phần B04 trong kế hoạch rồi kiểm tra mapping khoa hiện có, API/ledger liên quan trước khi thiết kế báo cáo quá hạn/công nợ và Excel. Không đưa Mobile hoặc dashboard vào B04. Giữ toàn bộ diff B01–B03 hiện có khi tiếp tục.

## B04 ngày 29/10/2026

Trạng thái: **B04 HOÀN TẤT — DỪNG TRƯỚC B05**. Baseline vẫn HEAD `34d942b` cộng diff B01–B04 chưa commit/push. Giữ nguyên các thay đổi B01–B03, không sửa Web/Mobile trong B04.

### Đã hoàn thành

- Module Backend `reporting`, GET `/api/v1/librarian-reports/student-obligations`, `/export`, `/faculties`, chỉ LIBRARIAN/ADMIN. Preview phân trang 0-based; export cùng mode/khoa/từ khóa nhưng lấy toàn bộ, không phụ thuộc page/size.
- Ba mode độc lập OVERDUE/DEBT/EITHER; một sinh viên một dòng. Đã trả sách vẫn còn nợ xuất hiện trong DEBT; quá hạn chưa được scheduler ghi phí vẫn có thể nợ bằng 0. Chỉ user_type STUDENT; trạng thái detail/ticket và hạn trả thực quyết định còn quá hạn, không lấy LOST/DAMAGED/SETTLED làm sách quá hạn đang giữ.
- Khoản phí, thanh toán/cấn cọc, miễn giảm, điều chỉnh và dư nợ lấy từ transactions/ledger; không dùng totalLateFine làm nợ, không cộng PAYMENT/allocations lần nữa. Tổng hợp từng nguồn trước khi ghép để không nhân tiền theo số cuốn/phiếu. Tiền đã ghi sổ cả lịch sử, không phải ước tính phí tương lai/chưa ghi.
- Hiển thị số nợ ví riêng, chênh ví-ledger và chênh đối soát ledger; ví chưa có là null. Ví dương nhưng không có khoản ledger không bị mất khỏi báo cáo. Không sửa hay che dữ liệu lệch khi đọc.
- Transaction read-only REPEATABLE_READ cho từng request, chung thời điểm chốt và timezone Asia/Ho_Chi_Minh. GET không cộng phí/cấn cọc/chuyển trạng thái. Preview/export là hai snapshot hiện tại riêng, không cam kết đóng băng giữa hai request.
- Migration **29-academic-report-mapping.yaml**: khoa có ID, ánh xạ department nguồn -> khoa/ngành, ràng buộc một chuỗi chỉ về một khoa; thêm index cho truy vấn. Không sửa migration cũ. Đã chạy migration trong DB Testcontainers.
- **Dữ liệu mapping:** repo chưa có danh mục xác nhận, đã hỏi nguồn trong lượt này; chưa nhận được nguồn để nạp. Không seed khoa/mapping giả. Null/chuỗi chưa ánh xạ hiện UNCLASSIFIED/Chưa phân loại; giữ chuỗi gốc. Cần nhóm cung cấp danh mục thật và mapping được xác nhận trước khi lọc theo các khoa thật. Cấu trúc/API và cơ chế lọc đã được kiểm thử với fixture có mapping; chưa có màn/API quản trị danh mục.
- Excel thật dùng POI SXSSF: Tong hop / Qua han / So no, header/bộ lọc/thời điểm/đơn vị/freeze/autofilter, rỗng vẫn có header. Mọi chuỗi nguồn là STRING, không tạo công thức từ MSSV/tên/lý do. Tiền quá 15 chữ số giữ dạng text. Tách sheet nếu vượt giới hạn dòng Excel; snapshot/response vẫn dùng bộ nhớ theo lượng dữ liệu, chưa làm job export nền.
- Response no-store, tên file attachment có mode/thời điểm; expose Content-Disposition qua CORS cho B05. Hợp đồng chi tiết và bàn giao: [API_B04_BAO_CAO_QUA_HAN_CONG_NO.md](API_B04_BAO_CAO_QUA_HAN_CONG_NO.md).

### File thuộc B04

- Mới: `backend/src/main/java/com/huit/library/modules/reporting/`: controller `StudentObligationReportController`, DTO `StudentObligationReport`, repository `StudentObligationReportRepository`, service `StudentObligationReportService`, exporter `StudentObligationExcelExporter`, hai entity `AcademicFacultyEntity` / `AcademicDepartmentMappingEntity`.
- Mới: migration `backend/src/main/resources/db/changelog/changesets/29-academic-report-mapping.yaml`.
- Mới: `backend/src/test/java/com/huit/library/integration/StudentObligationReportIntegrationTest.java`.
- Sửa: `config/SecurityConfig.java` (expose header), `Week6IntegrationTest.java` (thêm một test HTTP báo cáo), `src/test/resources/db/changelog/circulation-test-master.yaml` (include 29). Phần diff cũ trong hai file test này vẫn giữ nguyên từ B01–B02.
- Tài liệu: API B04, checkpoint, kế hoạch và context. Không thay dependencies/biểu phí/cách ghi sổ/caller Web cũ.

### Kết quả kiểm tra mới nhất

**PASS 47/47 Backend tests**, không lỗi/không bỏ qua, ngày 29/10/2026. Lệnh từ `backend/`:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd --batch-mode '-Dtest=StudentObligationReportIntegrationTest,CirculationMigrationIntegrationTest,DebtLedgerServiceTest,LateFineServiceTest,PenaltyCaseServiceTest,Week6IntegrationTest' test
```

Gồm 10 test báo cáo mới, 6 test migration, 23 HTTP/nghiệp vụ integration (thêm 1 test B04), 8 unit test ledger/phạt liên quan. Bằng chứng mới:

1. PostgreSQL thật chạy migration 29, mapping chính xác/thiếu khoa, không tự seed danh mục, chặn mapping trùng.
2. Không nợ/quá hạn, chỉ nợ, chỉ quá hạn, cả hai, trả rồi vẫn nợ; nhiều phiếu/khoản phí không nhân tiền. Lượt đọc không tạo thêm giao dịch.
3. Nghĩa vụ 200.000; đáp ứng 100.000 gồm cọc 50.000, tiền nộp 20.000, miễn 20.000, điều chỉnh 10.000 -> thanh toán/cấn cọc 70.000, miễn 20.000, điều chỉnh 10.000, còn nợ 100.000. PAYMENT/allocations/topup và điều chỉnh pending không bị cộng sai.
4. Loại tài liệu terminal/sự cố, chưa tới hạn, fine-stopped; giữ nguyên dữ liệu department gốc, ví null và ví lệch được phân biệt.
5. Preview trang một dòng trong ba sinh viên, export đủ ba dòng + ba chi tiết và khoản nợ; mở lại cả ba sheet bằng POI kiểm tra tiền và ngày. Rỗng vẫn hợp lệ, tìm kiếm `%`/`_` literal, chuỗi SQL không mở rộng tập.
6. Chuỗi MSSV/tên/lý do có ký tự công thức không thành FORMULA; số tiền lớn giữ nguyên khi đọc lại file.
7. MockMvc với JWT và DB thật: anonymous/Student bị từ chối cả ba endpoint; LIBRARIAN/ADMIN xem và tải được, đúng content type/filename/no-store/CORS; mode/khoa/size sai bị 400; export không áp dụng page/size truyền thêm.

Compile Backend và `git diff --check` PASS. Lượt chạy đầu trong sandbox không truy cập được Docker pipe; chạy lại có quyền Docker bằng Testcontainers cô lập đã PASS. Log có cảnh báo Mongo chưa kết nối và Redis reconnect khi container dừng từ bộ integration hiện có; không phải lỗi assertion. Không chạy Web/Flutter vì B04 không sửa client; số 147 Web test ở B03 không phải lượt chạy lại của B04.

### Điểm tiếp tục

**Dừng tại B04. B05–B16 chưa làm.** Khi được gọi B05, đọc API B04 rồi thêm bộ chọn mode/khoa/từ khóa, preview phân trang, snapshot/VND và tải Blob toàn bộ vào `pages/admin/borrows`; dùng đúng faculty IDs và hiển thị Chưa phân loại khi chưa có mapping. Giữ các thao tác quầy/phiếu hiện có. Chưa restart/deploy Backend hoặc áp migration 29 trên DB người dùng; môi trường chạy tính năng mới cần migration 29.

### B04 bổ sung — phân loại khoa/ngành theo yêu cầu “tất cả đều nên được phân loại hết”

Trạng thái: **BỔ SUNG HOÀN TẤT — DỪNG TRƯỚC B05**. Nội dung này cập nhật phần “chờ danh mục/mapping” ở checkpoint B04 ban đầu phía trên.

- Đã chủ động đối chiếu [danh mục khoa HUIT](https://huit.edu.vn/) và [danh sách 39 ngành ngày 03/07/2026](https://ts.huit.edu.vn/thong-bao/danh-muc-nganh-dao-tao-trinh-do-dai-hoc), truy cập 29/10/2026. Khoa phụ trách dựa trên liên kết website khoa của mỗi dòng ngành; không suy từ trọng số recommender. Migration **30-seed-huit-academic-classification.yaml** nạp 17 khoa/39 ngành, tên ngành, `Ngành <tên>`, mã ngành, tên khoa, CNTT/Khoa CNTT và alias ATTP trong repo. ID khoa là ID nội bộ, mã ngành là mã chính thức.
- Các department có trong seed sinh viên (`CNTT`, `Khoa Công nghệ thông tin`) đều nhận được khoa FIT. Nhãn chỉ nói khoa không tự được gán một ngành cụ thể; báo cáo vẫn giữ rawDepartment.
- Chuẩn hóa Unicode NFD, dấu tiếng Việt/đ, hoa thường, khoảng trắng và dấu câu, ghép đúng alias. Unique index ngăn hai chuỗi tương đương ánh xạ về hai khoa, tránh nhân dòng/tiền. Giữ mapping thủ công có sẵn; migration báo lỗi nếu dữ liệu cũ có khóa chuẩn hóa xung đột. Không sửa migration 29.
- Thêm staff GET `/api/v1/librarian-reports/student-obligations/classification-coverage` trả tổng sinh viên, đã/chưa phân loại, complete và nhóm department thiếu mapping kèm số lượng. Kiểm tra toàn bộ STUDENT, kể cả không nợ/quá hạn, không trả tên/MSSV. Mục tiêu hoàn tất dữ liệu thật là complete=true và tổng số sinh viên đúng; null/rỗng/chuỗi không xác định phải sửa nguồn, không gán một khoa giả cho đủ tỷ lệ.
- **PASS 51/51 Backend tests**, 0 lỗi/0 bỏ qua; chạy lại cùng 6 class trong lệnh B04 phía trên. Gồm 14 report/catalog/coverage tests, 6 migration tests, 23 integration/HTTP tests và 8 unit ledger/phạt tests. Kiểm chứng PostgreSQL thật: 39 ngành x ba dạng (tên, tiền tố Ngành, mã), 17 tên khoa và bốn dạng department seed = **138/138 fixture sinh viên phân loại đúng**, coverage.complete=true. Test riêng xác nhận audit không bỏ qua người không có nợ/quá hạn và nêu đúng số liệu thiếu/null/rỗng, không fuzzy-match câu chỉ chứa từ CNTT. HTTP/JWT chặn anonymous/Student ở cả coverage, ADMIN/LIBRARIAN được đọc. Tiền/Excel/rollback cũ vẫn PASS; `git diff --check` PASS. Log cục bộ `.codex-tmp/b04-classification-final.log` được ignore.
- File mới: migration 30. File sửa bổ sung: reporting DTO/repository/service/controller, report integration tests, Week6IntegrationTest, test changelog master và tài liệu/context. Chưa làm B05 hoặc đổi Web/Mobile.
- Khi rà soát không có container project hoặc dịch vụ DB/Backend trên các cổng mặc định đang chạy. Chưa áp migration vào DB người dùng hoặc restart server; không dùng tỷ lệ fixture để tuyên bố DB ngoài repo đã đạt 100%. Sau khi môi trường chạy migration 29–30, gọi classification-coverage để xác nhận dữ liệu thật và sửa các department còn thiếu nếu có. Hợp đồng/nguồn chi tiết đã cập nhật ở API B04.

## B05 ngày 29/10/2026

Trạng thái: **B05 HOÀN TẤT — DỪNG TRƯỚC B06**. Baseline vẫn HEAD `34d942b` cộng diff B01–B05 chưa commit/push. Giữ nguyên các diff trước, không thay Backend/schema/Mobile trong B05.

### Đã hoàn thành

- Thêm tab **Báo cáo quá hạn & công nợ** trong `/admin/borrows`; tab **Danh sách phiếu mượn** giữ lọc ngày/trạng thái/tìm kiếm, chi tiết, mở quầy và từ chối giữ chỗ. Component báo cáo chỉ khởi tạo khi mở lần đầu, giữ form/bảng khi đổi tab.
- API client/models mới theo B04; bộ chọn khoa đọc facets từ Backend, mode OVERDUE/DEBT/EITHER, MSSV/tên, cỡ trang 10/25/50/100, preview phân trang server. Không dùng ngày tạo/trạng thái phiếu của tab cũ để giới hạn báo cáo sinh viên.
- Hiện tổng số sinh viên đúng bộ lọc, thời điểm chốt/timezone/VND, tiền nghĩa vụ/thanh toán-cọc/miễn/điều chỉnh/dư nợ do server trả. Không tính lại nợ ở frontend; ví null khác số 0, chênh ví-ledger hoặc chênh đối soát được chỉ rõ. Đổi nhãn aggregate totalLateFine cũ thành **Phí trễ lũy kế**, tránh gọi đó là nợ.
- Kiểm tra classification-coverage toàn bộ sinh viên riêng: hiển thị đã phân loại khi API xác nhận, nhóm thiếu department/số lượng khi còn lỗi, phân biệt rỗng với dữ liệu đủ. Không dựng khoa hoặc tuyên bố phân loại đủ khi request lỗi. Có retry metadata; khoa chưa tải được vẫn có thể xem mọi khoa.
- Chi tiết theo sinh viên gồm tài liệu còn quá hạn và các khoản ledger. Xem phiếu mở panel cũ, giữ nguyên báo cáo/bộ lọc; lỗi mở phiếu hiện được ở tab báo cáo. Có link quầy trả, USER ledger và PENALTY_CASE. Người đã trả vẫn nợ không bị loại khỏi mode DEBT.
- Export chỉ gửi mode/facultyId/q của báo cáo đã xem, không gửi page/size. Bộ lọc đang sửa phải áp dụng trước khi tải/chuyển trang. Khóa xuất lặp/bộ lọc khi đang gửi, nhận Blob+headers, tên file attachment (có xử lý UTF-8/tên dự phòng an toàn), anchor tạm và revoke URL sau tải. Lỗi JSON/Blob hoặc response sai MIME không được lưu thành Excel.
- Có loading/rỗng/error/retry riêng; preview lỗi xóa bảng cũ, export lỗi giữ kết quả để thử lại. Kiểm tra quyền/network, không hiển thị số 0 giả khi API hỏng. Bảng tiền cuộn trong khung, sticky header, bố cục nhỏ xếp các bộ lọc dọc.
- Hướng dẫn: [HUONG_DAN_B05_BAO_CAO_WEB.md](HUONG_DAN_B05_BAO_CAO_WEB.md). Đã cập nhật API B04, kế hoạch và PROJECT_CONTEXT.

### File thuộc B05

- Mới: `web-app/src/app/pages/admin/borrows/student-obligation-report.{ts,html,css,spec.ts}`.
- Mới: `web-app/src/app/services/student-obligation-report.models.ts`, `student-obligation-report.service.{ts,spec.ts}`, `report-file-saver.{ts,spec.ts}`.
- Sửa: `web-app/src/app/pages/admin/borrows/borrows.{ts,html,css,spec.ts}`. Phần diff B01/B03 trong các file này được giữ nguyên.
- Tài liệu: hướng dẫn B05 mới, API B04 (liên kết UI hiện có), checkpoint, kế hoạch và context. Không thêm dependency, sửa tiền/biểu phí hoặc triển khai schema.

### Kết quả kiểm tra mới nhất

| Kiểm tra ngày 29/10/2026 | Kết quả |
|---|---|
| Toàn bộ Web specs | **PASS 173/173**, đủ 45 file, không loại test |
| Web production build sau hoàn thiện UI | **PASS** |
| Edge headless với hồ sơ/API fixture cô lập | **PASS** desktop 1440px và Web hẹp 390px |
| Tải file qua UI và đọc lại bằng POI | **PASS**, đủ 26 sinh viên/3 sheet, preview chỉ 10 dòng |
| `git diff --check` | **PASS** |

Lệnh từ `web-app/`:

```powershell
npm.cmd test -- --watch=false --runner-config=../.codex-tmp/b01-vitest.config.mjs
npm.cmd run build
```

26 test tăng so với lượt B03: 4 API client, 4 filename/file saver, 15 UI báo cáo, 3 tích hợp tab/panel phiếu. Bao phủ query/phân trang khác export, bộ lọc đã xem/đang sửa/reset, link theo đúng ID, người đã trả còn nợ, ví chưa có/đối soát, rỗng/lỗi/quyền/retry, audit phân loại/mất metadata, duplicate download, Blob error/sai MIME, cleanup URL và giữ các thao tác phiếu. Build còn warning CSS budget portal home/history và CommonJS qrcode cũ; tests có warning sourcemap ZXing thiếu nguồn.

Browser QA dùng bản production, request GET đều bị chặn sang fixture: đổi mode DEBT/khoa FIT/từ khóa 2001/cỡ 10, xem trang 2/3, tải đúng filename từ header và không gửi page/size. Mở/đóng phiếu giữ bộ lọc, kết quả rỗng, lỗi 503/thử lại/reset đều đúng; không runtime error. Đã xem ảnh desktop và ba ảnh Web hẹp, kiểm tra main/page không bị cắt hoặc tràn ngang ngoài khung bảng.

File Excel fixture được tạo bằng **StudentObligationExcelExporter thật của B04**, không tự dựng workbook giả trong Web. Sau khi browser tải, POI mở lại ba sheet xác nhận đủ 26 sinh viên (không bị giới hạn trang 10 dòng), MSSV và từng cột nghĩa vụ/thanh toán-cọc/miễn/điều chỉnh/nợ khớp JSON API fixture; chi tiết overdue/ledger đủ số dòng. Script Java/Node, fixture, ảnh và log chỉ ở `.codex-tmp/b05-*` được Git ignore, không có fixture trong UI sản phẩm.

Đây là QA trình duyệt với API fixture, chưa nghiệm thu Web xuyên suốt DB thật đang dùng. Không chạy lại Maven suite vì B05 không sửa Backend; 51 Backend tests B04 là bằng chứng lịch sử riêng. Không restart/deploy hoặc cập nhật DB người dùng; môi trường chạy cần API B04 và migration 29–30. Server/profil trình duyệt kiểm tra tạm đã đóng.

### Điểm tiếp tục

**Dừng tại B05. B06–B16 chưa làm.** Khi người dùng gọi B06, đọc batch B06 rồi mở InventoryService/DTO nhận hàng, BookCopy/shelf và màn imports: thêm vị trí vào từng dòng nhập và lưu đúng trên bản sao. Không gộp tự nhận diện ISBN/quy tắc Admin của B07 vào B06. Giữ toàn bộ diff B01–B05 khi tiếp tục.

## B06–B07 ngày 29/10/2026 — checkpoint đã chốt

Trạng thái: **B06 HOÀN TẤT; B07 ĐÃ NHẬN DIỆN ISBN/PHÂN LOẠI, CHỜ QUY TẮC ADMIN; DỪNG TRƯỚC B08**. Người dùng xác nhận trực tiếp **Admin chưa làm phần quy tắc**. Baseline vẫn HEAD `34d942b` cùng diff B01–B06 và phần độc lập B07 chưa commit/push. Giữ toàn bộ các diff trước.

### B06 đã triển khai

- Backend `modules/inventory/`: DTO dòng nhập bắt buộc `shelfId`, optional `floorId`/`bookshelfId`; kiểm tra toàn bộ cây và đầu sách trước ghi. Khóa chia sẻ cây vị trí giữ tới cuối transaction, tương thích hai phiếu nhập đồng thời cùng ngăn. Lỗi bất kỳ dòng/copy nào rollback receipt/details/copies/notifications.
- Migration **31-import-copy-locations.yaml**: receipt_details.shelf_id và location_path, book_copies.receipt_detail_id, FK/index. Nullable cho lịch sử, không đoán/backfill phiếu cũ. Mọi copy mới nhận đúng shelf và dòng nguồn; đường dẫn trên dòng là snapshot lúc nhập.
- Staff `GET /inventory/receipts/{id}` trả các dòng, vị trí lúc nhập và barcode thực sự liên kết với dòng đó. Đổi tên tầng không sửa snapshot. Caller cũ thiếu shelfId nhận 400, cần cập nhật payload; không có ngăn mặc định.
- Web `pages/admin/imports/` và `services/imports.service.ts`: mỗi dòng tra catalog rồi chọn tầng/kệ/ngăn riêng; bỏ phản hồi lookup cũ, reset con khi đổi cha, chặn lưu thiếu vị trí, khóa form khi gửi và chống bấm gửi lặp. Sau lưu hoặc mở lịch sử xem lại đường dẫn/barcode. Không tạo bản sao khi hủy form trước gửi.

### B07 đã làm và phần chờ

- `InventoryCatalogController/CatalogIdentificationService/Isbn` cung cấp staff GET `/inventory/catalog-identification` với đúng một trong `isbn` hoặc `bookId`. Kiểm tra/chuẩn hóa ISBN-10/13 và checksum, tìm đúng ấn bản trong catalog; ISBN không có trả 404, nhiều đầu sách khớp trả 409 để chọn bookId rõ ràng. Nếu gửi đồng thời bookId/ISBN khi nhập thì phải khớp. BookId hỗ trợ dữ liệu catalog cũ thiếu ISBN hợp lệ.
- Hiển thị category/DDC/subject đang có với nguồn/evidence; không suy khoa/ngành từ trọng số recommender hoặc chữ số ISBN. Không gọi nguồn metadata ngoài. Luôn `placementStatus=WAITING_ADMIN_RULES`, `suggestedShelfId=null`; UI thông báo phải chọn thủ công.
- **Chưa có** engine, rule ưu tiên/hiệu lực/xung đột, kiểm tra rule thay đổi trước lưu, quyền/lý do override. B07 **chưa hoàn tất**, không tuyên bố đã tích hợp Admin. `Docs/API_B06_B07_NHAP_KHO_VI_TRI.md` có API thực tế và bản nháp hợp đồng để người làm Admin chốt (rule ID/version, điều kiện, ngăn, ưu tiên/hiệu lực, quyền đổi vị trí và audit). Chỉ nối engine khi có hợp đồng chính thức; phải kiểm tra server trên cả đường nhập bookId.

### Kiểm chứng mới ngày 29/10/2026

- **Backend: 56/56 PASS**, 0 failure/error/skip. `CirculationMigrationIntegrationTest` 7, `StudentObligationReportIntegrationTest` 14, `Week6IntegrationTest` 29, `BookCopyServiceTest` 2, `LocationServiceTest` 1, `InventoryServiceTest` 1, `IsbnTest` 2. PostgreSQL Testcontainers thật xác minh migration/FK, nhiều dòng/ngăn/copy, đường dẫn sau đổi tên, lỗi dòng cuối rollback, hai phiếu cùng ngăn không chặn nhau, ISBN-10/13/979/ấn bản/mơ hồ và HTTP ADMIN/LIBRARIAN/STUDENT.
- Lệnh trong `backend` (JAVA_HOME JDK 21 hiện có): `./mvnw.cmd --batch-mode -Dtest=InventoryServiceTest,IsbnTest,LocationServiceTest,BookCopyServiceTest,CirculationMigrationIntegrationTest,Week6IntegrationTest,StudentObligationReportIntegrationTest test`. Log `.codex-tmp/b06-backend-tests.log` chốt BUILD SUCCESS lúc 18:08:53 +07.
- Focused test master nay include migration 05 (cây vị trí) với prerequisite publisher chỉ dành cho test và include 31. Không kéo migration 04 có CSV lớn/reset seed vào focused suite; không sửa migration production cũ. Đã chạy lại cả report integration vì cùng dùng test master.
- **Web: 186/186 PASS, 46 test files; production build PASS.** Lệnh trong `web-app`: `npm.cmd test -- --watch=false --runner-config=../.codex-tmp/b01-vitest.config.mjs`, `npm.cmd run build`. Có thêm 12 component test và 1 service test so với B05. Cảnh báo CSS home/history và qrcode CommonJS hiện có không chặn build.
- Browser Edge cô lập, production bundle và API fixture: desktop 1440/mobile 390, hai dòng ISBN/bookId khác ngăn, chỉ một POST đúng payload, không tự suy shelf, mở lại phiếu sau reload giữ đường dẫn/barcode từ GET fixture, không tràn drawer hoặc lỗi runtime. Script/log/ảnh `.codex-tmp/b06-ui-check.cjs`, `b06-ui-check.log`, `b06-import-desktop.png`, `b06-import-mobile.png`, `b06-receipt-mobile.png`. Đã xem ảnh desktop/mobile. Đây là browser fixture **tách biệt** với Backend DB tests, không phải nghiệm thu Web-to-live-DB.
- `git diff --check` PASS. `PROJECT_CONTEXT.md` và bảng trạng thái kế hoạch đã cập nhật. Docker Desktop được khởi động để chạy Testcontainers; không chạy Compose/restart backend, không áp migration lên DB đang dùng, không sửa Flutter.

### Tiếp tục từ đây

**Dừng trước B08; B08–B16 chưa làm.** Khi Admin có schema/API quy tắc, đối chiếu bản nháp rồi hoàn thiện engine và các case còn thiếu của B07 trước khi đánh dấu hoàn tất. B06 vẫn nhập vị trí thủ công. Chưa có import idempotency key: nếu mất kết nối sau gửi phải đối chiếu phiếu trước khi lập lại. Chưa triển khai thay đổi lên môi trường đang chạy; migration 31 cần được áp cùng backend/client mới trong lượt triển khai được yêu cầu.

## B08 ngày 29/10/2026 — hoàn tất, dừng trước B09

Trạng thái: **B08 HOÀN TẤT**. Baseline vẫn HEAD `34d942b` cùng toàn bộ diff B01–B06, B07 độc lập và B08 chưa commit/push. B07 tự đề xuất ngăn vẫn **CHỜ ADMIN**, không thay đổi trong lượt này. B09–B16 chưa làm.

### Đã triển khai

- Thêm `ProposalSummaryReport`, `ProposalSummaryRepository`, `ProposalSummaryService`, `ProposalSummaryExcelExporter`, `ProposalSummaryController` trong `modules/reporting/`. Staff GET `/proposals/admin/summary`, `/summary/export`, `/summary/faculties` (hai path sau nối vào `/proposals/admin`) đều bảo vệ ADMIN/LIBRARIAN, no-store.
- Lọc khoa/ngày/trạng thái trước nhóm. ISBN-10/13 tương đương gộp, ISBN khác không gộp theo tên; cùng ISBN khác ấn bản/năm rõ ràng tách và cảnh báo. Thiếu ISBN dùng tên/tác giả/NXB/ấn bản/năm đã chuẩn hóa Unicode/case/whitespace, giữ ký hiệu như C++/C#. Thiếu định danh hoặc ISBN sai giữ riêng để kiểm tra. Nguồn gốc luôn còn nguyên.
- Mỗi nhóm có lượt, người gửi khác nhau theo userId, phân bố khoa và nguồn chi tiết. Người gửi lặp chỉ tăng lượt. Khoa lấy từ mapping/hồ sơ hiện tại B04; dữ liệu không có khoa thật hiển thị cần bổ sung, không bịa mapping. Chỉ mở rộng visibility helper chuẩn hóa department của repository B04 sang package scope để dùng chung; không đổi thuật toán B04.
- Preview phân trang nhóm; XLSX xuất tất cả nhóm/nguồn theo cùng bộ lọc, ba sheet **Tong hop / Theo khoa / De xuat goc**. ID/ISBN/MSSV/chuỗi nguồn luôn là STRING, không tạo công thức/hyperlink. Có header, filter/snapshot, freeze pane, auto-filter, chia sheet nếu quá giới hạn dòng. Số lượt không được diễn giải là số bản cần mua.
- Web `suggestions/proposal-summary.{ts,html,css,spec.ts}` và `services/proposal-summary.service.{ts,spec.ts}`: tab tải lười **Tổng hợp / Excel**, bộ lọc riêng, phân trang, cảnh báo cần áp dụng lại trước xuất, mở đúng mã đề xuất trong drawer cũ. Giữ nguyên duyệt/từ chối từng đề xuất. `ReportFileSaver` có helper `xlsxFilename` dùng chung với B05; downloader vẫn kiểm MIME/Blob và lỗi.
- Không migration production mới, không ghi/xóa/gộp proposal nguồn, không duyệt hàng loạt hay gửi file/email. Hướng dẫn/API/semantics đầy đủ: `Docs/API_B08_TONG_HOP_DE_XUAT_MUA.md`.

### Kiểm chứng mới

- **57/57 Backend PASS**, 0 failure/error/skip: `ProposalSummaryIntegrationTest` 7, `StudentObligationReportIntegrationTest` 14, `Week6IntegrationTest` 30, `BookProposalServiceTest` 2, `BookProposalDtoValidationTest` 3, `BookProposalAuthorizationContractTest` 1. Testcontainers PostgreSQL thật; Week6 dùng thêm Redis/Mailpit cô lập. HTTP chứng minh staff/student/không đăng nhập, bộ lọc, XLSX không nhận giới hạn preview, CORS Content-Disposition và không đổi trạng thái proposal.
- Lệnh trong `backend`, JAVA_HOME `C:\Program Files\Java\jdk-21`: `./mvnw.cmd --batch-mode -Dtest=ProposalSummaryIntegrationTest,BookProposalServiceTest,BookProposalDtoValidationTest,BookProposalAuthorizationContractTest,Week6IntegrationTest,StudentObligationReportIntegrationTest test`. Cần quyền truy cập Docker pipe ngoài sandbox. Log cuối `.codex-tmp/b08-backend-tests.log`: BUILD SUCCESS. Focused test master mới `proposal-summary-test-master.yaml` include circulation test master + migration 12/23; đã bổ sung đủ dependency thay vì sửa migration cũ hoặc import CSV lớn.
- **199/199 Web PASS**, 48 files; `npm.cmd test -- --watch=false --runner-config=../.codex-tmp/b01-vitest.config.mjs`; production `npm.cmd run build` PASS. Logs `.codex-tmp/b08-web-tests.log`, `b08-web-build.log`. Cảnh báo cũ home/history CSS và qrcode CommonJS còn; không có cảnh báo budget mới cho B08.
- Browser Edge cô lập trên production bundle, API fixture tạo bằng **grouping và POI exporter thật**: 26 nhóm/28 lượt/2 người báo cáo chung, lọc FIT còn 26 nhóm/27 lượt/1 người, preview 10 dòng. Kiểm bộ lọc khoa/ngày/trạng thái, phân trang, mở mã nguồn trong drawer, empty state, mobile 390/desktop 1440, không mutation/lỗi runtime. File tải được mở lại bằng POI: đủ 26 nhóm và 27 nguồn, key/tên/lượt/người khớp fixture, ba sheet, không formula cell. Đây là browser fixture + DB test riêng biệt, chưa phải Web-to-live-DB E2E.
- Script/ảnh/log/fixture ignored: `.codex-tmp/b08-ui-check.cjs`, `b08-ui-check.log`, `B08ReportFixture.java`, `b08-api-report.json`, `b08-report-downloaded.xlsx`, `b08-summary-desktop.png`, `b08-summary-mobile-filters.png`, `b08-summary-mobile-table.png`. Đã xem các ảnh. File XLSX này là **dữ liệu kiểm thử**, không dùng gửi khoa.
- `git diff --check` PASS; đường dẫn/tài liệu context đã kiểm tra và cập nhật. Không restart backend, không chạy Compose/áp migration DB đang dùng, không sửa Flutter, không commit/push.

### Giới hạn và bước tiếp theo

Preview/export là snapshot riêng; thay đổi đề xuất giữa hai lần có thể làm file khác preview. Khoa là hồ sơ hiện tại, không phục dựng khoa lúc gửi. Gộp đọc toàn bộ nguồn phù hợp vào bộ nhớ rồi phân trang nhóm; chưa nghiệm thu tải dữ liệu rất lớn. Metadata thiếu/mâu thuẫn cần đối chiếu thủ công, không tự đoán ấn bản hay khoa.

**Dừng trước B09.** Nếu người dùng gọi B09, đọc đúng batch và mở inventory audit/copy/circulation để thiết kế hồ sơ đề xuất xử lý sách cũ riêng với book_proposals. Không tự coi quy tắc Admin của B07 hoặc quyết định thanh lý đã có. Giữ toàn bộ working-tree diff hiện tại.

## B09–B10 ngày 29/10/2026 — xong phần thủ thư, dừng trước B11

Trạng thái: **B09–B10 HOÀN TẤT PHẦN THỦ THƯ; QUYẾT ĐỊNH/THỰC THI CHỜ ADMIN**. Người dùng xác nhận trực tiếp **“Admin chưa làm, ghi rõ phần chờ”**. Không dựng kết quả duyệt/thanh lý giả. B07 vẫn chờ quy tắc xếp sách Admin. Baseline HEAD vẫn `34d942b` cộng các diff B01–B10 đang có; chưa commit/push, giữ nguyên diff trước lượt này.

### Backend B09

- Module inventory mới: `DisposalController`, `DisposalService`, `dto/DisposalModels`, ba entity/repository DisposalBatch/Proposal/History. Không dùng `book_proposals` cho hồ sơ xử lý tồn kho.
- Migration **32-disposal-proposals.yaml** tạo `disposal_batches`, `disposal_proposals`, `disposal_history`, FK bảo vệ nguồn/book/copy, CHECK trạng thái/lý do/phương án và partial unique một DRAFT/SUBMITTED cho mỗi barcode. Rollback chỉ bỏ ba bảng mới. Không sửa migration cũ, không áp lên DB đang dùng.
- Staff API `/inventory/disposals`: candidates theo auditId, lập lô nháp 1–50 bản, list lọc/phân trang, GET/PUT chi tiết, submit/cancel. Mọi API bảo vệ ADMIN/LIBRARIAN, no-store; chỉ người lập sửa/gửi/hủy hồ sơ của mình. Actor lấy từ principal.
- Nguồn bắt buộc audit hoàn tất + scan nhận diện thật; kiểm copy vật lý có book, status hợp lệ, hold và phiếu giữ/mượn đang mở. Fingerprint phát hiện source/copy đổi sau preview. Lock nguồn chia sẻ, copy theo barcode, proposal theo ID và @Version cho mutation. Toàn lô rollback nếu bất kỳ dòng/history lỗi.
- Request UUID/owner/body được lưu với advisory lock PostgreSQL; retry cùng key/body trả cùng IDs không lập trùng. DRAFT sửa được và lưu lại snapshot nguồn hiện tại sau đối chiếu; SUBMITTED chỉ theo dõi/hủy trong phạm vi hiện tại, CANCELLED giữ lịch sử. History có UUID, version, actor/time, before/after gồm nguồn và nội dung.
- Tất cả thao tác đề xuất **không đổi copy status/shelf/hold, không xóa copy/lịch sử**. Chưa có endpoint quyết định, thực thi hay bảng event thực tế; GET detail luôn `WAITING_ADMIN_WORKFLOW`. B13 không được suy số đã thanh lý/hủy từ đề xuất.

### Web B10

- Tích hợp tab tải lười **Hồ sơ xử lý sách cũ** tại `/admin/inventory-audit` và nút **Lập đề xuất xử lý từ phiên này** sau hoàn tất. Child `pages/admin/inventory-audit/disposal-workspace.{ts,html,css,spec.ts}`, client `services/disposal.service.{ts,spec.ts}`.
- Dùng candidates thật, chọn nhiều bản sao, per-row tình trạng/lý do/phương án/URL chứng cứ; barcode không hợp lệ hiện blocker và không cho chọn. Evidence mặc định URL HTTP(S), không upload tệp/gọi tải URL tại server. Hủy form chưa gửi không gọi mutation; chỉ bấm lập nháp mới POST.
- List lọc trạng thái/phiên/barcode chính xác, phân trang server; detail mở lại từ API với snapshot/current blockers/owner permissions và history. Thay đổi chưa lưu chặn nút gửi; source thay đổi cần lưu/xác nhận lại nháp. UI nói rõ gửi không phải đã xử lý thực tế.
- Lập nháp gặp 0/5xx giữ UUID/body trong bộ nhớ và sessionStorage theo user, khóa sửa để retry nguyên yêu cầu; reload không tự POST. Lỗi xác định giữ form để đối chiếu nguồn. Sửa/gửi/hủy gặp lỗi khóa tiếp cho tới khi GET chi tiết lại. Không đổi route hoặc Flutter.

### Kiểm chứng mới

- **53/53 Backend PASS, 0 failure/error/skip**: DisposalWorkflowIntegrationTest 10, DisposalMigrationIntegrationTest 3, Week6IntegrationTest 30, InventoryAuditServiceTest 7, BookCopyServiceTest 2, LocationServiceTest 1. Workflow Spring/HTTP trên PostgreSQL/Redis Testcontainers với schema Hibernate create-drop; migration riêng dùng Liquibase kiểm schema/FK/partial index và rollback/reapply trong schema cô lập. Có ca hai request cùng barcode, hai retry cùng key, nguồn đổi, active loan nhưng copy AVAILABLE, sai quyền/version/URL và lỗi history cuối rollback nguyên lô. Hồi quy Week6 vẫn PASS.
- Lệnh trong `backend` (JAVA_HOME JDK 21 hiện có, cần Docker pipe ngoài sandbox): `./mvnw.cmd --batch-mode -Dtest=DisposalWorkflowIntegrationTest,DisposalMigrationIntegrationTest,Week6IntegrationTest,InventoryAuditServiceTest,BookCopyServiceTest,LocationServiceTest test`. Log cuối `.codex-tmp/b09-backend-tests.log` BUILD SUCCESS lúc **18:51:43 +07**. Focused `disposal-test-master.yaml` include circulation test master + migration 32. Lỗi ghi file class tạm ở lần compile đầu và import Container trùng trong test đã xử lý; đây là kết quả lượt chạy cuối.
- **215/215 Web PASS, 50 files**, gồm 16 test mới (12 workspace, 3 service, 1 parent). `npm.cmd test -- --watch=false --runner-config=../.codex-tmp/b01-vitest.config.mjs`; `npm.cmd run build` PASS. Logs `.codex-tmp/b10-web-tests.log`, `b10-web-build.log`. Chỉ còn cảnh báo cũ home/history CSS, qrcode CommonJS và sourcemap dependency trong test; không có budget warning mới cho B10.
- **Browser fixture Edge PASS**: production bundle, desktop 1440/mobile 390, hai bản/khác phương án, copy đang mượn bị khóa, hủy form trước gửi, giả mất phản hồi sau tạo → reload không tự POST → retry cùng body/UUID không tăng số hồ sơ, sửa nháp/gửi/hủy, GET lại giữ nội dung/history, không mutation copy/lỗi runtime. Đã xem ảnh desktop/mobile. Browser dùng fixture cô lập; kiểm Backend DB riêng, chưa là nghiệm thu Web-to-live-DB.
- Script/log/ảnh ignored: `.codex-tmp/b10-ui-check.cjs`, `b10-ui-check.log`, `b10-draft-desktop.png`, `b10-draft-mobile.png`, `b10-detail-desktop.png`, `b10-detail-mobile.png`. `git diff --check` PASS; context/kế hoạch/API handoff đã cập nhật. Không restart backend/chạy Compose, không thay DB người dùng, không commit/push.

### Phần chờ và tiếp tục

Contract, thao tác và giới hạn: **`Docs/API_B09_B10_XU_LY_SACH_CU.md`**. Admin còn phải chốt vòng đời/quyền/quyết định, bổ sung schema/adapter và bước thực thi. Mở rộng trạng thái phải cập nhật CHECK và partial-index predicate bằng migration mới. Sự kiện thực thi cần event ID/ngày hiệu lực/người chịu trách nhiệm/liên kết quyết định và kiểm lại loan/hold; chưa xây trong lượt này vì chưa có hợp đồng quyền. Không tính SUBMITTED hoặc CANCELLED là kết quả thanh lý/hủy cho B13.

Giới hạn còn lại: nguồn URL không được server xác minh nội dung; candidate/history đọc toàn tập tương ứng, nguồn mutation khóa scan của phiên trước khi lọc dòng chọn, chưa thử tải phiên rất lớn. SessionStorage không dùng được thì retry chỉ còn trong bộ nhớ. Các đề xuất không giữ copy khỏi circulation; phải kiểm lại khi thực thi thực tế sau này.

**Dừng trước B11; B11–B16 chưa làm.** Lượt sau đọc batch được gọi và mở dashboard/circulation/reporting theo context. Không tự làm phần Admin đang chờ hoặc dùng hồ sơ đề xuất làm dữ liệu kết quả thực thi. Giữ toàn bộ working-tree diff hiện tại.

## B11–B12 ngày 29/10/2026 — Backend hoàn tất, dừng trước B13

Trạng thái: **B11–B12 HOÀN TẤT BACKEND**. Người dùng đã yêu cầu tạm dừng/tắt máy rồi gọi tiếp B11–B12; code và log trước khi tắt máy còn nguyên, checkpoint cũ chưa kịp ghi nên lượt tiếp tục này đã đối chiếu source và hoàn thiện bàn giao. Baseline vẫn HEAD `34d942b` cộng toàn bộ working-tree diff B01–B12; không commit/push. Giữ nguyên các thay đổi trước lượt này.

Người dùng chốt trực tiếp **“Cho thủ thư gán ngành, có nguồn và lịch sử”**. Đã triển khai trong B12; không chờ Admin cho quyền gán ngành này. B07 vẫn chờ quy tắc xếp sách; B09–B10 vẫn chờ quyết định/thực thi Admin.

### B11 đã triển khai

- `modules/dashboard/`: CirculationAnalyticsController/Service/Repository và DTO `CirculationAnalytics`; staff GET `/dashboard/analytics/circulation`, `/rankings`, `/overdue`, no-store. Tổng hợp/phân trang tại PostgreSQL, mỗi GET read-only REPEATABLE_READ.
- Timestamp nghiệp vụ mới trên LoanDetailEntity; migration **33-circulation-business-timestamps.yaml** thêm checkout/physical-return/time-source/circulationBookId và hai partial timestamp index. Backfill chỉ dựa chứng cứ BORROWING history/dòng đã tồn tại và normal return không có penalty. Ngày không biết giữ null, không suy từ ngày đặt/hạn trả.
- Checkout thường/thủ công ghi thời điểm giao và bookId. Normal return ghi nhận vật lý; ReturnInspection DAMAGED có nhận tại quầy ghi physicalReturnAt; LOST hoặc tất toán case không biến thành lượt nhận vật lý. Giữ replay và atomic transaction cũ.
- Phân biệt items và distinct tickets, giao/nhận trong kỳ riêng; MONTH/QUARTER/YEAR lấp 0, cắt bucket đầu/cuối, ngày đầu gồm/cuối loại trừ, timezone Asia/Ho_Chi_Minh. Tổng ticket kỳ không cộng từ từng bucket.
- MOST_BORROWED, LEAST_BORROWED (chỉ có lượt), NEVER_RECORDED (toàn lịch sử còn lưu), NO_RECORDED_IN_PERIOD, CURRENTLY_BORROWED (hiện tại); đồng hạng ổn định, đầu sách vật lý 0 bản vẫn có, loại đầu thuần số. Coverage nêu rõ ngày cũ thiếu và lượt trong kỳ không còn catalog quy thuộc.
- Quá hạn STUDENT hiện tại, loại reservation/sự cố/dòng đã dừng phạt, có liên kết phiếu/quầy và B04. Không dựng snapshot quá hạn lịch sử.
- Dashboard cũ giữ DTO: weekly đếm giao sách thật thay tạo reservation, trending loại pending reservation, borrowedCurrent nhận BORROWING/BORROWED/OVERDUE, overdueBooks cùng quy tắc STUDENT. `fineCollected` cũ chưa sửa nghĩa, thuộc việc rà B13.

### B12 đã triển khai

- HoldingsAnalyticsController/Service/Repository, DTO `HoldingsAnalytics`, MaterialClassificationService và bốn entity scalar trong reporting. Migration **34-academic-material-classification.yaml** tạo academic_majors, profiles, assignments và history. Catalog ngành là snapshot mapping B04 khi migrate; không seed ánh xạ sách và không dùng recommender.
- Staff GET academic-catalog, holdings, materials; drill-down copies/digital-assets/classification theo BOOK hoặc DIGITAL độc lập. BOOK có file liên kết chỉ là một đầu, file cộng vào digitalFiles và kế thừa phân loại BOOK; file độc lập là một đầu DIGITAL.
- Tổng distinct, nhóm khoa/ngành chồng lấp, trong một khoa không nhân đôi đầu nhiều ngành. Tách head/copy/file; type chính xác PHYSICAL/DIGITAL/HYBRID; state lọc đầu có bản phù hợp rồi trả toàn bộ stock của đầu đó. Có NO_COPIES/NO_DIGITAL_FILE/UNCLASSIFIED.
- Bucket copy rời nhau gồm available/borrowed/reserved/lost/damaged/maintenance/retired/other; AVAILABLE có hold còn hiệu lực thuộc reserved. Vị trí tầng/kệ/ngăn và download API hiện có, không lộ đường dẫn file nội bộ.
- PUT classification nhận expected version, các majorId hợp lệ/không trùng và nguồn bắt buộc. Lưu actor từ principal + nguồn + before/after history, khóa advisory/root material, toàn bộ thay đổi atomic. Version cũ xung đột 409; retry cùng body sau thành công không tạo event trùng. Bỏ mapping vẫn cần nguồn/history.
- Audit cố ý không FK đến upstream catalog để giữ sau xóa; current API chỉ làm việc với nguồn tồn tại. File độc lập được gắn BOOK sau đó ngừng đếm profile riêng; không tự chuyển mapping cũ sang BOOK.

### Kiểm chứng

- Lượt trước khi tắt máy: **74/74 Backend PASS, 0 failure/error/skip**, BUILD SUCCESS lúc **19:48:46 +07 ngày 29/10/2026**, log `.codex-tmp/b11-b12-backend-tests.log` còn nguyên.
- Bộ chọn gồm DashboardAnalyticsIntegrationTest 15, DashboardAnalyticsMigrationIntegrationTest 3, Week6IntegrationTest 31, CirculationStateServiceTest 13, CirculationTransactionServiceTest 5, ReturnInspectionServiceTest 4, DashboardServiceTest 3. PostgreSQL/Redis/Mailpit Testcontainers cô lập; không dùng DB người dùng.
- Test mới kiểm items/tickets, reservation/cancel, trả từng phần/khác tháng, ranh giới quý/năm/bucket rỗng, tie/0 bản/thiếu ngày, overdue, distinct nhiều ngành/khoa/file, trạng thái/vị trí/pagination, source/history/version/replay, cạnh tranh cùng version và rollback history lỗi. HTTP/JWT chứng minh ADMIN/LIBRARIAN được phép, STUDENT/không đăng nhập bị chặn. Liquibase kiểm backfill/FK/rollback/reapply; EXPLAIN trên 10.000 dòng chứng minh dùng index checkout với khoảng chọn lọc.
- Lệnh tại `backend`, JAVA_HOME `C:\Program Files\Java\jdk-21`: `./mvnw.cmd --batch-mode -Dtest=DashboardAnalyticsIntegrationTest,DashboardAnalyticsMigrationIntegrationTest,DashboardServiceTest,Week6IntegrationTest,CirculationStateServiceTest,CirculationTransactionServiceTest,ReturnInspectionServiceTest test`.
- Khi tiếp tục sau bật máy, probe đầu không kết nối Docker vì engine chưa chạy (`.codex-tmp/b11-b12-resume-tests.log`); đã khởi động Docker Desktop để chạy lại bộ chọn. Kết quả lượt cuối được ghi bên dưới, không dùng probe lỗi làm kết quả nghiệm thu.
- **Lượt chạy cuối sau tiếp tục: 74/74 Backend PASS, 0 failure/error/skip, BUILD SUCCESS lúc 23:43:09 +07 ngày 29/10/2026**, log `.codex-tmp/b11-b12-resume-final-tests.log`. Docker engine đã sẵn sàng; cả DB/migration/HTTP/concurrency/rollback và hồi quy circulation trong bộ chọn đều chạy thành công. Không sửa thêm code nghiệp vụ sau lượt này.
- `git diff --check` PASS, các đường dẫn tài liệu/migration/test master bàn giao tồn tại. Chỉ có cảnh báo LF→CRLF từ file imports.ts của batch trước.
- Không đổi Web/Flutter trong B11–B12, không chạy lại Web/build/browser. Số 215 Web của checkpoint B09–B10 là lịch sử, không phải kiểm chứng mới cho API này.

### Bàn giao và giới hạn

Contract/tham số/DTO, cách đếm, request gán ngành, quy tắc dữ liệu cũ và nghiệm thu: **`Docs/API_B11_B12_DASHBOARD.md`**. `PROJECT_CONTEXT.md` và bảng batch đã cập nhật migration/module/API mới.

Không phục dựng thời điểm lịch sử thiếu chứng cứ; coverage là bắt buộc khi diễn giải xu hướng. `retiredCopies` chỉ là nhóm status hiện tại, không phải event thanh lý/hủy; chưa có vòng đời ngừng khai thác riêng cho tài liệu số. Admin quyết định/thực thi chưa tồn tại. Catalog ngành B04 đổi sau migration 34 chưa tự đồng bộ; chưa có API quản trị catalog. SourceReference là nguồn do thủ thư khai báo, server không xác minh nội dung. Audit nguồn đã xóa giữ trong DB nhưng chưa có màn/API tra cứu riêng. SQL đã tổng hợp/phân trang nhưng chưa load-test production lớn.

Không restart backend/chạy Compose/áp migration lên DB người dùng, không commit/push. **Dừng trước B13; B13–B16 chưa làm.** Khi người dùng gọi B13, đọc batch và phần tài chính/nhập kho, phân biệt charge/thu thực tế và chỉ dùng sự kiện xử lý sách cũ có thẩm quyền; không đếm SUBMITTED/CANCELLED hay raw retired status thành kết quả thực thi. Web mới B14–B15 dùng contract B11–B12, không tự mở rộng ngay.

## B13 ngày 29/10/2026 — xong tài chính/nhập, thanh lý/hủy chờ Admin

Trạng thái: **B13 HOÀN TẤT TÀI CHÍNH/NHẬP; THỐNG KÊ THỰC THI THANH LÝ/HỦY CHỜ ADMIN**. Không đánh dấu đã nghiệm thu số liệu thực thi khi chưa có schema/API/event có thẩm quyền. Người dùng đã xác nhận phần chờ này ở B09–B10. Baseline vẫn HEAD `34d942b`, toàn bộ diff B01–B13 chưa commit/push được giữ nguyên. B07 vẫn chờ quy tắc xếp sách. B14–B16 chưa làm.

### Đã triển khai

- Thêm `FinanceStockAnalyticsController/Service`, `FinanceAnalyticsRepository`, `ImportAnalyticsRepository`, DTO `FinanceStockAnalytics` trong `modules/dashboard/`. Staff GET `/dashboard/analytics/finance`, `/finance/entries`, `/finance/payments/{id}/allocations`, `/stock-movements`, `/stock-movements/receipts`. ADMIN/LIBRARIAN, no-store, GET read-only REPEATABLE_READ, tổng hợp/phân trang tại PostgreSQL.
- Tiền phát sinh đọc cả FINE/REPAIR_FEE/COMPENSATION/TECHNICAL_FEE theo createdAt. Thu tiền đọc PAYMENT âm COMPLETED theo paidAt của receipt, tách CASH/BANK_TRANSFER/VNPAY/OTHER; receipt không nhân theo số allocation. Allocations có nguồn charge hợp lệ/cùng user được tổng hợp trước join; thiếu/lặp/sai nguồn hiện discrepancy/invalid rows, không tự sửa ledger.
- Cấn cọc được chụp riêng khi phát sinh charge; miễn/điều chỉnh COMPLETED theo ngày tạo, giữ dấu để thể hiện điều chỉnh ngược. Nạp cọc riêng, không cộng vào thu phạt. Không dùng paidAmount charge hoặc lateFineAccrued/PenaltyCase totals làm tiền mới thu.
- Debt trả ledgerOutstanding/walletDebt/chênh lệch và satisfiedIncludingReductions **hiện tại**, không theo kỳ; historicalDebtAvailable=false. Không có số dư cuối kỳ lịch sử. Coverage phản ánh ngày thiếu, nguồn cọc không rõ, receipt/allocation lệch.
- Khoảng ngày đầu gồm/cuối loại trừ, timezone Asia/Ho_Chi_Minh, MONTH/QUARTER/YEAR lấp 0/cắt bucket đầu cuối; dùng range validation B11. Tổng và bucket tính riêng; importedTitles distinct không cộng được từ từng tháng.
- Nhập theo COMPLETED + completedAt và receipt_details.quantity hợp lệ, không lấy Book.createdAt/copy hiện còn. Tách phiếu, đầu distinct trong kỳ, đầu lần nhập đầu tiên ghi nhận và số bản. Có phiếu COMPLETED cũ thiếu ngày của một đầu thì không tự kết luận lần nhập đầu của đầu đó. Drill-down phiếu có link API chi tiết B06. Copy sau này bị mất/xóa không giảm số lượng nhập lịch sử.
- Thanh lý/hủy trả `WAITING_ADMIN_WORKFLOW`, reason, totals/buckets liquidatedCopies/destroyedCopies **null**. Không đếm proposal DRAFT/SUBMITTED/CANCELLED hoặc copy RETIRED thành event thực thi, không thêm quyền/luồng quyết định Admin.
- Dashboard cũ giữ DTO nhưng sửa `fineCollected`: tiền PAYMENT thực nhận lũy kế có paidAt tới hiện tại, không còn cộng FINE charge. B15 cần giải thích lại card/đơn vị; không đổi UI trong B13.

### Schema và writer

- Migration **35-finance-import-analytics-evidence.yaml** thêm nullable transactions.deposit_applied_amount/deposit_applied_at, goods_receipts.completed_at. Backfill cọc bằng 0 chỉ khi charge paidAmount=0; positive/null cũ giữ unknown. Phiếu cũ COMPLETED không có event hoàn tất giữ completedAt=null. Rollback bỏ ba cột mới, giữ dữ liệu nguồn; không sửa migration cũ.
- `TransactionEntity` thêm hai field cọc. `LateFineService`/`PenaltyCaseService` lưu amount (kể cả 0)/thời điểm cấn một lần lúc tạo charge. Các lần thu tiền sau vẫn sửa paymentMethod/paidAt cũ nhưng không sửa evidence mới. Luồng tài chính, replay và atomic transaction giữ nguyên.
- `GoodsReceiptEntity` thêm completedAt; `InventoryService` lưu sau toàn bộ dòng/bản sao được tạo, cùng transaction. Lỗi giữa chừng không có hoàn tất/phiếu/copy từng phần. Không thêm index nếu chưa có query-plan chứng minh cần thiết.

### Kiểm chứng lượt cuối

- **71/71 Backend PASS, 0 failure/error/skip**, BUILD SUCCESS **23:55:47 +07 ngày 29/10/2026**, log ignored `.codex-tmp/b13-backend-final-tests.log`.
- Breakdown: FinanceStockAnalyticsIntegrationTest 10, FinanceStockAnalyticsMigrationIntegrationTest 2, DashboardAnalyticsIntegrationTest 15, Week6IntegrationTest 32, DashboardServiceTest 3, DebtLedgerServiceTest 2, LateFineServiceTest 3, PenaltyCaseServiceTest 3, InventoryServiceTest 1.
- DB thật Testcontainers kiểm phạt 200.000/cọc 50.000/nợ 150.000/thu hai đợt, cọc đủ, topup riêng, bốn phí, miễn giảm/điều chỉnh có dấu, nhiều allocation không nhân receipt, dedup receipt, allocation lệch/sai nguồn, ranh giới kỳ/bucket rỗng, dữ liệu cũ thiếu nguồn, nhập lặp đầu/nhiều dòng/ngoài kỳ/trạng thái chưa hoàn tất/copy bị xóa và phần thực thi unavailable. Migration kiểm không đoán chứng cứ cũ, rollback/reapply giữ nguồn.
- Week6 có luồng nghiệp vụ thật: ReturnInspection tạo phí sửa 200.000, cấn 50.000, DebtLedger thu 60.000 + 90.000 và replay cả hai, báo cáo vẫn chỉ 150.000 receipt/50.000 cọc; evidence cọc không đổi dù paymentMethod chuyển BANK_TRANSFER. Import thật có completedAt. HTTP kiểm role/no-store, filters sai, trạng thái thanh lý unavailable; B11–B12 hồi quy trong bộ chọn vẫn PASS.
- Lệnh tại `backend`, JAVA_HOME `C:\Program Files\Java\jdk-21`: `./mvnw.cmd --batch-mode -Dtest=FinanceStockAnalyticsIntegrationTest,FinanceStockAnalyticsMigrationIntegrationTest,DashboardAnalyticsIntegrationTest,DashboardServiceTest,Week6IntegrationTest,DebtLedgerServiceTest,LateFineServiceTest,PenaltyCaseServiceTest,InventoryServiceTest test`. Docker pipe ngoài sandbox, PostgreSQL/Redis/Mailpit cô lập, không DB người dùng. Focused master mới `finance-stock-analytics-test-master.yaml` include dashboard master +32/+35.
- Lượt probe đầu thiếu user fixture nên FK từ chối 7 test mới; đã thêm user nguồn, không nới FK/migration. Kết quả 71 PASS trên là lượt cuối sau sửa fixture và chốt thời điểm hoàn tất nhập. `git diff --check` PASS; chỉ warning LF→CRLF của imports.ts từ batch trước. Không chạy Web/build/browser vì không đổi Web; kết quả Web các batch trước là lịch sử.

### Giới hạn và bước tiếp theo

API/ý nghĩa tiền, nguồn thời điểm, ví dụ/đối chiếu và hợp đồng chờ Admin: **`Docs/API_B13_TAI_CHINH_NHAP_XU_LY.md`**. Context/kế hoạch/B11–B12 handoff đã cập nhật contract hiện tại.

Thiếu thời điểm cũ/nguồn cọc giữ unknown; không phục dựng closing debt. First recorded import không chứng minh đầu sách mới tuyệt đối nếu có tồn trước hệ thống. Refund chưa có contract chỉ tiêu dòng tiền ròng; không tự bù vào moneyReceived. Chưa load-test production lớn; SQL có thể quét toàn nguồn. Số thực thi thanh lý/hủy chỉ làm tiếp sau contract/event Admin; cần event ID/ngày hiệu lực/barcode-book snapshot/actor/quyết định và test trùng/ngoài kỳ/lịch sử. Chưa có API tạo điều chỉnh đảo mới.

Không deploy/restart backend/Compose/áp migration vào DB đang dùng, không commit/push, không sửa Flutter. **Dừng trước B14.** Lượt sau B14 dùng CirculationAnalytics API B11 cho Web; B15 mới ghép holdings/finance/import, hiển thị coverage/debt hiện tại và thanh lý unavailable đúng nghĩa. Giữ toàn bộ working-tree diff.

## B14–B16 ngày 30/10/2026 — UI/hồi quy xong, DB đã migrate

Người dùng đã yêu cầu **B15–B16 và áp hết migration**, rồi xác nhận **làm luôn B14–B16**. Phạm vi này cho phép áp migration DB local; không còn ràng buộc “chưa áp migration” của các checkpoint trước. HEAD vẫn `34d942b`, giữ mọi diff trước đó, không commit/push.

- **ĐÃ ÁP DB LOCAL**: container `library_postgres`, database `huit_library`, volume `library-management_postgres_data`. Sao lưu custom-format trước áp tại `backups/librarian-pre-b14-b16-20261030.dump` (19.135.149 byte, ignored); `pg_restore -l` đọc được. Liquibase validate/checksum PASS; chỉ thiếu 28–35, đã chạy đủ 8 lúc **00:03:15 +07**, tổng 81 changeset, `REMAINING=0`, lock=false. Số books/book_copies/loan_tickets/transactions trước và sau giữ nguyên **269925 / 269939 / 27 / 14**. Không chạy demo seeder, không truncate/restore. Log ignored `.codex-tmp/b16-migration-plan.log`, `b16-migration-update.log`. PostgreSQL được bật từ trạng thái stopped; backend cũ vẫn stopped.
- Dashboard mới **`/admin`** (route mặc định hiện có, không có `/admin/dashboard`): bốn tab CirculationDashboard/HoldingsDashboard/FinanceDashboard/StockDashboard, chung ngày đầu gồm/cuối loại trừ và period, số hiện tại ghi rõ snapshot. Client `dashboard-analytics.service.ts`, models bên cạnh; tải từng phần/tab, hủy request cũ, lỗi không biến thành 0. CSS chung `analytics.css`, bảng responsive, biểu đồ giao/nhận từ số thật. Deep link B04 mở tab báo cáo và MSSV/mode OVERDUE. Vị trí/tệp/nguồn phân loại, receipt/allocations drill-down. B15 chỉ đọc mapping/history, không thêm UI ghi classification; API gán ngành B12 giữ nguyên.
- B15 thêm q tối đa 200 vào holdings/materials, tìm literal title/author hoặc ID tại SQL; cùng filter cho tổng/list. Test DB thêm bảo vệ C++/ký tự %/phân trang/tổng. Không migration mới sau 35.
- **Trạng thái cuối:** B14 HOÀN TẤT; B15 HOÀN TẤT UI trong contract có sẵn, số thực thi còn chờ; B16 ĐÃ HỒI QUY/BÀN GIAO PHẦN KHẢ DỤNG. Các phần Admin B07 placement rules, B09–B10 decision/execution và B13 số thực thi vẫn mở, không đánh dấu 7/7 yêu cầu hết phụ thuộc.

### Gate cuối và bằng chứng

- **283/283 Backend PASS**, 0 failure/error/skip, `./mvnw.cmd --batch-mode package` BUILD SUCCESS **00:24:49 +07 ngày 30/10**, log `.codex-tmp/b16-backend-final-package.log`. Jar `backend/target/library-0.0.1-SNAPSHOT.jar`. Lượt 282 PASS trước đó được chạy lại sau bổ sung coverage copy mất liên kết; kết quả hiện tại là 283. Toàn bộ integration/unit/HTTP/migration/rollback/cạnh tranh B01–B13 và search/coverage B15 đã chạy, không phải chỉ các test mới.
- **230/230 Web PASS, 51 files**, `npm.cmd test -- --watch=false --runner-config=../.codex-tmp/b01-vitest.config.mjs`, log `.codex-tmp/b16-web-final-tests.log`. Dashboard mới 13 test, analytics client 3, thêm 1 B04 deep link; thay thế 2 test Dashboard cũ. Lỗi probe ban đầu là fixture Angular zoneless chưa mark render và mock readonly route getter; đã sửa cách test, không tắt check/giảm assertion.
- **Production Web build PASS**, log `.codex-tmp/b16-web-final-build.log`, bundle tại web-app/dist/web-app. Chỉ warning cũ home/history CSS và qrcode CommonJS; test có sourcemap dependency warning. Không có budget warning mới cho Dashboard.
- **Browser Edge headless fixture PASS**, production bundle, desktop 1440/mobile 390, 26 API requests, **0 mutations/0 runtime errors**. Kiểm cùng kỳ giữa phần, xếp hạng trang 2, tổng/list/search C++/nguồn và vị trí/tệp, adjustment âm/allocations, lỗi tài chính rồi retry không hiện tổng 0, receipt detail, null/chờ Admin. Mobile đợi sidebar transition hoàn tất, kiểm mép phải và content width trước chụp. Đã xem ảnh desktop và mobile.
- Runner/log/ảnh ignored `.codex-tmp/b16-dashboard-ui.cjs`, `b16-dashboard-ui.log`, `b14-circulation-{desktop,mobile}.png`, `b15-holdings-desktop.png`, `b15-finance-desktop.png`, `b15-stock-{desktop,mobile}.png`. Browser fixture nguồn từ `dashboard-test-data.ts`, không ghi DB; không gọi là Web-to-live-DB E2E. Các lỗi probe của runner (route /admin/dashboard, selector label, chụp giữa transition, sandbox browser pipe) đã sửa; lượt cuối pass tại route thật `/admin`.
- **Preflight PASS**, 0 lỗi/3 warning CSV Git lớn, log `.codex-tmp/b16-preflight.log`; Compose config với .env.example PASS. `git diff --check` PASS (LF→CRLF warning một số file Windows), đường dẫn bàn giao tồn tại. Không chạy lại Flutter vì không đổi native client và không thấy caller Dashboard analytics trong mobile services/tests.

### Dữ liệu thật sau migration và phần còn mở

- Đọc repository thật bằng transaction read-only REPEATABLE_READ/rollback: `.codex-tmp/b16-db-read-check.log`, PASS, tổng materials khớp trang list. Không chạy backend seeder/scheduler, không tạo dữ liệu demo live.
- Snapshot local: **269931 đầu tài liệu** (269925 BOOK + 6 DIGITAL độc lập), đều chưa có mapping ngành có nguồn. **269939 copy toàn DB nhưng chỉ 17 quy về đầu sách hiện có, 269922 chưa quy được catalog**. B16 thêm Summary.coverage toàn DB để báo phần ngoài tổng/filter/nhóm hiện tại; UI đổi nhãn số bản thuộc đầu đã chọn, không gọi đó là toàn kho. Không gán/xóa/sửa dữ liệu liên kết để làm đẹp số. Test mới chứng minh orphan copy hiện coverage riêng.
- Circulation có 2 dòng có chứng cứ mượn thiếu ngày, 1 normal return thiếu ngày; finance có 1 charge thiếu nguồn cọc ban đầu; 2 phiếu COMPLETED cũ thiếu ngày hoàn tất (2 đầu liên quan). Đây là dữ liệu chưa thể phục dựng, không backfill đoán. Import kỳ có thể 0 đồng thời coverage báo 2 phiếu cũ chưa tính được.
- Local migration đã đủ **81 changeset/0 pending/lock=false**, source counts giữ nguyên và backup trước áp còn trên disk. Backend container cũ **vẫn stopped**, không triển khai image cũ; jar/bundle mới đã build. Khi sử dụng phải chạy đúng Backend/client mới, không suy migration đồng nghĩa runtime đã deploy.
- B07/TT-03 tự xếp vị trí, B09–B10 quyết định/thực thi và TT-06 số thanh lý/hủy chưa có Admin contract/event; giữ WAITING/null. Chưa load-test production lớn, chưa có closing debt lịch sử/vòng đời số riêng/camera-device UAT. Không dựng ca thực thi giả.
- Bàn giao sử dụng: **`Docs/HUONG_DAN_B14_B15_DASHBOARD.md`**. Checklist TT-01–TT-07, fixture, lệnh chạy lại và kết quả: **`TestChucNang/24_NGHIEM_THU_THU_THU_B01_B16.md`**. Kế hoạch/context/API B11–B12 đã cập nhật q và global coverage.

**Dừng sau B16 trong phạm vi đã có.** Chỉ làm tiếp khi người dùng gọi việc mới; không tự làm thay Admin hoặc sửa hàng loạt nguồn không có mapping/evidence. Không commit/push/deploy backend/frontend; DB migration là thao tác duy nhất triển khai được user cho phép trong lượt này. Giữ mọi working-tree diff và backup.
