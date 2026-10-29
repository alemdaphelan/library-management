# B03 — Quầy kiểm tra trả sách và bàn giao Mobile

Ngày: **29/10/2026**. Hợp đồng Backend không đổi so với [B02](API_B02_KIEM_TRA_TRA_SACH.md). B03 triển khai Web cho LIBRARIAN/ADMIN; chưa sửa Flutter.

## Thao tác trên Web

1. Mở **Quản lý Mượn - Trả → Trả sách**. Quét camera hoặc nhập barcode rồi bấm Thêm: chỉ tải lượt mượn và thêm vào form, chưa ghi nhận trả.
2. Nếu báo mất, chọn tra cứu bằng mã phiếu hoặc MSSV/họ tên. Mở phiếu, chọn **Báo mất** ở đúng tài liệu; không cần cầm sách để quét. Từ danh sách phiếu BORROWING/OVERDUE cũng có liên kết sang quầy trả.
3. Mỗi lượt tối đa ba tài liệu, có thể từ nhiều phiếu. Chỉ chi tiết đang BORROWING/OVERDUE và chưa có ngày trả được thêm; tài liệu đã LOST/DAMAGED/SETTLED tiếp tục xử lý ở hồ sơ cũ.
4. Chọn tình trạng và nhập thông tin tương ứng:

| Tình trạng | Thông tin cần nhập |
|---|---|
| Bình thường | Ghi chú tùy chọn, không tạo phiếu phạt |
| Hỏng nhẹ | Mô tả, loại/ngôn ngữ tài liệu, chi phí sửa chữa dương; số trang tùy chọn, tối đa 10 |
| Hỏng nặng hoặc mất | Mô tả, loại/ngôn ngữ, xác minh còn lưu hành; còn lưu hành phải nộp sách thay thế |
| Không còn lưu hành | Chọn nộp sách thay thế hoặc đền bù tiền; tiền có thể nhập giá bìa, để trống/0 dùng biểu phí server |
| Ngoại văn có giá bìa, đền tiền | Thêm tỷ giá sang VNĐ và nguồn tỷ giá; không bắt tỷ giá khi không dùng giá bìa |

Đường dẫn chứng cứ http(s) và ghi chú là tùy chọn. Frontend kiểm tra dữ liệu, Backend quyết định biểu phí và điều kiện cuối cùng.

5. **Hủy lượt chưa gửi** chỉ xóa form. **Xác nhận tình trạng & ghi nhận** gửi một request inspection cho cả lượt; không trả thường rồi tạo phạt bằng request thứ hai.
6. Khi đang gửi, các trường và chuyển tab quầy bị khóa. Lỗi mạng/5xx hoặc phản hồi thiếu biên nhận: bấm **Thử lại lượt này**, giữ nguyên body và UUID. Không tự gửi khi mở lại trang. Request đang chờ được giữ trong sessionStorage theo tài khoản và cùng tab trình duyệt; không coi đây là lưu trữ qua đóng tab/đổi thiết bị. Nếu trình duyệt báo không lưu được, giữ trang để thử lại.
7. Khi server từ chối bằng 4xx xác định (trừ 408/429), có thể thử lại cùng lượt hoặc bấm **Chỉnh sửa lượt bị từ chối** để sửa và tạo key mới ở lần xác nhận kế. Với 409 đã có sự cố, tải lại phiếu/hồ sơ trước khi quyết định; không chuyển sang trả thường để vượt lỗi.
8. Biên nhận hiển thị số tiền server trả, mã/trạng thái hồ sơ, trạng thái tài liệu/bản sao, trạng thái phiếu và số dư từng bạn đọc. Mở liên kết hồ sơ để tiếp tục nghiệm thu sách thay thế/thu nợ. Đây là snapshot lúc ghi nhận; replay không cập nhật theo các khoản nộp sau đó.

## Bàn giao cho người làm Mobile

- Thêm caller staff `POST /loans/return-inspection` với kiểu request/response trong B02; tham khảo kiểu TS `web-app/src/app/services/return-inspection.models.ts`. Các API trả cũ vẫn còn tương thích nhưng không dùng cho lượt có sự cố.
- `LoanItem.status` là trạng thái phiếu; kiểm tra thêm `itemStatus` là trạng thái chi tiết. Không cho báo mất lần nữa chỉ vì phiếu còn BORROWING trong lúc chờ sách thay thế.
- Tách quét/tìm phiếu khỏi xác nhận. Báo mất gửi `loanDetailId`, không bắt buộc có sách trước camera. Cho chọn NORMAL/DAMAGED/LOST từng item; incidentType và loanDetailId trong penalty phải khớp item ngoài.
- Tạo UUID một lần khi xác nhận, lưu body gốc cùng key trước gửi, khóa thao tác khi chờ; retry cùng key/body. Khôi phục lượt chưa xác định kết quả trước khi tạo lượt mới. Có thể retry receipt đã lưu sau giờ đóng quầy; thao tác mới vẫn bị kiểm tra giờ.
- Tổng phí mới = tổng `lateFineAdded + penaltyAmount`; `lateFineTotal` là lũy kế, không cộng lần nữa. `depositApplied`, `debtAdded` là biến động của lượt; `balances.totalDebt` gồm cả nợ cũ. `penaltyOutstanding` chỉ thuộc hồ sơ đó.
- Dùng trạng thái server: NORMAL có thể trả vào RESERVED cho hàng đợi; LOST/MAINTENANCE không thông báo về kho. Sách thay thế chưa nghiệm thu vẫn WAITING_REPLACEMENT dù đã đủ phí.
- Liên kết Web: `/admin/circulation?referenceType=LOAN_TICKET&referenceId=<id>&mode=return`; hồ sơ `/admin/penalties?referenceType=PENALTY_CASE&referenceId=<id>`; công nợ `referenceType=USER&referenceId=<uuid>`.

## Nghiệm thu khi chạy với Backend thật

1. Backend đã có migration 28 và API B02. Dùng tài khoản thủ thư; không dùng fixture của test cho môi trường thật.
2. Quét/nhập tay tài liệu đang mượn, kiểm tra chưa phát sinh POST trước xác nhận; hủy không thay đổi trạng thái. Xác nhận NORMAL và kiểm tra AVAILABLE/RESERVED theo hàng đợi.
3. Hỏng nhẹ: thiếu mô tả/chi phí bị chặn; mức phạt/cọc/nợ trên receipt khớp ledger. Hỏng nặng/mất: bắt xác minh lưu hành, xử lý sách thay thế hoặc tiền đúng chính sách.
4. Báo mất từ phiếu và tìm theo MSSV/họ tên; không cần barcode vật lý. Chi tiết đã có sự cố không thêm được vào lượt khác.
5. Giả lập mất phản hồi sau gửi, thử lại và reload cùng tab: một operation/hồ sơ, key/body giữ nguyên, không cấn cọc hai lần. Replay giữ snapshot gốc dù công nợ đã thay đổi sau đó.
6. Một item bị lỗi khiến cả lượt rollback. Lỗi 409 không tự tạo thao tác khác; kiểm tra lại phiếu/hồ sơ. Phiếu còn tài liệu/sách thay thế chưa nghiệm thu không bị báo hoàn tất toàn bộ.
7. Kiểm tra camera và quyền camera trên thiết bị thật. Web đã kiểm tra bằng trình duyệt cô lập với API fixture; việc này không thay nghiệm thu xuyên suốt Backend thật hoặc Flutter.
