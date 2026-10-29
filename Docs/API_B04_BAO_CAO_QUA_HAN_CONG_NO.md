# B04 — API báo cáo sinh viên quá hạn / công nợ và Excel

Ngày: **29/10/2026**. Dành cho **LIBRARIAN/ADMIN**, JWT như các API nhân viên khác. B04 triển khai Backend; Web B05 đã tích hợp tại tab báo cáo trong `/admin/borrows`, xem [hướng dẫn B05](HUONG_DAN_B05_BAO_CAO_WEB.md). Hợp đồng API không đổi trong B05.

## Endpoint và bộ lọc

Base: `/api/v1/librarian-reports/student-obligations`.

| Method/path | Kết quả |
|---|---|
| GET base | JSON tổng hợp một sinh viên/một dòng; chi tiết quá hạn và sổ nợ của các sinh viên trên trang |
| GET base`/export` | File `.xlsx` thật, toàn bộ sinh viên phù hợp bộ lọc và chi tiết tương ứng |
| GET base`/faculties` | Danh mục `{facultyId, facultyName}`, luôn có `UNCLASSIFIED` / Chưa phân loại |
| GET base`/classification-coverage` | Đếm toàn bộ sinh viên đã/chưa phân loại, kể cả người không quá hạn/nợ; liệt kê department thiếu mapping cùng số lượng, không trả danh tính |

| Query | Quy tắc |
|---|---|
| `mode` | `OVERDUE`, `DEBT`, `EITHER`; mặc định `EITHER`, phân biệt hoa/thường |
| `facultyId` | Bỏ trống = mọi khoa; ID từ endpoint faculties; `UNCLASSIFIED` = chưa ánh xạ. ID không tồn tại trả 400 |
| `q` | Tìm chuỗi con không phân biệt hoa/thường trong MSSV hoặc họ tên, trim, tối đa 100 ký tự; `%`, `_` là ký tự bình thường |
| `page` | Chỉ preview, bắt đầu 0, không âm; mặc định 0 |
| `size` | Chỉ preview, 1–200, mặc định 25 |

Export chỉ đọc `mode`, `facultyId`, `q`. Truyền `page/size` sang export không làm giới hạn kết quả. Preview và export gọi cùng bộ lọc/SQL; mỗi request chốt một snapshot riêng, nên số liệu có thể đổi nếu giao dịch xảy ra giữa hai request.

Ví dụ đường dẫn (ID khoa phải có trong dữ liệu thật):

```text
GET /api/v1/librarian-reports/student-obligations?mode=EITHER&facultyId=UNCLASSIFIED&q=2001&page=0&size=25
GET /api/v1/librarian-reports/student-obligations/export?mode=EITHER&facultyId=UNCLASSIFIED&q=2001
```

Các response thành công có `Cache-Control: no-store`. Export có Content-Type `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, Content-Disposition `attachment; filename="thu-thu-<mode>-yyyyMMdd-HHmmss.xlsx"`. CORS expose `Content-Disposition` để Web đọc tên file.

## Định nghĩa tập dữ liệu

- Chỉ `users.user_type = STUDENT`, không kéo nhân viên vào báo cáo sinh viên chỉ vì họ có giao dịch hoặc tài khoản.
- **OVERDUE**: có ít nhất một chi tiết chưa trả, `return_status` BORROWING/OVERDUE, phiếu BORROWING/OVERDUE, chưa có `fine_stopped_at`, `due_date < snapshotAt`. Không chờ scheduler đổi nhãn OVERDUE. LOST/DAMAGED/SETTLED/PENDING/CANCELLED không là sách đang còn quá hạn.
- **DEBT**: dư nợ ledger dương **hoặc** ví ghi nợ dương. Người đã trả hết sách vẫn xuất hiện. Ví dương nhưng ledger không có khoản tương ứng vẫn hiện để đối soát, không tự biến số ví thành dư nợ ledger.
- **EITHER**: hợp hai tập trên, mỗi user ID đúng một dòng. Không phải giao hai tập.
- `overdueDays` là số ngày lịch từ hạn sớm nhất đến ngày chốt; trong cùng ngày có thể là 0 dù thời điểm hạn đã qua. Xem `earliestDueDate`/chi tiết để biết giờ; không dùng con số này tự tính lại tiền phạt.
- Chỉ tiền **đã ghi sổ**; GET không cộng phí trễ mới, không cấn cọc, không chỉnh trạng thái và không trigger scheduler. Chưa ghi phí có thể đồng thời quá hạn và dư nợ bằng 0.

## JSON trả về

`StudentObligationReport`:

| Trường | Ý nghĩa |
|---|---|
| `snapshotAt`, `timezone`, `currency` | Thời điểm chốt request, `Asia/Ho_Chi_Minh`, `VND` |
| `filter` | `{mode, facultyId, query}` đã chuẩn hóa; facultyId null là mọi khoa |
| `page`, `size`, `totalStudents` | Trang preview, cỡ trang, tổng số sinh viên phù hợp toàn bộ bộ lọc |
| `students` | Danh sách tổng hợp của trang; export nội bộ lấy toàn bộ |
| `overdueItems` | Chi tiết còn quá hạn của những user ID trong `students` |
| `ledgerEntries` | Các khoản nghĩa vụ và dòng thanh toán/miễn/điều chỉnh của những user ID trong `students` |

Mỗi `students` có `userId`, `mssv`, `fullName`, `rawDepartment`, `facultyId`, `facultyName`, `majorName`, `mappingStatus`, `overdueItemCount`, `earliestDueDate`, `overdueDays` và các số tiền sau:

| Trường tiền | Nguồn/ý nghĩa |
|---|---|
| `chargedAmount` | Tổng giá trị dương các giao dịch `FINE`, `REPAIR_FEE`, `COMPENSATION`, `TECHNICAL_FEE` của sinh viên, gồm cả khoản cũ đã giải quyết |
| `paidOrDepositAmount` | Tổng `paid_amount` các khoản trên trừ miễn giảm và điều chỉnh giảm đã hoàn tất; gồm thanh toán thực và cọc đã cấn, không coi miễn giảm là tiền đã thu |
| `waivedAmount` | Trị tuyệt đối giao dịch âm `WAIVER_ADJUSTMENT`, status COMPLETED |
| `adjustedAmount` | Trị tuyệt đối giao dịch âm `ADJUSTMENT`, status COMPLETED; yêu cầu đang PENDING/REJECTED không tính |
| `outstandingAmount` | Tổng `max(amount-paid_amount,0)` của các khoản nghĩa vụ có status OUTSTANDING/PARTIALLY_PAID/PARTIALLY_ADJUSTED/PARTIALLY_WAIVED, cùng quy tắc payable của ledger |
| `walletDebt` | Số nợ đang lưu trong ví; null nếu chưa có ví, khác với ví có số dư 0 |
| `walletDifference` | `walletDebt - outstandingAmount`, null nếu chưa có ví |
| `reconciliationDifference` | `chargedAmount - paidOrDepositAmount - waivedAmount - adjustedAmount - outstandingAmount` |

Mô hình hiện có gộp cọc, thanh toán, miễn và điều chỉnh vào `transactions.paid_amount`; B04 loại riêng hai loại giảm nợ để có cột **Đã thanh toán/cấn cọc** chung, không suy đoán phân chia tiền mặt với cọc từ `payment_method` (trường này có thể bị ghi đè sau thanh toán). Không cộng giao dịch PAYMENT âm hoặc allocations vào số đã đáp ứng lần nữa.

Chênh khác 0, hoặc số thanh toán/cấn cọc âm, là dấu hiệu dữ liệu cần đối soát; API không sửa, che hoặc ép bằng 0. Các số tổng hợp là toàn bộ lịch sử khoản đã ghi của sinh viên trong phạm vi loại phí trên, không chỉ các khoản gắn với những tài liệu đang quá hạn. Không dùng `loan_details.late_fine_accrued` hoặc `totalLateFine` làm công nợ.

`overdueItems`: `userId`, `ticketId`, `loanDetailId`, `barcode`, `bookTitle` (có thể null với dữ liệu catalog cũ bị thiếu), `ticketType`, `dueDate`, `overdueDays`.

`ledgerEntries`: `userId`, `transactionId`, `ticketId`, `loanDetailId`, `penaltyCaseId`, `type`, `reason`, `status`, `amount`, `satisfiedAmount`, `outstandingAmount`, `paymentMethod`, `receiptNumber`, `createdAt`. `satisfiedAmount` là paid_amount thô, **gồm cả miễn/điều chỉnh**, không gắn nhãn đã thu tiền. Dòng PAYMENT/miễn/điều chỉnh âm chỉ đối chiếu; không cộng lại vào nghĩa vụ. Miễn theo case có thể không có parent transaction, nên không phân bổ giả miễn giảm xuống từng khoản.

Các nguồn overdue/charges/reductions/wallet được GROUP BY user trước khi join. Count, tổng hợp và chi tiết trong mỗi request ở transaction read-only `REPEATABLE_READ`, dùng cùng thời điểm chốt. Query phân trang có thứ tự MSSV, họ tên, user ID ổn định.

## Mapping khoa/ngành — hợp đồng dữ liệu

Migration **29-academic-report-mapping.yaml** thêm:

| Bảng | Khóa và trường |
|---|---|
| `academic_faculties` | `faculty_id` PK varchar(64), `faculty_name` bắt buộc |
| `academic_department_mappings` | `department_value` PK varchar(255), `faculty_id` FK bắt buộc, `major_name` tùy chọn |

Từ migration **30-seed-huit-academic-classification.yaml**, `department_value` và `users.department` dùng cùng khóa chuẩn hóa Unicode NFD, bỏ dấu tiếng Việt (đ -> d), không phân biệt hoa/thường, gộp dấu câu/khoảng trắng. Chỉ ghép đúng khóa với các alias đã khai báo, không dùng fuzzy search/từ khóa hoặc điểm affinity của recommender để đoán khoa. Unique index trên khóa chuẩn hóa ngăn cùng một cách viết về hai khoa; nếu DB cũ có mapping xung đột thì migration báo lỗi để sửa, không chọn ngẫu nhiên. Giữ nguyên `users.department` để đối chiếu. `UNCLASSIFIED` vẫn là ID dự phòng dành riêng.

Theo yêu cầu bổ sung của người dùng, đã tự đối chiếu nguồn chính thức thay vì chờ danh mục: [danh sách khoa trên HUIT](https://huit.edu.vn/) và [39 ngành đại học, ngày 03/07/2026](https://ts.huit.edu.vn/thong-bao/danh-muc-nganh-dao-tao-trinh-do-dai-hoc), truy cập 29/10/2026. Khoa quản lý từng ngành được đối chiếu từ liên kết “Xem chi tiết” tới website khoa trong bảng tuyển sinh.

Migration 30 seed **17 khoa, 39 ngành**: tên ngành, `Ngành <tên>`, mã ngành chính thức, tên khoa và alias `CNTT`/`Khoa CNTT` trong dữ liệu repo. ID khoa (FIT, FOODTECH, COMMERCE, ...) là ID nội bộ ổn định do project đặt, không phải mã hành chính do trường công bố. Giữ mapping thủ công đã tồn tại bằng ON CONFLICT DO NOTHING, không sửa migration 29. Có nhãn khoa thì phân loại khoa nhưng không tự điền ngành cụ thể (ví dụ CNTT không chứng minh người đó học Công nghệ thông tin thay vì An toàn thông tin).

Các giá trị seed sinh viên hiện có trong repo (`CNTT`, `Khoa Công nghệ thông tin`) đã có mapping. Tên không dấu/khác hoa thường/khoảng trắng/dấu câu được nhận diện cùng khoa/ngành. Null/rỗng hoặc chuỗi chưa xác định vẫn được chỉ ra để sửa thông tin nguồn, không gán bừa một khoa cho đủ 100%. Mục tiêu nghiệm thu dữ liệu thật là `classification-coverage.complete=true`; chưa có DB người dùng đang chạy trong lượt bổ sung để chứng minh điều đó trên dữ liệu ngoài repo.

Coverage trả `totalStudents`, `classifiedStudents`, `unclassifiedStudents`, `complete`, `unmappedDepartments: [{rawDepartment,studentCount}]`. Chỉ được khẳng định phân loại hết DB đang dùng khi `unclassifiedStudents=0` (với totalStudents phù hợp số sinh viên thực tế). API này dùng cùng khóa mapping của báo cáo, kiểm tra cả sinh viên không có nợ/quá hạn; không ẩn thiếu mapping bằng bộ lọc báo cáo.

Đây là mapping hiện tại tại thời điểm đọc, chưa phải lịch sử thay đổi cơ cấu khoa/ngành. Dùng chung hợp đồng này cho B05/B08/B12 nếu chưa có danh mục trung tâm khác; thống nhất trước khi thay thế, không tạo một bộ tên khoa rời rạc ở frontend.

## File Excel và quyền

- Ba sheet: **Tong hop** (một sinh viên/dòng), **Qua han** (một tài liệu/dòng), **So no** (một giao dịch/dòng). Mỗi sheet có thời điểm/zone/bộ lọc, đơn vị, ghi chú số liệu, header, freeze pane và autofilter. Rỗng vẫn có đủ ba sheet với header.
- Tiền là ô số có định dạng; giá trị quá 15 chữ số được lưu text để tránh Excel làm mất chính xác. Nguồn chuỗi như MSSV/tên/lý do luôn ghi kiểu STRING, kể cả bắt đầu `=`, `+`, `-`, `@`, tab hoặc CR; không tạo formula/hyperlink từ chuỗi người dùng.
- Dùng POI SXSSF, xử lý chi tiết theo nhóm user và tách sheet nếu vượt giới hạn dòng Excel, không cắt theo trang preview. Snapshot và file response vẫn cần bộ nhớ theo lượng dữ liệu; chưa phải job export nền hoặc link lưu trữ lâu dài.
- Thiếu JWT/role không được phép bị security từ chối; mode sai, khoa không tồn tại, page/size/q không hợp lệ trả 400. Không có dữ liệu trả JSON rỗng hoặc workbook có header, không phải 404.

## Bàn giao B05

1. Dùng faculties cho bộ chọn khoa; có lựa chọn mọi khoa. Dùng classification-coverage kiểm tra dữ liệu đã phân loại hết; chỉ hiện Chưa phân loại để phát hiện dữ liệu lỗi/thiếu cần sửa, không coi đó là một khoa hoàn chỉnh. Không nhóm `rawDepartment` thành khoa trong client.
2. Giữ mode/facultyId/q giống nhau giữa xem và tải. Preview dùng page/size; export tải Blob toàn bộ, lấy filename từ Content-Disposition.
3. Hiện snapshotAt, VND, số sinh viên; tách dư nợ ledger với ví và chênh lệch khi có. Tiền đã thu/cấn cọc, miễn và điều chỉnh là các cột riêng.
4. Mở phiếu tại `/admin/circulation?referenceType=LOAN_TICKET&referenceId=<id>&mode=return`, sổ nợ tại `/admin/penalties?referenceType=USER&referenceId=<uuid>`; giữ các thao tác quản lý phiếu hiện có.
5. B04 chưa thêm UI Web/Mobile, chưa deploy/restart hoặc chạy migration trên DB người dùng. Môi trường chạy cần cả migration 29 (schema) và 30 (danh mục/index khóa chuẩn hóa).
