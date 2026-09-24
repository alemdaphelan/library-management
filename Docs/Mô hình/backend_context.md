# BACKEND SYSTEM BLUEPRINT: HUIT LIBRARY MANAGEMENT (V41 FINAL)
> **MỤC ĐÍCH**: Bản đặc tả toàn diện (Comprehensive Specification) này dùng để feed cho Antigravity IDE build ra hệ thống CHUẨN XÁC ĐẾN TỪNG DÒNG CODE logic, không chỉ là khung khởi tạo (stub). Bám sát 100% 6 file (Business Use Cases, System Use Cases, Technologies, ERD, Analysis, Design).

## 1. TECHNICAL STACK & DEPENDENCIES (pom.xml / build.gradle)
- **Core**: Java 17, Spring Boot 3.2+
- **Database**: `spring-boot-starter-data-jpa` (PostgreSQL), `liquibase-core` (Migration)
- **Polyglot**: 
  - `spring-boot-starter-data-mongodb` (Event Log / Telemetry)
  - `spring-boot-starter-data-elasticsearch` (Full-text Search Books)
  - `spring-boot-starter-data-redis` (JWT Blacklist, Caching)
- **Messaging & Real-time**: `spring-kafka`, `spring-boot-starter-websocket` (STOMP)
- **External Services**: `spring-cloud-starter-openfeign` (Call AI Service)
- **Security**: `spring-boot-starter-security`, `java-jwt` (Auth0)
- **Mapper & Utils**: MapStruct, Lombok

## 2. PROJECT STRUCTURE (Chuẩn N-Tier / Modular Monolith)
```text
com.huit.library
├── config/             # Cấu hình Security (JWT Filter), RedisCache, KafkaTopic, FeignClient, WebSocketMessageBroker, ElasticConfig
├── core/               # Chứa các Base class dùng chung
│   ├── entity/         # BaseEntity (chứa id, createdAt, updatedAt)
│   ├── exception/      # GlobalExceptionHandler, Custom Exceptions (ResourceNotFound, BusinessLogicException)
│   └── response/       # ApiResponse<T>
├── modules/            # Tách biệt theo Domain Driven Design
│   ├── auth/           # AuthController, UserService, UserRepository, UserEntity, RedisTokenStore
│   ├── catalog/        # BookController, BookService, BookElasticRepository, BookEntity, Category, Publisher, Author
│   ├── circulation/    # LoanController, LoanService, LoanTicketEntity, LoanDetailEntity, Waitlist, PenaltyStrategy
│   ├── finance/        # PaymentController, WalletEntity, TransactionEntity
│   ├── facilities/     # RoomController, RoomBookingEntity, Kiosk
│   ├── inventory/      # ShiftController, GoodsReceipt, Supplier, InventoryAudit
│   ├── interactive/    # Review, Complaint, BookProposal
│   └── digital/        # DigitalDocument, DigitalUsageLog
└── integration/        # External Clients
    ├── ai/             # AiRecommendationClient (FeignClient)
    └── sync/           # UniversitySyncService
```

## 3. DOMAIN ENTITIES ĐẶC TẢ CHI TIẾT (Khớp 100% 35 Bảng ERD V41)
*Chú ý: Antigravity IDE khi gen code Entity phải tạo đủ các trường sau, không được thiếu sót.*

### 3.1. Nhóm Users & Auth
- **UserEntity**: `userId` (UUID), `roleId`, `passwordHash`, `isFirstLogin`, `fullName`, `studentId` (MSSV - quan trọng cho Sync), `department` (Khoa), `email`, `phone`, `userType`, `cardStatus`, `cardExpiryDate`, `disciplineFlag` (Cờ vi phạm), `roomViolationCount`.
- **WalletEntity**: `walletId`, `userId`, `depositBalance`, `totalDebt`.

### 3.2. Nhóm Catalog (Elasticsearch & JPA)
- **BookEntity (JPA)**: `bookId`, `title`, `isbn`, `ddcCallNumber` (Mã xếp giá), `publishYear`, `language`, `labelColor`, `defaultPrice`, `isDigital`.
- **BookDocument (Elasticsearch)**: Lưu trữ các index field: `bookId`, `title`, `authorNames`, `isbn`, để search fuzzy tốc độ cao.
- **BookCopyEntity**: `barcode` (ID vật lý), `bookId`, `conditionNote`, `lockedByUser` (Pessimistic Lock khi đang checkout), `lockedUntil`.

### 3.3. Nhóm Circulation (Mượn trả & Phạt)
- **LoanTicketEntity**: `ticketId`, `userId`, `ticketType` (Tại chỗ/Mang về), `status`, `createdAt`.
- **LoanDetailEntity**: `detailId`, `ticketId`, `barcode`, `checkoutConditionNote`, `returnConditionNote` (Bắt bẫy rách trang), `returnStatus`, `dueDate`, `returnDate`, `renewalCount`, `isRecalled`, `lateFineAccrued`.
- **TransactionEntity**: `transactionId`, `parentTransactionId` (Dành cho hoàn tiền), `adminRef`, `proofDocumentUrl`, `amount`, `type`.

### 3.4. Nhóm Interactive & Inventory (Các bảng vá V41)
- **ShiftHandoverEntity**: `shiftId`, `posTerminalId`, `startingCash`, `systemRevenue`, `actualCash`, `discrepancyAmount` (Lệch tiền - bắt lỗi thủ quỹ), `status`.
- **GoodsReceiptEntity** & **ReceiptDetailEntity**: Nhập kho từ `Supplier`.
- **ComplaintEntity**, **BookProposalEntity**, **ReviewEntity**, **WaitlistEntity**: (Các trường chuẩn theo ERD).

### 3.5. Nhóm Event Log (MongoDB)
- **UserEventDocument (Mongo)**: `logId`, `userId`, `eventType` (VIEW, CLICK, LOAN), `itemId`, `timestamp`. Dữ liệu này Kafka sẽ consume và đổ vào Mongo.

## 4. CHI TIẾT IMPLEMENTATION BUSINESS LOGIC (DESIGN PATTERNS)

### 4.1. Pattern 1: Strategy Pattern (Tính phí phạt)
Hệ thống sử dụng interface `PenaltyStrategy` với method `calculateFine(LoanDetailEntity)`.
- **OverduePenaltyImpl**: `(Ngày trả thực - Ngày hết hạn) * Đơn giá phạt / ngày`.
- **DamagePenaltyImpl**: Dựa vào so sánh `checkoutConditionNote` và `returnConditionNote`. Nếu lúc mượn "Nguyên vẹn", lúc trả "Rách trang", phạt = `Book.defaultPrice * tỷ lệ hư hỏng`.

### 4.2. Pattern 2: Pessimistic Locking (Chống Double-Booking)
Khi Sinh viên A và B cùng ấn mượn 1 cuốn sách (cùng 1 `barcode`) trên Web:
- `BookCopyRepository` sử dụng `@Lock(LockModeType.PESSIMISTIC_WRITE)` trên method `findByBarcode()`. Đảm bảo chỉ 1 thread được update `lockedByUser`.

### 4.3. Polyglot & Messaging Architecture
- **Kafka Producer**: Mọi hành vi `loanBooks()`, `searchBooks()` đều gọi `kafkaEventPublisher.publish("library.telemetry", event)`.
- **AI Feign Client**: Interface `@FeignClient(name = "ai-service", url = "${ai.service.url}")` định nghĩa `List<String> getRecommendations(@PathVariable String userId)`.
- **WebSockets / STOMP**: Spring config `@EnableWebSocketMessageBroker`. Client subscribe `/user/queue/alerts`. Spring dùng `SimpMessagingTemplate` để push thông báo (VD: Sách trong Waitlist đã có người trả).
- **Redis Cache**: TTL = 30 phút. `@Cacheable(value = "book_details", key = "#id")`. JWT khi Logout sẽ bị đẩy vào Redis (blacklist) với TTL = thời gian sống còn lại của token.
