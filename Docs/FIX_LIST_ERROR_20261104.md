# Checkpoint sửa List_error.docx — 04/11/2026

Nguồn: C:/Users/Lenovo/OneDrive/Desktop/List_error.docx (8 mục, không có ảnh nhúng). Baseline main / 756870b; working tree sạch lúc bắt đầu. Code Admin shelf_subject_rules/library-policy đã merge. Toàn bộ thay đổi ở working tree, chưa commit/push/deploy.

| Mục | Kết quả |
|---|---|
| 1. Dashboard | Đã sửa: biểu đồ SVG cho mượn/trả, tài chính, nhập kho và danh mục theo khoa; preset ngày/tuần/tháng/năm với ngày tham chiếu, khoảng ngày tùy chọn, DAY/WEEK/MONTH/QUARTER/YEAR. Đồng bộ kỳ đã áp dụng cho báo cáo và danh sách theo sự kiện. |
| 2. Quy tắc nhập kho | Đã nối rule chủ đề Admin: tra catalog gợi ý ngăn/path thật; Web tự điền và kiểm tra lại cây; server từ chối từng dòng nhập vào ngăn không phù hợp trước mọi ghi, rollback toàn lượt. |
| 3. Gửi yêu cầu mượn | Đã sửa cập nhật async dùng chung và chặn gửi trùng ở Detail; kiểm tra phản hồi chậm thành công/thất bại, mượn một cuốn và nhiều cuốn hiển thị kết quả ngay. |
| 4. Form mất/hỏng | Đã đổi sang MSSV → hồ sơ bạn đọc → tài liệu thực sự đang mượn. Chỉ gửi loanDetailId nội bộ từ lựa chọn hợp lệ, bỏ kết quả tra cũ và tài liệu đã trả/đã có sự cố. Thu tiền cũng tra MSSV. |
| 5. Trường bắt buộc | Đã rà các form nghiệp vụ và khai báo required/conditional required; RequiredFieldsService đặt một sao đỏ cạnh nhãn, có aria-hidden và liên kết label/control; giữ trường tùy chọn, không nhân đôi sao có sẵn. |
| 6. Sách trên ngăn | Đã sửa query LEFT JOIN để bản sao không biến mất khi mất catalog; thêm catalogMatched=false, phân trang và danh sách chưa xếp ngăn, có thao tác đặt từng bản sao vào ngăn do thủ thư chọn. Dữ liệu mất liên kết vẫn cần đối chiếu nguồn — xem bên dưới. |
| 7. Cấn cọc hết nợ | Đã sửa History/MyPenalties: tính phí còn phải trả sau paidAmount/trạng thái đóng, bỏ giao dịch nạp cọc/thanh toán khỏi số nợ; hết nợ báo đã thanh toán/cấn trừ đủ. |
| 8. CRUD treo giao diện | Đã khôi phục Zone.js + provideZoneChangeDetection đồng bộ với main.ts để callback của các component dùng field thường tự cập nhật. Edge xác nhận thêm/sửa/xóa trễ, lập hồ sơ và đặt sách không cần click/tab thêm. |

## Hợp đồng và phạm vi

- Đường dẫn chính: web-app/src/app/pages/admin/dashboard, imports, penalties, shelf-map; portal/detail, history, my-penalties; services/required-fields.service.ts, debt-amount.ts, location.service.ts. Backend modules/catalog/service/ShelfPlacementService, inventory/CatalogIdentificationService và InventoryService; dashboard/AnalyticsBuckets và các repository/service thống kê.
- Mọi khoảng thời gian là [from, until), giờ Việt Nam. Tuần bắt đầu thứ Hai; tối đa 1000 bucket. Tổng danh mục, quá hạn và dư nợ vẫn là snapshot hiện tại vì không có lịch sử đủ để tái tạo; tab danh mục không đưa bộ chọn ngày gây hiểu nhầm. Thanh lý/hủy thực thi vẫn thiếu workflow/sự kiện Admin; không tự coi đề xuất là kết quả.
- Catalog identification trả SUGGESTED_BY_ADMIN_RULE hoặc NO_MATCHING_RULE, suggestedShelfId nullable, placements có đầy đủ floor/bookshelf/shelf. Dùng book_subject_assignments và rule active/subject active, không suy từ ISBN/từ khóa. Nhiều ngăn phù hợp được sắp ổn định theo ID, gợi ý ngăn đầu. Ngăn không có rule là không giới hạn theo hợp đồng Admin hiện tại. Không có priority/effective date/capacity/override version để triển khai thêm.
- GET staff /locations/shelves/{id}/books/page và /locations/unplaced-copies trả {items,total,page,size}, page từ 0, size 1–100 (mặc định 50). API list cũ giữ tương thích; ShelfBookDTO thêm catalogMatched. PUT /book-copies/{barcode}/location vẫn kiểm tra chủ đề khi xếp ngăn.
- Đọc DB local: 269939 bản sao, 17 chưa có shelf_id và đều khớp catalog; 269922 bản sao cũ không khớp catalog hiện tại nhưng có vị trí. Nguyên nhân ngăn trước đây trống là INNER JOIN loại những bản sao này. Nay chúng hiển thị barcode/mã cũ và nhãn chưa đối chiếu, không có link sai sang đầu sách. Không tự sửa book_id bằng offset, không tạo vị trí thực tế giả. Cần nguồn ánh xạ đáng tin để phục hồi tên/ISBN; 17 cuốn chưa xếp được thủ thư đặt bằng thao tác rõ ràng trên UI.
- Không có migration mới trong lượt sửa này. Không ghi nghiệp vụ vào DB local; test backend dùng PostgreSQL/Redis Testcontainers, browser dùng API synthetic được chặn hoàn toàn. Đã khởi động container library_postgres sẵn có để chẩn đoán chỉ đọc; truy vấn NOT IN quá chậm đã được hủy.

## Kiểm tra đã hoàn tất

| Kiểm tra | Kết quả |
|---|---|
| Backend full suite, JDK 21 + Maven 3.9.9 cache | 299/299 PASS, 0 failure/error/skip |
| Web full suite, Angular/Vitest 1 worker | 258/258 PASS, 54 file |
| Web production build | PASS, bundle khoảng 1.84 MB |
| Edge trên build cuối | PASS, 62 request fixture, 0 pageerror; CRUD trễ 650 ms; mượn một/nhiều cuốn; MSSV; rule nhập kho; orphan/unplaced; hết nợ; Admin mobile 390 px |
| Visual QA | Đã xem dashboard desktop/mobile và form MSSV; sao đỏ nằm cạnh nhãn, không trôi lên hàng riêng |
| git diff --check | PASS |

Lệnh Web: npm test -- --watch=false --runner-config=../.codex-tmp/b01-vitest.config.mjs; npm run build (workdir web-app). Runner config giới hạn maxWorkers=1/pool=threads/fileParallelism=false. Maven bundled trong repo hiện thiếu boot jar, wrapper Windows bị lỗi lúc chạy; dùng bản Maven cache tại %USERPROFILE%/.m2/wrapper/dists/apache-maven-3.9.9/8e74001100ff70d6af083c5511fcc5ec49282d7017cde82c3698eee8fdf86698/bin/mvn.cmd --batch-mode test, JAVA_HOME trỏ JDK 21.

Log/fixture ảnh chẩn đoán giữ trong .codex-tmp/list-error-*: backend-test.log, web-test.log, web-build.log, ui.cjs, ui-result.log và PNG desktop/mobile/MSSV/ngăn. Đây là file tạm bị gitignore; các test hồi quy quan trọng nằm cạnh source và trong backend/src/test. Cảnh báo còn có sẵn: CSS budget home/history, qrcode CommonJS, ZXing missing sourcemap khi test. Không kiểm lại Flutter hoặc tích hợp VNPAY/Firebase thiết bị thật.

Nếu tiếp tục: đọc PROJECT_CONTEXT.md và checkpoint này, đối chiếu git diff hiện tại; phần source 8 mục đã hoàn thành trong phạm vi ghi trên. Phần dữ liệu catalog cũ cần nguồn mapping/backup trước khi sửa liên kết. Không tự commit/push từ checkpoint.

## Chạy local theo yêu cầu tiếp theo — 04/11/2026

Đã package code mới (skipTests vì full suite đã qua ở trên), copy JAR vào container library_backend rồi khởi chạy cổng 8080, giữ volume DB/tài liệu và tắt demo seeding. Angular dev server nền tại http://127.0.0.1:4200; staff /admin; Swagger http://127.0.0.1:8080/swagger-ui/index.html. Backend health UP, catalog/Swagger HTTP 200; Liquibase 86 applied, 0 mới cần chạy. Redis/Mongo/Elasticsearch/Kafka/Zookeeper/Mailpit đang bật. Thông tin PID/log ở .codex-tmp/dev-runtime.json và dev-web.*. Chưa commit/push; không phải deploy từ image mới — recreate container từ image cũ sẽ mất JAR vừa copy, cần build lại source khi deploy mới.

Đã tắt theo yêu cầu giải phóng RAM: Angular (cả tiến trình con), backend, PostgreSQL, Redis, MongoDB, Elasticsearch, Kafka, Zookeeper và Mailpit. Giữ nguyên dữ liệu/volume. Trạng thái runtime: stopped.
