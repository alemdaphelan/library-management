# F04 — bạn đọc báo mất sách

Trong Lịch sử mượn, từng cuốn BORROWING/OVERDUE chưa nhận trả và chưa có sự cố có nút **Báo mất cuốn này** (cả tab Đang mượn và Tất cả). Bạn đọc nhập mô tả rồi xác nhận gửi. Nút bị khóa trong lúc gửi; lỗi/timeout giữ mô tả để kiểm tra lịch sử hoặc gửi lại, không tự gửi lại. Báo thành công hiện “Đã báo mất · chờ thủ thư”, không cho gia hạn cuốn đó; các cuốn khác dùng quy định gia hạn hiện có.

## API và dữ liệu

- STUDENT: `POST /loans/{ticketId}/items/{detailId}/loss-report`, body `{"description":"Mất sách trên xe buýt"}`. Không nhận userId, số tiền hay phương thức đền bù từ bạn đọc. Mô tả bắt buộc, tối đa 1000 ký tự; backend bỏ khoảng trắng ở hai đầu.
- Phải là chính chủ phiếu, đúng detail thuộc phiếu, ticket/detail BORROWING hoặc OVERDUE, chưa có returnDate/physicalReturnAt. Lỗi quyền 403, không tìm thấy phiếu/cuốn 404, trạng thái không còn phù hợp 409, mô tả không hợp lệ 400. Request chưa đăng nhập bị chặn bởi cấu hình xác thực chung.
- Response 200 là `LoanItemDTO` hiện tại, bổ sung `lossReportedAt` và `lossReportDescription`. Các trường này cũng có ở my-loans/chi tiết phiếu. Migration 37 thêm hai cột nullable và constraint đi cùng nhau; không suy diễn báo mất từ hồ sơ phạt cũ.
- Mỗi detail có một báo mất đầu tiên, giữ nguyên nội dung/thời điểm. Khóa ticket → details trong transaction ngăn gửi đồng thời. Gửi lại từ chính chủ trả báo gốc cùng trạng thái hiện tại, kể cả sau khi thủ thư đã xử lý/nhận sách; không tạo thêm thông báo. Mốc báo có độ chính xác microsecond để response đầu và retry khớp PostgreSQL.
- Báo mất chỉ lưu thông tin và thông báo cho staff; **không** ghi transaction/penalty case, cấn cọc, đổi kho, đổi trạng thái mượn/ngày trả/hạn trả hoặc dừng phí trễ. Việc tính và dừng phí khi thủ thư lập hồ sơ vẫn theo nghiệp vụ hiện có. Cuốn đó vẫn là nghĩa vụ đang mượn cho tới khi được xử lý; backend gia hạn trả 409 cho cuốn đã báo mất.
- ADMIN/LIBRARIAN: `GET /loans/loss-reports/{detailId}` trả `LoanTicketDetailDTO` với danh tính chủ phiếu và **chỉ một item** được báo. Student không được đọc endpoint staff. Detail chưa có báo mất trả 404.

## Thủ thư tiếp nhận

Thông báo `LOAN_LOSS_REPORTED`, referenceType `LOAN_LOSS_REPORT`, referenceId là detailId, gửi tới ADMIN/LIBRARIAN bằng `StaffNotificationService`. Nội dung in-app được lưu trong cùng transaction với báo mất; email/push dùng bộ gửi sau commit hiện có, best effort. Event key theo detailId và người nhận; không phát lại khi retry.

Trong Thông báo, **Tiếp nhận báo mất** mở `/admin/penalties?referenceType=LOAN_LOSS_REPORT&referenceId=<detailId>`. Panel hiển thị tên/MSSV, phiếu, cuốn/barcode, thời điểm, mô tả gốc. Mở panel không lập hồ sơ hoặc ghi phí.

**Chuẩn bị hồ sơ cho cuốn này** tra cứu MSSV hiện tại, xác minh đúng userId/ticketId/detailId còn mượn, chọn cuốn và sao chép mô tả sang form LOST. Mô tả gốc giữ nguyên. Thủ thư kiểm tra cách đền bù/phí rồi bấm **Lập hồ sơ** để gọi API phạt hiện có. Nếu đã có hồ sơ, panel mở đúng hồ sơ đó. Nếu sách đã trả/đang xử lý thì không chuẩn bị hồ sơ mới. Không có MSSV hoặc danh tính/cuốn không khớp sẽ yêu cầu đối chiếu, không tự gán người khác.

Nếu bạn đọc tìm thấy sách, thủ thư có thể nhận trả bình thường qua quầy; báo mất gốc vẫn giữ để đối chiếu. F04 chưa có luồng rút/sửa báo mất hoặc màn Flutter mới.

## Triển khai và kiểm tra

Migration: `backend/src/main/resources/db/changelog/changesets/37-reader-loss-reports.yaml` được master includeAll tự nạp. Rollback bỏ hai cột và mất nội dung báo mất, nên sao lưu trước khi triển khai/rollback. Việc đã áp DB local và kết quả test cuối được ghi tại checkpoint F04 trong [KE_HOACH_FIX_LOI_V2.md](KE_HOACH_FIX_LOI_V2.md).

Backend: `LoanLossReportIntegrationTest` dùng HTTP/JWT thật với PostgreSQL/Redis tạm; `ReaderLossReportMigrationIntegrationTest` kiểm tra cột cũ null, constraint, rollback/reapply; `LoanServiceTest` kiểm tra chỉ cuốn đã báo mất bị chặn gia hạn. Web: History, Penalties, Notifications, CirculationService specs. Không gửi báo thử tới tài khoản hay staff DB local.
