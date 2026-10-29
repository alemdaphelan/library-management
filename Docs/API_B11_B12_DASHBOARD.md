# B11–B12 — Dashboard mượn/trả, khoa/ngành và tài liệu hiện có

Ngày bàn giao: **29/10/2026**. Phạm vi: Backend B11–B12. Giao diện dùng API mới thuộc B14–B15; thống kê tài chính/nhập mới/kết quả xử lý sách cũ thuộc B13.

Người dùng đã chốt: **“Cho thủ thư gán ngành, có nguồn và lịch sử”**. Không suy ngành của sách từ tên sách, department của độc giả hoặc trọng số recommender.

## 1. Quyền và quy ước

- Base API: `/dashboard/analytics`, giữ nguyên prefix hiện tại. Tất cả endpoint yêu cầu JWT ADMIN hoặc LIBRARIAN, trả JSON trực tiếp, `Cache-Control: no-store`. STUDENT/không đăng nhập không được truy cập.
- Ngày `YYYY-MM-DD`; timezone `Asia/Ho_Chi_Minh`. `from` bao gồm, `until` loại trừ. Ví dụ tháng 10: `from=2026-10-01&until=2026-11-01`.
- Mặc định từ ngày đầu năm hiện tại đến đầu ngày mai; khoảng tối đa 50 năm, năm 1–9999. `period=MONTH|QUARTER|YEAR`, mặc định MONTH. Bucket đầu/cuối được cắt theo khoảng yêu cầu, bucket rỗng vẫn có số 0. Sự kiện tương lai sau `snapshotAt` không vào thống kê trong kỳ.
- Phân trang bắt đầu `page=0`, `size=1..100`; số trang ngoài dữ liệu trả danh sách rỗng. Mỗi request GET dùng snapshot DB read-only REPEATABLE_READ. Các lần GET riêng có thể khác nhau khi dữ liệu thay đổi.
- Enum phải đúng chữ hoa. Sai enum/ngày/bộ lọc/page trả 400; tài liệu không tồn tại trả 404; version cũ hoặc yêu cầu dùng tệp đã liên kết như tài liệu độc lập trả 409.

## 2. B11 — giao nhận thực tế

| GET | Tham số | Nội dung |
|---|---|---|
| `/circulation` | from, until, period | snapshotAt/timezone/range, totals, buckets, coverage |
| `/rankings` | from, until, ranking, page, size (mặc định 10) | totalBooks, books, coverage; range.period luôn MONTH vì không có bucket xếp hạng |
| `/overdue` | page, size (mặc định 25) | totalItems, items quá hạn **hiện tại**; không có lọc ngày quá khứ |

Mỗi `Counts` có bốn chỉ số:

- `checkoutItems`: số dòng tài liệu đã giao sách thực tế.
- `physicalReturnItems`: số dòng đã nhận lại vật lý, kể cả DAMAGED nhận tại quầy.
- `checkoutTickets`: số phiếu khác nhau có giao trong kỳ/bucket.
- `physicalReturnTickets`: số phiếu khác nhau có nhận vật lý trong kỳ/bucket.

Một phiếu ba cuốn giao cùng kỳ là 3 items và 1 ticket. Một phiếu trả ở hai tháng được tính trong cả hai bucket nhưng tổng kỳ vẫn distinct 1 ticket. Vì vậy **không cộng số ticket của các bucket để suy tổng kỳ**. Tạo reservation, hủy hoặc tất toán bồi thường mất sách không phải một lượt giao/nhận vật lý. Trả khác tháng mượn được tính ở tháng nhận lại.

`LoanDetailEntity` có `checkoutAt`, `checkoutTimeSource`, `physicalReturnAt`, `physicalReturnTimeSource`, `circulationBookId`. Checkout thường/thủ công ghi thời điểm giao + bookId; normal return ghi nhận vật lý. Return-inspection DAMAGED ghi nhận vật lý; LOST không ghi. Replay không phát sinh thêm sự kiện. Bản ghi tài chính SETTLED/returnDate của sự cố không tự tạo lượt trả vật lý.

### Xếp hạng

| ranking | Tập đầu sách |
|---|---|
| MOST_BORROWED (mặc định) | Có lượt giao ghi nhận trong kỳ, nhiều xuống ít |
| LEAST_BORROWED | Có lượt giao ghi nhận trong kỳ, ít lên nhiều; không trộn với 0 lượt |
| NEVER_RECORDED | Không có chứng cứ mượn trong toàn bộ dữ liệu còn lưu |
| NO_RECORDED_IN_PERIOD | Không có lượt giao xác định thời gian trong kỳ |
| CURRENTLY_BORROWED | Có dòng đang mượn tại snapshot hiện tại; không phải snapshot cuối kỳ lọc |

Chỉ xét đầu sách có ý định khai thác vật lý (`isDigital=false/null`) hoặc có bản sao, kể cả đầu sách vật lý 0 bản; không đưa đầu sách thuần số vào xếp hạng mượn vật lý. Dòng trả về có `recordedInPeriod`, `recordedLifetime`, `currentlyBorrowed`, `copyCount`, `position`, `bookUrl`. `recordedLifetime` nhận cả chứng cứ trạng thái mượn thật nhưng thiếu ngày; không gọi dữ liệu thiếu ngày là chưa từng mượn. `position` dense rank theo chỉ số; thứ tự đồng hạng ổn định theo title/bookId. NEVER_RECORDED là kết luận trên dữ liệu còn lưu, không chứng minh toàn bộ lịch sử ngoài hệ thống.

Quá hạn: chỉ STUDENT, ticket/detail còn mượn BORROWING/BORROWED/OVERDUE, chưa nhận/trả, dueAt trước snapshot, chưa dừng tính phạt. Có userId/MSSV/tên, ticketId/detailId/barcode/bookId/title/dueAt, `ticketUrl` mở quầy và `studentReportApi` tìm báo cáo B04. Tìm B04 theo MSSV, thiếu MSSV dùng tên; tên có thể trùng nên đối chiếu userId. `totalItems` đếm dòng tài liệu, không đếm sinh viên distinct.

### Dữ liệu cũ và coverage

Migration **33-circulation-business-timestamps.yaml** chỉ backfill checkout khi có lịch sử chuyển BORROWING và dòng tồn tại trước/chính lúc chuyển; chỉ backfill physical return từ RETURNED + returnDate và không có penalty case. Không lấy ngày tạo reservation hoặc suy ngày mượn từ hạn trả. Dòng thiếu chứng cứ giữ null. `circulation_book_id` lưu ID để duy trì quy thuộc khi copy bị xóa; không có FK đến catalog, không phục dựng metadata đầu sách đã xóa.

`coverage` gồm:

- `checkoutRecordsWithoutTime`: dòng có chứng cứ mượn nhưng thiếu thời điểm giao, toàn bộ dữ liệu.
- `normalReturnsWithoutTime`: RETURNED nhưng thiếu thời điểm nhận vật lý, toàn bộ dữ liệu.
- `ambiguousIncidentReturns`: có sự cố DAMAGED nhưng chưa có chứng cứ nhận vật lý, toàn bộ dữ liệu.
- `periodCheckoutsWithoutCurrentCatalog`: lượt giao trong kỳ không quy được về đầu sách catalog hiện có.
- `complete`: cả bốn bằng 0. Không dùng nó làm bằng chứng rằng không có dữ liệu ngoài hệ thống.

Những dòng không có ngày không vào xu hướng theo kỳ. Tổng lượt có ngày có thể lớn hơn tổng xếp hạng catalog khi đầu sách đã bị xóa; coverage giải thích phần không quy thuộc. Có partial index trên checkout_at/physical_return_at. Test EXPLAIN chứng minh index checkout phục vụ khoảng chọn lọc 10.000 dòng; chưa nghiệm thu tải production lớn.

API Dashboard cũ giữ DTO: weekly-stats nay đếm **items giao thật** thay vì ticket được tạo; trending loại pending reservation; borrowedCurrent nhận BORROWED/BORROWING/OVERDUE; overdueBooks dùng quy tắc quá hạn STUDENT hiện tại. **B13 đã cập nhật** `fineCollected` thành PAYMENT thực nhận lũy kế có paidAt, loại charge/cấn cọc/nạp cọc; contract tài chính hiện tại: `Docs/API_B13_TAI_CHINH_NHAP_XU_LY.md`.

## 3. B12 — thống kê tồn hiện tại

| GET | Tham số | Nội dung |
|---|---|---|
| `/academic-catalog` | không | faculties/majors với ID, tên, mã ngành nếu có |
| `/holdings` | facultyId, majorId, type, state | filter, snapshotAt, totals và nhóm faculties/majors |
| `/materials` | cùng bộ lọc + page/size (25) | totalMaterials và materials |
| `/materials/{kind}/{id}/copies` | page/size (25) | bản sao, status/ghi chú/vị trí/copyApi |
| `/materials/{kind}/{id}/digital-assets` | page/size (25) | file, định dạng/dung lượng/accessLevel/downloadUrl |
| `/materials/{kind}/{id}/classification` | không | current và history gán ngành |

`kind=BOOK|DIGITAL`: BOOK là một đầu sách, DIGITAL là một digital_document **chưa liên kết bookId**. File gắn vào đầu sách được đếm trong digitalFiles của BOOK, kế thừa phân loại BOOK và không tạo thêm đầu tài liệu độc lập. Gọi DIGITAL/{id} cho file đã liên kết trả 409 và chỉ BOOK cần dùng. Nếu file độc lập sau này được liên kết, audit phân loại cũ vẫn lưu nhưng không cộng vào thống kê hiện tại và không tự gộp vào phân loại BOOK.

`type=ALL|PHYSICAL|DIGITAL|HYBRID` là loại chính xác: HYBRID có cả khai thác vật lý và số; PHYSICAL/DIGITAL không bao gồm HYBRID. Đầu sách vật lý 0 bản vẫn có physicalTitles; đầu sách đánh dấu số chưa có file vẫn có digitalTitles và lọc được NO_DIGITAL_FILE.

`state=ALL|AVAILABLE|BORROWING|RESERVED|LOST|DAMAGED|MAINTENANCE|RETIRED|NO_COPIES|NO_DIGITAL_FILE`. Bộ lọc trạng thái chọn **đầu tài liệu có trạng thái tương ứng**, các chỉ số của đầu đã chọn vẫn thể hiện tất cả bản của nó. Ví dụ đầu có 2 AVAILABLE và 1 LOST: state=LOST chọn đầu này và trả totalCopies=3, lostCopies=1.

`facultyId`/`majorId` lấy từ catalog, không tự tạo ID. `UNCLASSIFIED` chọn tài liệu chưa gán ngành. Ngành phải thuộc khoa nếu gửi cả hai. Không cho kết hợp khoa cụ thể và ngành UNCLASSIFIED.

### Đơn vị đếm và đối chiếu

`Counts` gồm:

- `materials`: BOOK + DIGITAL độc lập distinct, khớp totalMaterials của list cùng bộ lọc tại cùng dữ liệu.
- `catalogTitles`: BOOK; `physicalTitles`: có khai thác vật lý; `digitalTitles`: có khai thác số, gồm standalone; `standaloneDigitalTitles`: riêng DIGITAL độc lập. HYBRID xuất hiện trong cả physicalTitles và digitalTitles, không cộng hai chỉ số để ra materials.
- `totalCopies` và các bucket rời nhau: availableCopies, borrowedCopies, reservedCopies, lostCopies, damagedCopies, maintenanceCopies, retiredCopies, otherCopies. Tổng các bucket bằng totalCopies.
- `digitalFiles`: số metadata digital_documents liên kết hoặc độc lập, không phải kiểm tra tệp thật còn trên đĩa.
- `unmappedMaterials`: số tài liệu chưa có ngành được gán.

BORROWING/BORROWED/OVERDUE cùng vào borrowedCopies. RESERVED và AVAILABLE có hold còn hiệu lực vào reservedCopies; các AVAILABLE khác vào availableCopies. RETIRED/WITHDRAWN/DISPOSED/LIQUIDATED/DESTROYED vào retiredCopies; status lạ/null vào otherCopies. Đây là nhóm **trạng thái copy hiện tại**, không phải số sự kiện đã thanh lý/hủy. B09–B10 đang chờ Admin; B13 không được dùng đề xuất hay bucket này thay cho kết quả thực thi.

Một BOOK nhiều ngành cùng khoa chỉ đếm một lần trong khoa đó. Nhiều khoa/ngành có thể đếm chồng lấp; tổng thư viện distinct. Nhóm khoa/ngành theo bộ lọc hiện tại, có nhóm Chưa phân loại. List trả mappingStatus=MAPPED/UNMAPPED, majors, counts và các API drill-down; link đầu sách `/book/{id}`. Copy detail trả vị trí theo tầng/kệ/ngăn; asset detail chỉ trả download API hiện có, không lộ storageKey/đường dẫn nội bộ. Download vẫn kiểm quyền tại DigitalAssetController/Service.

### B15/B16 bổ sung tra cứu và coverage nguồn

GET holdings/materials nhận thêm `q` tùy chọn, tối đa 200 ký tự, trim, tìm literal không wildcard theo tên/tác giả (case-insensitive) hoặc ID chính xác. Filter response có query; cùng bộ lọc áp vào tổng/nhóm/list trước phân trang. Summary có `coverage.totalPhysicalCopies`, `copiesWithoutCurrentCatalog`, `linkedDigitalFilesWithoutCurrentCatalog` trên **toàn DB**, không theo bộ lọc. `totals.totalCopies` chỉ là bản thuộc các đầu đang chọn; nếu copy mất liên kết catalog, không được gọi số đó là toàn bộ tồn kho. Không tự gán lại hoặc xóa nguồn mất liên kết. UI/hướng dẫn hiện tại: `Docs/HUONG_DAN_B14_B15_DASHBOARD.md`.

## 4. Gán ngành có nguồn và lịch sử

`PUT /dashboard/analytics/materials/{kind}/{id}/classification`

```json
{
  "version": 0,
  "majorIds": ["<majorId lấy từ academic-catalog>"],
  "sourceReference": "Biên bản rà soát giáo trình số 12, ngày 29/10/2026"
}
```

- GET chưa có hồ sơ trả current.version=0, majors=[], history=[] và không ghi DB.
- PUT dùng version vừa đọc; tối đa 100 ngành khác nhau, mỗi ID tối đa 128 ký tự; sourceReference bắt buộc, tối đa 1000 ký tự. Có thể gửi majorIds=[] để bỏ ánh xạ nhưng vẫn phải ghi nguồn.
- Actor lấy từ JWT, không nhận từ body. Mỗi thay đổi lưu version, nguồn, actor/tên/thời điểm, snapshot trước/sau. Đổi nguồn dù ngành giữ nguyên vẫn là thay đổi có history.
- Advisory lock theo kind/id và khóa root material bảo vệ thao tác đồng thời. Thay thế assignments, tăng version và thêm history trong cùng transaction; lỗi bất kỳ bước nào rollback toàn bộ.
- Gửi lại cùng version/body ngay sau lần thành công trả current, không thêm history. Nếu version đã đổi và nội dung khác trả 409; đọc lại và đối chiếu trước khi gửi. Cùng nội dung/version hiện tại cũng không tạo thêm event.
- Bảng profile/history cố ý không có FK đến books/digital_documents để giữ audit sau xóa nguồn. API phân loại hiện tại vẫn yêu cầu nguồn tồn tại; chưa có API duyệt riêng các audit mồ côi. Không xóa audit trong các API này.
- Migration **34-academic-material-classification.yaml** tạo academic_majors, material_classifications, material_major_assignments, material_classification_history. Danh mục ngành là snapshot từ mapping khoa/ngành B04 tại lúc migration chạy; không tạo ánh xạ sách tự động. Thay đổi catalog B04 sau đó chưa tự đồng bộ và chưa có API quản trị danh mục ngành trong B12.
- sourceReference là khai báo nguồn của thủ thư, server không tải URL/xác minh nội dung nguồn. Không sửa recommender, loan, copy, file hay trạng thái vòng đời khi gán ngành.

## 5. Kiểm chứng và nghiệm thu tiếp theo

Các lớp chính: `modules/dashboard/{controller,dto,repository,service}/*Analytics*`, `MaterialClassificationService`, bốn entity mới trong `modules/reporting/entity/`; writers checkout/return trong `CirculationStateService` và `ReturnInspectionService`. Migration mới 33/34; không sửa migration đã áp.

Lệnh tại `backend`, JAVA_HOME `C:\Program Files\Java\jdk-21`:

```powershell
.\mvnw.cmd --batch-mode '-Dtest=DashboardAnalyticsIntegrationTest,DashboardAnalyticsMigrationIntegrationTest,DashboardServiceTest,Week6IntegrationTest,CirculationStateServiceTest,CirculationTransactionServiceTest,ReturnInspectionServiceTest' test
```

Bộ kiểm thử gồm DB thật PostgreSQL Testcontainers, HTTP/JWT, migration/rollback, ranh giới ngày và bucket rỗng, nhiều item/phiếu/trả từng phần, reservation/cancel, xếp hạng/tie/0 bản, thiếu thời điểm, quá hạn hiện tại, nhiều ngành/khoa, file liên kết/độc lập, trạng thái và vị trí, pagination, nguồn/history/version/replay, cạnh tranh và rollback khi history lỗi. Kết quả từng lần chạy ghi trong `Docs/BAN_GIAO_BATCH_THU_THU.md`.

Chưa có UI mới, nghiệm thu Web-to-live-DB, số liệu vòng đời số đã ngừng khai thác hoặc load test production. Migration/Backend chưa triển khai lên DB người dùng. Khi triển khai cần áp 33/34 cùng code mới theo quy trình riêng; rollback schema phải đi với rollback code dùng cột/bảng mới. B14–B15 dùng contract này, B13 xử lý tài chính/nhập kho và ghi rõ phần thực thi sách cũ còn chờ Admin.
