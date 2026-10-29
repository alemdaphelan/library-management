# Hợp đồng kiểm tra trả sách và lập hồ sơ mất hỏng

API dành cho **ADMIN/LIBRARIAN**: `POST /loans/return-inspection`, JSON và JWT như các API quầy hiện có. Đây là đầu vào cho form B03. Trong B02 chưa thay giao diện Web/Mobile; client đang trả bình thường qua `/loans/return` vẫn dùng được.

Cập nhật B03 ngày 29/10/2026: Web đã dùng form xác nhận tình trạng và endpoint này. Hướng dẫn thao tác, retry và bàn giao Mobile ở [HUONG_DAN_B03_QUAY_TRA_SACH.md](HUONG_DAN_B03_QUAY_TRA_SACH.md). Flutter chưa được nâng cấp trong B03.

## Quy tắc một thao tác

- Một yêu cầu có 1–3 tài liệu, nhận diện bằng `loanDetailId`. Quầy lấy ID từ chi tiết phiếu hoặc `/loans/active-item/{barcode}`. Báo mất có thể chọn từ phiếu, không cần có sách vật lý để quét.
- `condition`: `NORMAL`, `DAMAGED`, `LOST`. Với DAMAGED/LOST phải gửi `penalty` theo DTO hồ sơ phạt đang có. `penalty.loanDetailId` phải khớp ID ngoài và `penalty.incidentType` phải khớp condition.
- NORMAL không có penalty. DAMAGED dùng `damageSeverity=LIGHT/HEAVY`. LIGHT dùng REPAIR với repairFee dương, tối đa 10 trang ảnh hưởng nếu có khai báo. HEAVY được xử lý như mất theo chính sách hiện có.
- LOST/HEAVY phải khai báo rõ `stillInMarket`; còn lưu hành thì REPLACEMENT. CASH áp dụng theo điều kiện chính sách hiện có: giá bìa/hệ số/ngôn ngữ/tỷ giá hoặc biểu phí có hiệu lực. Thiếu biểu phí thì trả 409, không đoán giá.
- Không gọi `/loans/return` trước khi gọi API này cho tài liệu có sự cố. Dịch vụ sẽ tính phí trễ, lập hồ sơ và ghi sổ nợ trong cùng transaction.
- Thành công là ghi nhận kiểm tra, không phải mọi sách đã trở lại kho. NORMAL thành RETURNED; bản sao có thể AVAILABLE hoặc RESERVED cho người đầu waitlist. Mất/hỏng giữ LOST/MAINTENANCE, ngừng tính phí trễ tại thời điểm báo, không cấp lại cho hàng đợi.
- Phiếu còn sự cố chưa khép hoặc còn tài liệu chưa trả thì giữ trạng thái đang mượn/quá hạn. Tiền đã đủ nhưng chưa nhận sách thay thế vẫn WAITING_REPLACEMENT; không tự ép RETURNED. Khép hồ sơ tiếp tục qua API xử lý phạt hiện có.

## Ví dụ trả bình thường cùng báo mất

Các ID dưới đây chỉ minh họa; dùng ID có thật ở môi trường chạy.

```json
{
  "idempotencyKey": "UUID-do-client-tao-cho-mot-lan-xac-nhan",
  "items": [
    {
      "loanDetailId": 101,
      "condition": "NORMAL",
      "returnConditionNote": "Tình trạng nguyên vẹn"
    },
    {
      "loanDetailId": 102,
      "condition": "LOST",
      "returnConditionNote": "Bạn đọc báo mất tại quầy",
      "penalty": {
        "loanDetailId": 102,
        "incidentType": "LOST",
        "description": "Bạn đọc báo mất tài liệu đang mượn",
        "evidenceUrl": null,
        "materialType": "BOOK",
        "languageCategory": "VIETNAMESE",
        "stillInMarket": false,
        "settlementMethod": "CASH",
        "coverPrice": 30000,
        "originalCurrency": "VND"
      }
    }
  ]
}
```

Ví dụ hỏng nhẹ: thay item thứ hai bằng cấu trúc sau.

```json
{
  "loanDetailId": 102,
  "condition": "DAMAGED",
  "returnConditionNote": "Rách 2 trang",
  "penalty": {
    "loanDetailId": 102,
    "incidentType": "DAMAGED",
    "damageSeverity": "LIGHT",
    "pagesAffected": 2,
    "description": "Rách trang, cần sửa chữa",
    "materialType": "BOOK",
    "languageCategory": "VIETNAMESE",
    "settlementMethod": "REPAIR",
    "repairFee": 200000
  }
}
```

Biểu phí không được tính lại ở client. Ví dụ hỏng nhẹ 200.000 và cọc khả dụng 50.000, không có phí trễ mới, thì Backend ghi 200.000 nghĩa vụ mới, cấn 50.000, tăng nợ 150.000. Những khoản nợ trước đó vẫn tồn tại trong số dư cuối của bạn đọc.

## Nội dung response

HTTP 200 trả một biên nhận gồm:

| Trường | Ý nghĩa |
|---|---|
| `operationId` | UUID của thao tác đã lưu |
| `operationType` | `RETURN_INSPECT` |
| `processedAt` | Thời điểm lập biên nhận gốc |
| `replayed` | false ở lần đầu, true khi gửi lại cùng key/nội dung |
| `items` | Kết quả từng tài liệu, theo thứ tự tăng loanDetailId |
| `tickets` | ticketId và trạng thái thực tế sau thao tác |
| `balances` | userId, depositBalance, totalDebt tại thời điểm lập biên nhận |

Mỗi phần tử `items` có các trường sau:

| Trường | Ý nghĩa |
|---|---|
| `loanDetailId`, `ticketId`, `userId`, `barcode` | Liên kết đến tài liệu/phiếu/bạn đọc, luôn lấy từ DB |
| `condition`, `detailStatus`, `copyStatus` | Tình trạng khai báo và trạng thái ghi nhận thật; không suy ra rằng mọi kết quả đều AVAILABLE |
| `penaltyCaseId`, `penaltyStatus` | Mã/trạng thái hồ sơ phạt; null với NORMAL |
| `lateFineAdded` | Phí trễ mới phát sinh trong thao tác này |
| `lateFineTotal` | Phí trễ lũy kế đã ghi nhận của tài liệu, gồm cả phần cũ |
| `penaltyAmount` | Tổng phí sửa chữa/đền bù/kỹ thuật của hồ sơ mới, không cộng phí trễ vào đây |
| `depositApplied` | Cọc đã cấn trong thao tác cho tài liệu này, gồm cả phí trễ mới và hồ sơ phạt |
| `debtAdded` | Mức tăng nợ trong thao tác cho tài liệu này sau cấn cọc |
| `penaltyOutstanding` | Phần còn phải thanh toán của riêng hồ sơ phạt, không phải toàn bộ công nợ bạn đọc |
| `returnDate`, `fineStoppedAt` | Ngày hoàn tất trả hoặc mốc dừng phí; sự cố chưa giải quyết không bị gán ngày trả thường |

UI cần ghép kết quả bằng `loanDetailId`, không dựa vào thứ tự người dùng quét. Tổng nghĩa vụ mới = tổng `lateFineAdded + penaltyAmount`; không cộng `lateFineTotal` vào một lần nữa. Dư nợ toàn bạn đọc xem tại `balances.totalDebt`, có thể bao gồm khoản cũ và các tài liệu khác.

## Retry và tính nhất quán

1. Tạo UUID mới khi bắt đầu một lần xác nhận. Giữ nguyên key và dữ liệu nếu lỗi mạng hoặc chưa biết kết quả. Khóa nút gửi khi request đang chạy.
2. Gửi lại cùng key/nội dung nhận lại **biên nhận gốc**, giữ nguyên mã hồ sơ, tiền, trạng thái và thời điểm; chỉ `replayed` đổi thành true. Phí/cọc/thông báo không được tạo lần nữa.
3. Đảo thứ tự items hoặc khác cách viết số tiền `200000`/`200000.00` không tạo một thao tác khác. Đổi tài liệu, tình trạng, ghi chú hoặc dữ liệu phạt với key cũ trả 409.
4. Một key dùng chung namespace với các thao tác circulation cũ; không tái sử dụng key checkout/return cho inspection.
5. Biên nhận là snapshot. Nếu sau đó bạn đọc nộp tiền hoặc hồ sơ đã khép, replay vẫn trả snapshot gốc. Muốn xem hiện trạng phải tải lại chi tiết phiếu/hồ sơ và sổ nợ.
6. Nếu một tài liệu lỗi, toàn bộ lượt rollback: trạng thái sách/phiếu, phí trễ, hồ sơ phạt, cọc/nợ, thông báo và promotion waitlist. Không có kết quả một phần.
7. Gửi key khác cho tài liệu đã có sự cố không lập thêm hồ sơ: trả 409. Sau xung đột đồng thời, tải lại hoặc thử cùng key/nội dung gốc; không tự chuyển sang trả thường.
8. Replay đã lưu được trả cả khi quầy đã đóng. Thao tác mới vẫn phải trong giờ nhận trả như luồng quầy hiện tại.

## Lỗi và dữ liệu lưu

| HTTP | Tình huống |
|---|---|
| 400 | Thiếu trường, danh sách rỗng/quá ba/trùng ID, condition không hợp lệ, phiếu phạt sai ID/loại, thiếu severity hoặc cách xử lý không phù hợp |
| 403 | Role Sinh viên/role không được phép dùng quầy |
| 404 | Chi tiết/phiếu/bạn đọc cần xử lý không tồn tại |
| 409 | Đã trả/đã có sự cố, trạng thái bản sao không hợp lệ, thiếu biểu phí, key dùng cho nội dung khác hoặc xung đột transaction |

Migration **28-return-inspection-receipts.yaml** thêm cột TEXT nullable `circulation_operations.result_payload` để replay đầy đủ. Bản ghi checkout/return cũ không cần backfill. Payload inspection được lưu trong cùng transaction với thay đổi nghiệp vụ; nếu lưu biên nhận lỗi thì cũng rollback. Không cập nhật/xóa migration đã áp dụng.

Các file triển khai chính: `ReturnInspectionController`, `ReturnInspectionService`, `ReturnInspectionRequestDTO`, `ReturnInspectionResponseDTO`; dùng lại `PenaltyCaseService` và các helper trả thường/khép phiếu của `CirculationStateService`. Luồng trả cũ và inspection đều khóa ticket -> detail -> copy, tránh nạp entity thay đổi được trước khi chờ khóa. B03 chỉ cần tích hợp hợp đồng này vào quầy và giữ các API xử lý hồ sơ phạt hiện có.
