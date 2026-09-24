# API SYSTEM BLUEPRINT: HUIT LIBRARY MANAGEMENT (V41 FINAL)
> **MỤC ĐÍCH**: Bản thiết kế API đặc tả chi tiết Payload, DTO, Params để gen Code Controller và Swagger DOC chính xác 100%. Đáp ứng đủ 39 System Use Cases.

## 1. GLOBAL API STANDARDS
- DTO Validation: Yêu cầu sử dụng `jakarta.validation.constraints` (`@NotBlank`, `@NotNull`, `@Min`).
- Response Wrapper class `ApiResponse<T>`:
```json
{
  "code": 200,
  "message": "Thành công",
  "data": { ... } // Tùy payload
}
```
- Exception Response:
```json
{
  "code": 400,
  "message": "Sách đã bị khóa bởi người khác",
  "errorDetails": "ERR_BOOK_LOCKED"
}
```

## 2. ĐẶC TẢ CHI TIẾT ENDPOINTS

### 2.1. Module: Auth & Users (Đồng bộ Đào tạo, Thẻ thư viện)
- **POST `/api/v1/auth/login`**:
  - Request: `LoginRequestDTO` (email, password).
  - Response: `JwtResponseDTO` (accessToken, refreshToken, role).
- **POST `/api/v1/auth/logout`**: Yêu cầu Bearer token, sẽ save token vào Redis Blacklist.
- **POST `/api/v1/users/sync`** (System Use Case 39):
  - Kích hoạt gọi API sang hệ thống Đào tạo (thông qua `RestTemplate` hoặc `WebClient`) dựa trên `studentId` để đồng bộ `department`, `fullName`.
- **PUT `/api/v1/users/{id}/activate-card`** (System Use Case 4): Kích hoạt thẻ, cập nhật `cardStatus = ACTIVE`, `cardExpiryDate = now() + 1 year`.

### 2.2. Module: Catalog & Search (Elasticsearch & AI)
- **GET `/api/v1/books`** (System Use Case 5):
  - Params: `q` (keyword - search fuzzy qua Elasticsearch), `category`, `page`, `size`.
  - Response: `Page<BookResponseDTO>` (có title, isbn, ddcCallNumber).
- **GET `/api/v1/books/recommendations`** (System Use Case 26):
  - Header: Authorization (để lấy userId).
  - Logic: `BookService` gọi `AiRecommendationClient.fetchRecommendations(userId)`, nhận mảng `bookId`, rồi query vào Postgres để trả chi tiết sách.
  
### 2.3. Module: Circulation (Mượn / Trả / Phạt)
- **POST `/api/v1/loans/checkout`** (System Use Cases 6, 9, 10):
  - Body: `CheckoutRequestDTO` (Danh sách `barcodes`, `ticketType`: ONSITE/BORROW).
  - Logic (Quan trọng): 
    1. Check `User.disciplineFlag` == false.
    2. Check `Wallet.depositBalance` >= Tổng giá sách mượn (nếu mượn về nhà).
    3. `BookCopyRepo` chạy Pessimistic Lock khóa các barcodes.
- **POST `/api/v1/loans/return`** (System Use Cases 12, 13, 23):
  - Body: `ReturnRequestDTO` (`barcode`, `returnConditionNote`).
  - Logic: Giải phóng `BookCopy`. Kích hoạt `PenaltyStrategy`. Nếu trễ hạn hoặc hư hỏng (so sánh Condition), sinh ra `Transaction` phạt nợ.
- **POST `/api/v1/waitlist/join`** (System Use Case 36):
  - Body: `WaitlistRequestDTO` (`bookId`).

### 2.4. Module: Finance & Transactions
- **POST `/api/v1/transactions/pay`** (System Use Case 14, 15):
  - Body: `PaymentRequestDTO` (`transactionId`, `method`).
  - Logic: Trừ nợ trong `WalletEntity.totalDebt`, set Transaction status = PAID.
- **POST `/api/v1/transactions/deposit-refund`** (System Use Case 19):
  - Logic: Rút tiền thế chân. Yêu cầu update `parentTransactionId` để trace log kế toán.

### 2.5. Module: Facilities (Rooms)
- **POST `/api/v1/rooms/book`** (System Use Case 17):
  - Body: `RoomBookingDTO` (`roomId`, `startTime`, `endTime`, `memberStudentIds`).
- **POST `/api/v1/rooms/check-in`** (System Use Case 18): Đánh dấu `actualStartTime`. Quá 15p không check-in -> Tăng `roomViolationCount` của User.

### 2.6. Module: Inventory & Operations (Kiểm kê, POS, Nhập kho)
- **POST `/api/v1/inventory/shift-handover`** (System Use Case 38):
  - Body: `ShiftHandoverDTO` (`posTerminalId`, `startingCash`, `actualCash`).
  - Logic: Hệ thống tự tính `systemRevenue`. `discrepancyAmount = actualCash - (startingCash + systemRevenue)`. Lưu vào `ShiftHandoverEntity`.
- **POST `/api/v1/inventory/receipts`** (System Use Case 33):
  - Nhập sách mới từ Supplier (Tạo GoodsReceipt, sau đó gen tự động barcodes cho BookCopy).
- **POST `/api/v1/inventory/audits`** (System Use Case 37): Kiểm kê định kỳ.

### 2.7. Module: Interactive (Reviews, Complaints, Proposals)
- **POST `/api/v1/feedback/complaints`** (System Use Case 31): Lưu vào `ComplaintEntity`.
- **POST `/api/v1/feedback/proposals`** (System Use Case 30): Lưu vào `BookProposalEntity`.
- **POST `/api/v1/books/{id}/reviews`** (System Use Case 27): Cập nhật Rating (1-5 stars).

### 2.8. Module: WebSocket Notifications (System Use Case 22)
- Cấu hình: STOMP Protocol. Connect tới `/ws-library`.
- Backend tự động push message tới: `/user/{userId}/queue/alerts`.
- Ví dụ logic: Job chạy ngầm lúc 0h đêm kiểm tra `LoanDetailEntity.dueDate`, nếu sắp hạn, gọi `SimpMessagingTemplate.convertAndSendToUser(...)`.
