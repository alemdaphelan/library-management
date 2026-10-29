# B06 vị trí nhập kho và phần độc lập của B07

Ngày: **29/10/2026**. B06 hoàn thiện nhập vào vị trí thủ công đã xác nhận. B07 mới có nhận diện catalog/phân loại; **chưa có tự đề xuất theo quy tắc**. Người dùng đã xác nhận người phụ trách Admin chưa làm nơi quản lý quy tắc. Không seed một bộ quy tắc giả hoặc chọn ngăn bằng từ khóa/ISBN.

## B06 — Hợp đồng thực tế đã triển khai

API nhân viên ADMIN/LIBRARIAN; JWT như quầy hiện có:

| API | Ý nghĩa |
|---|---|
| POST `/inventory/receipts` | Nhập nguyên phiếu trong transaction, kiểm tra cả lượt trước ghi |
| GET `/inventory/receipts` | Danh sách tổng hợp; giữ cấu trúc cũ, lines có thể null |
| GET `/inventory/receipts/{id}` | Đọc lại dòng nhập, vị trí lúc nhập và các barcode liên kết |
| GET `/inventory/catalog-identification?isbn=...` hoặc `?bookId=...` | Nhận diện đầu sách/ấn bản và metadata có sẵn, không tạo dữ liệu |

Request nhập:

```json
{
  "supplierId": 7,
  "lines": [{
    "bookId": 12,
    "isbn": "0-13-235088-2",
    "quantity": 2,
    "unitPrice": 100000,
    "floorId": 1,
    "bookshelfId": 2,
    "shelfId": 3
  }]
}
```

ID và giá trị trên chỉ minh họa. `shelfId` là bắt buộc, số dương; cỡ lô là số nguyên dương, giá không âm. `bookId` hoặc ISBN phải có; nếu có cả hai thì ISBN phải khớp đầu sách. `floorId`/`bookshelfId` tùy chọn cho caller trực tiếp; Web gửi cả hai để server phát hiện cây đã đổi trước lưu. Không tạo bản sao vật lý cho `isDigital=true`.

Caller cũ không gửi `shelfId` sẽ nhận 400; cần cập nhật payload trước khi dùng API mới. Không tự gán ngăn mặc định cho request thiếu vị trí.

- Nhập bằng ISBN kiểm tra checksum ISBN-10/13, bỏ khoảng trắng/gạch nối, so khớp dạng 10 và 13 tương đương. Không lấy đầu sách đầu tiên nếu catalog có nhiều bản ghi cùng ISBN: trả 409, cần kiểm tra và chọn bookId rõ ràng. Không gộp hai ISBN khác nhau chỉ vì cùng tên.
- Nhập bằng bookId hỗ trợ catalog cũ chưa có ISBN hợp lệ. Khi dùng bookId đơn lẻ, caller không gửi một ISBN cũ không liên quan. Nhãn “Mã đầu sách” không phải barcode bản sao.
- Server giữ khóa chia sẻ trên ngăn và cây tầng/kệ qua query fetch/lock, sắp kết quả theo floor/bookshelf/shelf; cần tồn tại đủ cha và tên. Khóa chặn sửa/xóa các vị trí đang nhập nhưng cho phép hai phiếu cùng ngăn thực hiện đồng thời. Ngăn thiếu/đã xóa, cây sai hoặc khác parent IDs gửi lên trả 409; caller phải tải lại. Hiện schema vị trí không có cờ active/disabled; không giả có trạng thái được cấu hình mà code chưa hỗ trợ.
- Mỗi copy mới có shelf thật, trạng thái AVAILABLE và receiptDetailId; mỗi dòng có shelfId và locationPath snapshot. Lỗi validate/DB/bản sao cuối làm rollback receipt/details/copies/notification, không ghi thành công một phần.
- Migration **31-import-copy-locations.yaml** thêm receipt_details.shelf_id (FK nullable ON DELETE SET NULL), location_path varchar(1000), book_copies.receipt_detail_id (FK nullable ON DELETE SET NULL) và index. Giữ nullable cho bản ghi lịch sử; không đoán/backfill vị trí hoặc barcode của phiếu cũ. Không sửa lịch sử migration 05/29/30.
- Response tạo và GET chi tiết giữ trường receiptId/receiptCode/supplierId/supplierName/quantity/totalAmount/status/createdAt, thêm `lines`:

```text
{detailId, bookId, title, isbn, quantity, unitPrice, shelfId, locationPath, barcodes: string[]}
```

`locationPath` là tên tầng/kệ/ngăn **lúc nhập**, vẫn giữ sau đổi tên/di chuyển vị trí. `shelfId` có thể null nếu vị trí đã bị xóa; copy hiện tại có thể đã được chuyển đi, không suy vị trí hiện tại từ snapshot phiếu. Phiếu cũ chưa có liên kết barcode trả danh sách rỗng, không ghép giả theo tất cả bản sao cùng bookId. GET không tìm thấy phiếu trả 404; thiếu shelfId qua HTTP trả 400, không có quyền trả 403.

## Web thủ thư

1. Mở **Quản lý Nhập kho sách → Tạo Phiếu Nhập**. Chọn nhà cung cấp.
2. Mỗi dòng chọn ISBN hoặc mã đầu sách, nhập và bấm **Đối chiếu danh mục**. Xem tên/tác giả/NXB/năm để kiểm tra ấn bản. Đổi định danh làm mất kết quả xác nhận cũ; response cũ không ghi đè định danh mới.
3. Xem category/DDC/subject và nguồn/evidence nếu có. Thiếu phân loại thì nêu rõ; đây không phải cam kết đã ánh xạ sách sang một ngành đào tạo. Mã ISBN không chứa mã khoa/ngăn.
4. Chọn tầng → kệ → ngăn cho từng dòng. Đổi tầng/kệ xóa lựa chọn con; dòng khác giữ lựa chọn riêng. Đường dẫn đầy đủ hiện trước lưu. Lỗi tải vị trí có thông báo/thử lại; không có dữ liệu giả để thay thế.
5. Chọn số lượng/giá hợp lệ và lưu. Form/nút đóng/thêm dòng bị khóa khi đang gửi, chống bấm gửi lặp. Hủy trước gửi không tạo dữ liệu. B06 chưa bổ sung idempotency receipt; sau lỗi mất kết nối chưa rõ kết quả phải đối chiếu danh sách phiếu trước khi lập lại.
6. Sau lưu hoặc khi bấm **Xem phiếu**, xem lại vị trí lúc nhập và barcode của từng dòng. Phiếu cũ hiện “chưa ghi nhận vị trí”, không bị coi là một phiếu đã có đủ dữ liệu B06.

## B07 — Phần đã làm và giới hạn hiện tại

Endpoint catalog-identification nhận **đúng một** `isbn` hoặc `bookId`. ISBN sai định dạng/checksum trả 400; không có catalog trả 404; trùng ISBN trả 409. Không gọi dịch vụ metadata ngoài và không tự tạo đầu sách. ISBN-13 chỉ prefix 978/979; ISBN-10 được chuyển sang 978 với check digit tính lại, 979 không được ghép với các bản ghi ISBN trống.

Response có bookId/title/isbn/canonicalIsbn/author/publisher/publishYear/defaultPrice, categoryId/categoryName/ddcCallNumber và `subjects[{code,name,confidence,source,evidence,primary}]` lấy từ assignments có subject active. `classificationStatus` AVAILABLE hoặc MISSING. Không dùng department_subject_affinities của recommender để quyết định khoa/ngăn.

Hiện **luôn** trả `placementStatus=WAITING_ADMIN_RULES`, `suggestedShelfId=null`, thông báo chọn thủ công. Không có engine/quy tắc Admin trong repo. Kiểm thử có chứng minh ISBN/ấn bản/mơ hồ/không suy ngăn; **chưa chứng minh** tie-break ưu tiên, rule hiệu lực/thay đổi trước lưu hoặc quyền override. Những phần đó chưa triển khai, B07 chưa hoàn tất.

Tham chiếu định dạng/check digit: [International ISBN Agency](https://www.isbn-international.org/content/what-isbn/10), [ISBN Users’ Manual](https://www.isbn-international.org/content/isbn-users-manual/29). Kiểm tra định dạng không chứng minh ISBN đã được cấp; nhận diện vẫn phải khớp catalog thật.

## Hợp đồng đề xuất gửi người phụ trách Admin — CHƯA TRIỂN KHAI / CHƯA THỐNG NHẤT

Đây là bản bàn giao cụ thể để thống nhất, **không phải API đang có**:

| Thành phần | Nội dung cần chốt |
|---|---|
| Quy tắc | ID ổn định, version, active, effectiveFrom/effectiveUntil, priority, shelfId đích |
| Điều kiện | categoryId và/hoặc subjectCode đã tồn tại; nếu dùng major/faculty phải có mapping sách-ngành đáng tin, không dùng mapping sinh viên thay thế |
| Kết quả tra | Trạng thái MATCHED/NO_RULE/CONFLICT/MISSING_METADATA, rule ID/version khớp, đường dẫn đầy đủ, lý do khớp; không chọn ngẫu nhiên khi hai ngăn cùng ưu tiên |
| Quyền | Ai tạo/sửa/bật/tắt, có cho thủ thư đổi ngăn hay không; nếu có phải quy định quyền và lý do bắt buộc |
| Audit | Ghi người/thời điểm cấu hình và ruleId/version/lý do override trên dòng phiếu; giữ snapshot khi rule sau này đổi |
| Lưu phiếu | Server đọc lại metadata, rule hiệu lực/ưu tiên và cây vị trí trong transaction; không tin suggestedShelfId/rule từ client |
| Xung đột | Rule/metadata/ngăn đã đổi hoặc quyền không đủ -> 409/403, rollback cả lượt và yêu cầu nhận diện lại |

Người phụ trách Admin cung cấp schema/API hoặc adapter chính thức theo contract đã chốt. Sau đó nối vào **cùng** luồng import (kể cả bookId) trước mọi ghi copy, không để thủ công B06 thành đường vòng vượt chính sách. Không tự chọn shelfId mặc định theo ISBN/nhãn màu/keyword hoặc tạo một bộ hard-code song song.

Để chốt B07 cần kiểm thử thêm: quy tắc đang hiệu lực, nhiều rule khác ưu tiên, đồng ưu tiên khác ngăn, không có rule, ngăn bị xóa/đổi cha, rule/metadata đổi sau preview, override hợp lệ/không quyền/thiếu lý do, rollback và UI tương ứng. Không nhận làm màn cấu hình Admin trong lượt này.
