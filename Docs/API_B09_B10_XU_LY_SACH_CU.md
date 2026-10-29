# B09–B10 — Hồ sơ đề xuất xử lý sách cũ của thủ thư

Chốt **29/10/2026**: hoàn tất Backend/Web **phần thủ thư**. Người dùng xác nhận **Admin chưa làm, ghi rõ phần chờ**. Quyết định và kết quả xử lý thực tế chưa tích hợp; không coi gửi hồ sơ là đã thanh lý/hủy. B07 vẫn chờ quy tắc Admin độc lập với module này.

## Phạm vi và trạng thái hiện có

```text
Lập từ kiểm kê đã hoàn tất → DRAFT → SUBMITTED
                              ↘         ↙
                               CANCELLED
```

- Mỗi hồ sơ ứng với **một barcode**, nguồn kiểm kê thật, tình trạng thực tế, nhóm lý do, lý do chi tiết, phương án, URL chứng cứ tùy chọn, người lập/ngày và lịch sử.
- `DRAFT`: người lập được sửa và xác nhận lại nguồn hiện tại. Chỉ gửi nội dung đã lưu, kiểm tra lại barcode/nguồn/điều kiện tại server.
- `SUBMITTED`: đã ghi nhận gửi đề xuất trong hệ thống; ngừng sửa nội dung. Người lập được hủy khi luồng Admin chưa có bước quyết định. Không đồng nghĩa đã duyệt, đã thông báo ra ngoài hoặc đã thực thi.
- `CANCELLED`: hủy hồ sơ đề xuất, giữ lịch sử; không phải hủy bản sách vật lý. Có thể lập hồ sơ mới cho barcode khi không còn hồ sơ hoạt động.
- ADMIN/LIBRARIAN xem mọi hồ sơ; chỉ **người lập** sửa/gửi/hủy hồ sơ của mình (kể cả Admin không sửa thay hồ sơ người khác qua API này). Người lập lấy từ principal, không nhận actor/status/bookId từ client.
- Response chi tiết luôn có `adminIntegrationStatus=WAITING_ADMIN_WORKFLOW`. Chưa có trạng thái quyết định, API thực thi, bảng sự kiện thanh lý/hủy hoặc kết quả có thẩm quyền. B13 không được đếm DRAFT/SUBMITTED/CANCELLED thành số sách đã xử lý.

## Thao tác Web

1. Vào **Kiểm kê kho bằng barcode** (`/admin/inventory-audit`). Hoàn tất phiên sau khi quét để cố định nguồn.
2. Bấm **Lập đề xuất xử lý từ phiên này**, hoặc tab **Hồ sơ xử lý sách cũ**, chọn phiên và tải nguồn. Không dùng danh sách giả. Mỗi barcode hiển thị trạng thái/vị trí hiện tại, kết quả quét và lý do không đủ điều kiện; barcode đã có hồ sơ hoạt động có nút mở hồ sơ đó.
3. Chọn 1–50 bản sao; nhập tình trạng và lý do riêng từng dòng, chọn phương án sửa/chuyển/thanh lý/hủy. URL chứng cứ chỉ nhận http/https hợp lệ, không chứa tài khoản; backend không tải URL, chưa có upload tệp/kiểm tra nội dung ảnh. Chuyển đơn vị/vị trí mới mô tả trong lý do; chưa đổi shelf thực tế.
4. **Lập hồ sơ nháp đã chọn** lưu toàn lô nguyên tử, mở lại hồ sơ đầu tiên từ GET đã lưu. **Hủy form chưa gửi** không gọi API ghi. Đổi phiên cần tải nguồn lại và chọn lại; không áp dòng cũ vào phiên mới.
5. Chi tiết hiển thị snapshot nguồn đã lưu, dữ liệu nguồn hiện tại và blocker. Nháp của người lập được sửa; nếu nguồn đổi, đối chiếu rồi **Lưu nháp / xác nhận nguồn** trước khi gửi. Trường hợp barcode nguồn đổi/không còn hợp lệ thì không ép sửa sang bản sao khác; hủy hồ sơ và lập lại đúng nguồn khi phù hợp.
6. **Gửi đề xuất đã lưu** dùng nội dung đã lưu; có thay đổi chưa lưu thì nút gửi bị khóa. Gửi thành công chỉ hiện “đã gửi — chờ Admin”, không đổi trạng thái bản sao. Có thể hủy hồ sơ trong phạm vi quyền nêu trên.
7. Danh sách lọc trạng thái/mã phiên/barcode chính xác, phân trang server 25 dòng; mở hồ sơ để xem lịch sử từng phiên bản, người thực hiện, nội dung trước/sau và snapshot nguồn tương ứng. Danh sách và form chọn nguồn có bộ chọn phiên độc lập.

### Mất kết nối và gửi lại

- Lập lô dùng `requestId` UUID và toàn bộ body cố định, lưu trong sessionStorage **theo userId/email hiện tại** (`huit_disposal_pending:<identity>`) và trong bộ nhớ. Lỗi 0/5xx giữ yêu cầu, khóa form để gửi lại nguyên body; không sinh ID/body mới khi chưa rõ kết quả. Khôi phục sau reload chỉ hiển thị yêu cầu, không tự POST. Nếu storage không dùng được thì chỉ còn bản trong bộ nhớ.
- Khi lỗi 400/403/409 rõ ràng, bỏ yêu cầu chờ nhưng giữ nội dung form để người dùng đối chiếu/tải nguồn lại. API business conflict không ghi một phần.
- Sửa/gửi/hủy gặp lỗi thì khóa thao tác tiếp trên chi tiết, yêu cầu **Tải lại chi tiết** để đối chiếu trạng thái/version trước khi tiếp tục. Không tự gửi một mutation mới khi chưa rõ kết quả.

## API thực tế

Base: **`/inventory/disposals`**, mọi endpoint yêu cầu ADMIN/LIBRARIAN, `Cache-Control: no-store`.

| Method | Path | Nội dung |
|---|---|---|
| GET | `/candidates?auditId=...` | Nguồn kiểm kê, fingerprint, eligible, blockers, activeProposalId từng dòng |
| POST | base | Lập lô 1–50 nháp; trả requestId/replay/proposals[] |
| GET | base | Danh sách `status` tùy chọn DRAFT/SUBMITTED/CANCELLED, `auditId`, `barcode` chính xác, page/size (0/25, size 1–100) |
| GET | `/{proposalId}` | Item, saved snapshot, current candidate, canEdit/canSubmit/canCancel, adminIntegrationStatus, history |
| PUT | `/{proposalId}` | Sửa nháp của người lập và xác nhận nguồn mới với version/fingerprint hiện tại |
| POST | `/{proposalId}/submit` | Gửi nháp đã lưu, kiểm tra lại nguồn và eligibility |
| POST | `/{proposalId}/cancel` | Hủy hồ sơ của người lập khi DRAFT/SUBMITTED, không xóa lịch sử |

Ví dụ tạo (các ID/fingerprint chỉ minh họa; phải lấy từ API nguồn thực tế):

```json
{
  "requestId": "2762244a-9ae9-4712-b846-0779f1288057",
  "auditId": 9,
  "lines": [{
    "barcode": "BC-001",
    "sourceDetailId": "d3a3889b-1f59-4b34-bd67-395ea38e3265",
    "expectedFingerprint": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "observedCondition": "Bìa rách, nhiều trang bong gáy",
    "reasonCode": "WORN",
    "reason": "Cần phục hồi trước khi khai thác tiếp",
    "proposedAction": "REPAIR",
    "evidenceUrl": "https://example.org/evidence/BC-001"
  }]
}
```

Lý do: `WORN`/`OBSOLETE`/`UNUSED`/`DAMAGED`. Phương án: `REPAIR`/`TRANSFER`/`LIQUIDATE`/`DESTROY`. Tình trạng/lý do bắt buộc, mỗi nội dung và URL tối đa 1000 ký tự. PUT gồm `version`, `expectedFingerprint` từ current candidate và năm trường nội dung tương tự dòng tạo (không nhận đổi barcode/phiên/dòng nguồn). Submit/cancel chỉ nhận `{"version": 0}` theo phiên bản đã đọc.

Trả 400 khi validation/URL/bộ lọc sai, 401 khi thiếu actor tại service, 403 do vai trò/không phải người lập, 404 khi không có phiên/hồ sơ, 409 khi trùng key khác nội dung/người, hồ sơ hoạt động trùng barcode, nguồn/trạng thái/version đã đổi hoặc không ở bước cho phép. Không đăng nhập bị SecurityFilterChain chặn trước controller.

### Nguồn, khóa và tính nguyên tử

- Chỉ phiên `COMPLETED`, scan `FOUND`/`MATCHED`/`MISPLACED` có copy và book thật, vật lý, trạng thái `AVAILABLE`/`DAMAGED`/`MAINTENANCE` được đề xuất. Những trạng thái khác (gồm BORROWING/BORROWED/OVERDUE/RESERVED/LOST), giữ chưa hết hạn hoặc phiếu PENDING/RESERVED/BORROWING/BORROWED/OVERDUE chưa kết thúc đều bị chặn; kiểm phiếu cả khi copy đang ghi AVAILABLE sai lệch.
- Snapshot gồm phiên/dòng/thời gian quét, barcode/book/title/ISBN, status/condition/shelf/path hiện tại, timestamp và hold fields. Fingerprint SHA-256 của snapshot giúp phát hiện nguồn/copy đổi giữa xem và lưu; không phải chữ ký hay một sự kiện thanh lý. Edit giữ snapshot cũ ở history.before và lưu nguồn mới ở history.after. Không sửa bản ghi kiểm kê gốc.
- Tạo lô: khóa PostgreSQL advisory theo request UUID, kiểm key/owner/body đã lưu, khóa chia sẻ audit/scans và khóa copy theo thứ tự barcode rồi kiểm toàn bộ dòng. Một lỗi rollback batch/proposal/history toàn lô. Key trùng cùng actor/nội dung chuẩn hóa trả cùng các proposal IDs (trạng thái hiện tại); key trùng khác nội dung/actor trả 409. Chuẩn hóa trim/sắp barcode để thứ tự dòng không tự tạo yêu cầu khác.
- Sửa/chuyển trạng thái khóa proposal, xác minh owner/version, khóa nguồn/copy và kiểm lại eligibility khi cần. `@Version` ngăn ghi đè; submit/cancel lặp ngay cùng version cũ nhận lại kết quả hiện tại, không thêm history lặp. Hủy không yêu cầu copy còn đủ điều kiện, nên có thể giải phóng hồ sơ đang chặn barcode đã đổi trạng thái.
- Partial unique index bảo đảm chỉ một DRAFT/SUBMITTED mỗi barcode, ngay cả caller không đi qua UI. Bản sao **không** bị đổi status/shelf/hold khi lập/gửi/hủy hồ sơ, nên sau gửi vẫn có thể được mượn; cần kiểm lại ở bước thực thi có thẩm quyền sau này.
- Danh sách có phân trang DB. Candidate và history hiện trả cả tập tương ứng; tạo/kiểm tra nguồn đang đọc/khóa các scan của phiên trước khi lọc dòng chọn, chưa nghiệm thu tải phiên rất lớn.

## Schema, code và phần Admin còn chờ

Migration mới **32-disposal-proposals.yaml**: `disposal_batches` (request/owner/body), `disposal_proposals` (một barcode, FK nguồn, version/snapshot/nội dung/trạng thái/creator), `disposal_history` (event UUID, proposalVersion duy nhất, actor, before/after). FK giữ copy/book/audit/scan và lịch sử liên quan, không cascade xóa nguồn. Không sửa migration cũ hoặc gộp vào `book_proposals`. Rollback 32 bỏ ba bảng mới; không đổi inventory cũ. Chưa áp migration lên DB đang dùng trong lượt này.

Code Backend: `modules/inventory/{controller/DisposalController,dto/DisposalModels,service/DisposalService}`, `entity/Disposal*`, `repository/Disposal*`; thêm truy vấn nguồn khóa chia sẻ và kiểm barcode trên phiếu vào repository hiện có. Web: `pages/admin/inventory-audit/disposal-workspace*`, `services/disposal.service*`, parent inventory-audit tích hợp tab/entry, không tạo route mới và không sửa Flutter.

**Phần chờ Admin, chưa triển khai và chưa coi đã chốt contract:**

- Trạng thái tiếp nhận/xét duyệt/từ chối, người có quyền, lý do/phiên bản/decision ID và tham chiếu quyết định chính thức. Hiện không có endpoint duyệt hay adapter nhận kết quả từ hệ thống Admin.
- Mở rộng trạng thái phải có migration mới cập nhật cả CHECK và predicate của partial unique index; không ghi trực tiếp APPROVED/COMPLETED vào schema ba trạng thái hiện có. Quyền rút hồ sơ sau khi Admin xử lý cũng cần chốt lại.
- Thực thi cần kiểm lại copy/phiếu giữ-mượn/quyết định mới nhất với cơ chế khóa phù hợp circulation; kết quả phải có event ID bất biến, ngày hiệu lực, người chịu trách nhiệm và liên kết quyết định. Chỉ dịch vụ có thẩm quyền mới ghi thay đổi copy/vị trí/kết quả nguyên tử và audit; retry không tạo kết quả trùng.
- Sau khi có hợp đồng quyền, mới nối màn thủ thư ghi nhận kết quả nếu được phép. B13 chỉ đếm sự kiện thực thi có thẩm quyền theo ngày hiệu lực; không đếm đề xuất/chấp thuận là số đã thanh lý/hủy. Hiện chưa có bảng/API thực thi nên chưa có dữ liệu này cho B13.

## Kiểm chứng ngày 29/10/2026

- **53 Backend PASS:** DisposalWorkflowIntegrationTest 10 (Spring/HTTP/JPA trên PostgreSQL thật, concurrency và rollback), DisposalMigrationIntegrationTest 3 (Liquibase 32/FK/partial unique/rollback-reapply ở schema cô lập), Week6IntegrationTest 30, InventoryAuditServiceTest 7, BookCopyServiceTest 2, LocationServiceTest 1. Workflow test dùng Hibernate create-drop; migration test kiểm riêng schema Liquibase và ràng buộc thực tế. Không áp schema test vào DB người dùng.
- **215 Web tests/50 files + production build PASS**. Thêm 12 workspace, 3 service và 1 parent test so với B08. Có kiểm nguồn, blockers, per-line validation, hủy form, key/body retry/restore, stale/403/API errors, version, nội dung chưa lưu và trạng thái chờ Admin.
- Edge browser fixture trên production bundle: chọn hai barcode/khác phương án, chặn bản đang mượn, hủy chưa POST, giả mất phản hồi sau commit → reload không tự POST → retry nguyên body không tạo trùng, sửa/gửi/hủy và mở lại history đã lưu trong fixture, desktop 1440/mobile 390. Không mutation copy, không lỗi runtime; đã xem ảnh. Đây là browser fixture và DB tests riêng, chưa phải Web-to-live-DB E2E.
- Logs, script và ảnh ignored trong `.codex-tmp/b09-backend-tests.log`, `b10-web-tests.log`, `b10-web-build.log`, `b10-ui-check.cjs`, `b10-ui-check.log`, `b10-draft-{desktop,mobile}.png`, `b10-detail-{desktop,mobile}.png`. B11 chưa bắt đầu.
