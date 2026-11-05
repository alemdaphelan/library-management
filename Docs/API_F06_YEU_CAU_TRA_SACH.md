# F06 — yêu cầu trả sách đang mượn

## Thao tác Web

Trong **Theo dõi Quá hạn & Phiếu mượn** (`/admin/borrows`), tab **Yêu cầu trả sách** hiển thị từng cuốn còn đang mượn. Có tìm theo tên/MSSV, số phiếu, mã vạch hoặc tên sách; lọc tất cả/chưa yêu cầu/đã yêu cầu/đã quá hạn; phân trang server. Link trực tiếp: `/admin/borrows?view=recall`.

Chọn **Yêu cầu trả cuốn này**, kiểm tra người/cuốn/hạn hiện tại, nhập lý do rồi xác nhận gửi. Mở hoặc đóng biểu mẫu chưa gửi không tạo yêu cầu. Trong lúc gửi khóa thao tác và chuyển tab; lỗi chưa rõ kết quả giữ lý do để gửi lại, không tự retry. Đóng biểu mẫu không rút lại yêu cầu đã ghi nhận. Kết quả hiển thị hạn cũ/hạn sau yêu cầu từ server; làm mới danh sách thất bại không biến kết quả đã xác nhận thành thất bại.

Bạn đọc thấy yêu cầu, lý do đầy đủ, thời điểm và hạn trả trong Lịch sử mượn (Đang mượn/Tất cả). Cuốn được yêu cầu trả không có nút gia hạn. Những cuốn khác vẫn theo quy định gia hạn hiện có.

## Quy tắc hạn và nguồn ghi nhận

Server lấy thời điểm Việt Nam (`Asia/Ho_Chi_Minh`), lưu đến microsecond. Hạn sau yêu cầu là **min(hạn cũ, thời điểm gửi + 7 ngày)**. Còn 5 ngày giữ đúng hạn cũ; còn 14 ngày rút còn 7; đã quá hạn không được đẩy sang tương lai. Không nhận hạn trả từ client. Chưa có hạn cũ thì trả 409 để đối chiếu, không tự gán hạn mới.

Lưu cờ `is_recalled=true` và bộ bằng chứng trên loan_details: `recall_requested_at`, `recall_requested_by` (UUID người gửi), `recall_reason`, `recall_previous_due_date`, `recall_due_date`. Hạn chính `due_date` cập nhật cùng transaction. Bộ bằng chứng đầu tiên giữ nguyên; trả sách/ghi nhận tiền sau đó không xóa bộ này. Thu hồi không ghi phí, đổi kho, nhận trả vật lý hoặc dừng tính phí trễ. Các luồng quá hạn, giới hạn mượn và tính phí tiếp tục dùng hạn chính.

## API

ADMIN/LIBRARIAN:

- `GET /loans/recall-items?q=&state=ALL&page=0&size=10`. state: ALL/NOT_RECALLED/RECALLED/OVERDUE. q tối đa 200 ký tự, tìm không phân biệt hoa thường và coi `%`/`_` là ký tự thường; không bỏ dấu tiếng Việt. page 0–100000, size 1–100. Response `{items,total,page,size}`; thứ tự due_date rồi detail_id, hạn null cuối. Count và rows cùng snapshot đọc, không ghi phí khi GET.
- Item gồm ticketId/detailId, bạn đọc, sách/mã vạch, hạn hiện tại/trạng thái, cờ và thông tin yêu cầu cũ, lossReportedAt. Chỉ ticket/detail BORROWING/OVERDUE, chưa có return_date/physical_return_at/fine_stopped_at. Cuốn chờ xác minh báo mất hoặc thiếu bạn đọc/hạn vẫn được hiển thị nhưng không có thao tác gửi. Mất liên kết catalog vẫn hiện barcode/nhãn tài liệu, không suy đoán bookId.
- `POST /loans/{ticketId}/items/{detailId}/recall`, body `{"reason":"Cần tài liệu phục vụ học phần"}`. Lý do bắt buộc 1–1000 ký tự; server bỏ khoảng trắng đầu/cuối. Response LoanItemDTO thêm isRecalled và bộ bằng chứng nêu trên. Chủ nhận lấy từ phiếu, người gửi lấy từ JWT; không nhận userId hay hạn client đề nghị.
- Khóa ticket → details cùng thứ tự với gia hạn/nhận trả. Tài liệu phải thuộc đúng phiếu, đang giữ, chưa có sự cố/báo mất, có hạn và bạn đọc hợp lệ. Sai phiếu/cuốn 404; dữ liệu hoặc phân trang không hợp lệ 400; trạng thái đã thay đổi/thiếu hạn/đã có lệnh cũ 409; Student không được gọi GET/POST staff (403).
- Gửi lại cùng cuốn và lý do đã chuẩn hóa trả yêu cầu gốc cùng trạng thái cuốn hiện tại, kể cả đã trả, không lùi/đẩy hạn lần nữa hoặc phát thêm thông báo. Lý do khác với yêu cầu đã ghi trả 409 để tải yêu cầu gốc. Không có API sửa/rút/gửi lại một lệnh mới trong F06.
- Gia hạn cuốn có cờ thu hồi trả 409 trước bước kiểm tra tài chính (khi phiếu còn BORROWING). Quy định từ chối phiếu đã quá hạn vẫn áp dụng. Gia hạn và thu hồi cùng khóa phiếu nên không ghi đè hạn của nhau.

## Thông báo và dữ liệu cũ

`LOAN_RECALLED`, referenceType LOAN_TICKET, referenceId ticketId, dedupKey theo detailId. Thông báo in-app cùng transaction với hạn/bằng chứng. Email/FCM qua cơ chế sau commit của F02, best effort; không bảo đảm giao bên ngoài nếu tắt cấu hình hoặc lỗi hệ thống gửi.

Do notifications.content hiện giới hạn 255 ký tự, thông báo chứa hạn và bản xem trước lý do/mã vạch, có chỉ dẫn mở Lịch sử. Lý do đầy đủ luôn lưu trên cuốn và trả cho bạn đọc qua my-loans, không cắt nội dung gốc để vừa thông báo. Số lần bấm gửi không làm tăng số thông báo.

Migration `38-loan-recall-evidence.yaml` thêm cột nullable/constraint/index, không đổi hạn/cờ cũ. Cờ thu hồi cũ nhưng thiếu bằng chứng được hiển thị và chặn gửi mới; không bịa người gửi, ngày gửi hay lý do. Rollback bỏ cột bằng chứng/index, giữ hạn/cờ hiện tại, vì vậy không dùng rollback để tự kéo dài hạn đã gửi; sao lưu trước triển khai và đối chiếu dữ liệu nếu phải phục hồi. Trạng thái áp DB local/backup nằm trong checkpoint F06 ở [KE_HOACH_FIX_LOI_V2.md](KE_HOACH_FIX_LOI_V2.md).

Source: LoanRecallController/Service/Models, LoanItemDTO/LoanDetailEntity/LoanService; Web borrows/RecallRequests và History/CirculationService. Kiểm tra bằng LoanRecallIntegrationTest (HTTP/JWT/PostgreSQL/Redis tạm), LoanRecallMigrationIntegrationTest và các spec liên quan. Không gửi yêu cầu thử tới bạn đọc thật. Flutter chưa có màn thao tác thu hồi mới.
