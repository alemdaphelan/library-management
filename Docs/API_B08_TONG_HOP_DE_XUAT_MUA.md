# B08 — Tổng hợp đề xuất mua sách và xuất Excel

Chốt **29/10/2026**. B08 hoàn tất ở Backend/Web; không thay đổi Flutter, schema production hoặc luồng duyệt từng đề xuất. Không gửi email/file cho khoa tự động.

## Sử dụng trên Web

1. Vào **Duyệt Đề xuất sách** (`/admin/suggestions`) → **Tổng hợp / Excel**. Tab này chỉ tải khi mở; tab **Từng đề xuất** và drawer duyệt/từ chối vẫn dùng luồng cũ.
2. Chọn khoa, từ ngày/đến ngày, trạng thái và nhóm/trang rồi **Áp dụng / Làm mới**. Bộ lọc tổng hợp độc lập với ô tìm kiếm/tab trạng thái của danh sách từng đề xuất.
3. Xem số nhóm, số lượt, số người và phân bố từng khoa. **Xem N đề xuất** mở các nguồn gốc; bấm mã `#id` để xem drawer của đúng đề xuất. Không có thao tác duyệt hàng loạt từ nhóm.
4. **Tải Excel toàn bộ** tải đủ tập kết quả bộ lọc, không chỉ trang đang xem. File rỗng vẫn có ba sheet, tiêu đề và bộ lọc. Người dùng tự gửi file cho khoa.
5. Nếu thay đổi bộ lọc chưa áp dụng thì xuất/phân trang bị khóa. Khi đang gửi request, form bị khóa. Lỗi tải/403/Blob không phải Excel có thông báo và có thể thử lại.

## API thực tế

Chỉ `ADMIN`/`LIBRARIAN`, response `Cache-Control: no-store`:

| Method | Path | Nội dung |
|---|---|---|
| GET | `/proposals/admin/summary` | Tổng hợp có phân trang theo nhóm |
| GET | `/proposals/admin/summary/export` | XLSX đầy đủ, cùng semantics bộ lọc |
| GET | `/proposals/admin/summary/faculties` | Khoa đã ánh xạ B04 + `UNCLASSIFIED` |

Query chung: `facultyId` tùy chọn, `from`/`to` dạng `yyyy-MM-dd` tùy chọn, `status=ALL|PENDING|APPROVED|REJECTED` (mặc định `ALL`). Ngày bắt đầu bao gồm đầu ngày; ngày kết thúc bao gồm hết ngày bằng điều kiện `< to + 1 ngày` trên `created_at` đang lưu. Không tự chuyển đổi timestamp lịch sử không có timezone. `from > to`, khoa không tồn tại, ngày/trạng thái sai trả 400. Preview thêm `page>=0`, `size=1..100` (mặc định 0/25); export không dùng page/size.

Response gồm `snapshotAt`, `timezone=Asia/Ho_Chi_Minh`, `filter`, `page`, `size`, `totalGroups`, `totalProposals`, `totalPeople`, `groups`. Mỗi group có:

- `groupKey`, `identityType` (`ISBN`, `METADATA`, `INVALID`, `INCOMPLETE`), `canonicalIsbn`, metadata đại diện từ đề xuất đầu tiên theo ngày/ID;
- `proposalCount`, `peopleCount`, `faculties[{facultyId,facultyName,proposalCount,peopleCount}]`;
- `warnings`, `sources[]`: mã đề xuất, userId/tên/MSSV/department gốc, khoa, toàn bộ tên/tác giả/NXB/ISBN/ấn bản/năm gốc, trạng thái/ngày gửi.

`sources` chứa đủ nguồn của các nhóm trong trang, không cắt theo số dòng gốc. Nhóm sắp theo số lượt giảm dần, tên chuẩn hóa và groupKey để phân trang xác định. groupKey là hash khóa gộp cho tập kết quả hiện tại, dùng đối chiếu ba sheet; không phải mã đầu sách/khóa bền vững để cập nhật dữ liệu.

## Quy tắc gộp và đếm

- ISBN hợp lệ được chuẩn hóa bằng `Isbn` của B07: bỏ khoảng trắng/gạch nối, kiểm checksum; ISBN-10 và ISBN-13 tương đương dùng chung ISBN-13. Hai ISBN khác nhau không gộp theo tên. ISBN có checksum sai được giữ riêng từng đề xuất và cảnh báo, không tráo sang nhóm thiếu ISBN.
- Nếu cùng ISBN nhưng có nhiều ấn bản hoặc nhiều năm rõ ràng khác nhau trong tập kết quả đã lọc: tách theo metadata đầy đủ và cảnh báo cần kiểm tra. Không tự sửa metadata/ISBN. Những nguồn thiếu giá trị trong tình huống xung đột cũng không bị ép vào một ấn bản rõ ràng.
- Không ISBN: khóa gồm tên + tác giả + NXB + ấn bản + năm. Chuẩn hóa Unicode NFKC, hoa/thường, khoảng trắng (kể cả Unicode); giữ dấu tiếng Việt và ký hiệu có ý nghĩa như `C++`/`C#`. Không fuzzy match. Thiếu tên/tác giả hoặc thiếu cả năm và ấn bản thì giữ riêng. Giá trị trống ở từng trường không tự coi là bằng trường đã biết, nên có thể còn nhóm cần đối chiếu thủ công.
- Lọc khoa/ngày/trạng thái **trước khi** nhóm/đếm. Một sách được nhiều khoa đề xuất là một nhóm trong báo cáo chung khi cùng khóa; báo cáo một khoa chỉ đếm nguồn thuộc khoa đó.
- Số lượt = số đề xuất gốc; cùng user gửi lại vẫn tính lượt, số người = số userId khác nhau không null. Tổng người đếm riêng toàn tập, không cộng người qua các nhóm/khoa. Nguồn không có userId vẫn giữ và tính lượt, không bịa số người.
- Khoa dùng mapping chuẩn của B04 trên hồ sơ **hiện tại** của sinh viên. Hồ sơ thiếu/không ánh xạ, người gửi không phải sinh viên hoặc không còn hồ sơ nằm trong `UNCLASSIFIED` và giữ department gốc để sửa. Không suy khoa từ nội dung sách; B08 không bổ sung khoa lịch sử lúc gửi đề xuất.

## Excel và tính nhất quán

- `Tong hop`: khóa nhóm/cách gộp/ISBN/metadata đại diện, lượt/người, cảnh báo.
- `Theo khoa`: từng nhóm × khoa với lượt/người. Không nhét danh sách mã dài vào một cell.
- `De xuat goc`: từng đề xuất/user gốc, thông tin sách, khoa, trạng thái/ngày và khóa nhóm để truy vết.
- Header/bộ lọc/thời điểm chốt, freeze pane và auto-filter; tự chia sheet nếu chạm giới hạn dòng Excel. Số đếm lưu numeric; proposal ID, ISBN, MSSV và các chuỗi nguồn luôn lưu STRING, kể cả bắt đầu `=`, `+`, `-`, `@`, tab/CR. Không tạo hyperlink/công thức từ đầu vào.
- Preview và export mỗi request chạy read-only `REPEATABLE_READ`; chúng là hai snapshot riêng. Nếu có đề xuất mới hoặc đổi trạng thái giữa hai request, file có thể khác preview; thông báo xuất thể hiện thời điểm xuất. Không ghi/xóa/gộp bản ghi nguồn, tạo sách hay gửi thông báo khi xem/xuất.
- Hiện service đọc toàn bộ nguồn phù hợp vào bộ nhớ để gộp chính xác, rồi mới phân trang nhóm. SXSSF giảm bộ nhớ khi ghi XLSX nhưng không loại bỏ chi phí nguồn/nhóm/byte[] của toàn tập. Chưa nghiệm thu tải dữ liệu rất lớn; không có giới hạn cắt ngầm.

## Kiểm chứng và đường dẫn code

Backend: `modules/reporting/{controller/ProposalSummaryController,dto/ProposalSummaryReport,repository/ProposalSummaryRepository,service/ProposalSummaryService,service/ProposalSummaryExcelExporter}.java`. Dùng lại khóa chuẩn hóa department của `StudentObligationReportRepository`; các endpoint cũ giữ nguyên.

Web: `pages/admin/suggestions/proposal-summary.{ts,html,css,spec.ts}`, `services/proposal-summary.service.{ts,spec.ts}`. Dùng lại `ReportFileSaver`; hàm `xlsxFilename` dùng chung với B05. Parent suggestions mở nguồn trong drawer hiện có.

**57 Backend + 199 Web test PASS, production build PASS** ngày 29/10/2026. PostgreSQL Testcontainers kiểm chứng dữ liệu nguồn/mapping, ISBN-10/13, khác ấn bản, thiếu/sai ISBN, khoảng trắng/ký hiệu, người gửi lặp, khoa/ngày/trạng thái, phân trang, file rỗng/công thức. HTTP thật kiểm ADMIN/LIBRARIAN/STUDENT/không đăng nhập, XLSX và CORS header. Test master riêng include chuỗi circulation đã kiểm chứng + migration 12/23 để có đầy đủ cột proposal, không sửa migration production.

Browser Edge cô lập chạy production bundle với API fixture tạo bằng grouping/POI thực tế: desktop 1440, mobile 390; lọc, phân trang, mở nguồn và tải đủ **26 nhóm/27 đề xuất gốc** dù preview 10 dòng. Đã mở lại file bằng POI đối chiếu nhóm/tên/số đếm và xác nhận không có formula cell; đã xem ảnh UI. Đây là kiểm thử browser fixture và Backend DB riêng biệt, chưa phải nghiệm thu trên DB đang dùng. Log/fixture/ảnh kiểm thử nằm trong `.codex-tmp/b08-*` (ignored), không phải dữ liệu để gửi cho khoa.
