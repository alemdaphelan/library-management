package com.huit.library.integration;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.auth.service.AuthService;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.circulation.dto.CheckoutRequestDTO;
import com.huit.library.modules.circulation.dto.ReturnInspectionRequestDTO;
import com.huit.library.modules.circulation.dto.ReturnInspectionResponseDTO;
import com.huit.library.modules.circulation.entity.WaitlistEntity;
import com.huit.library.modules.circulation.dto.ReturnRequestDTO;
import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.repository.CirculationOperationRepository;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanStatusHistoryRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.circulation.service.CirculationStateService;
import com.huit.library.modules.circulation.service.CirculationMaintenanceResult;
import com.huit.library.modules.circulation.service.CirculationMaintenanceService;
import com.huit.library.modules.finance.repository.TransactionRepository;
import com.huit.library.modules.finance.repository.PaymentAllocationRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.finance.dto.DebtPaymentRequestDTO;
import com.huit.library.modules.finance.dto.PaymentReceiptDTO;
import com.huit.library.modules.finance.service.DebtLedgerService;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.penalty.dto.ReplacementAcceptanceRequestDTO;
import com.huit.library.modules.penalty.dto.PenaltyCaseRequestDTO;
import com.huit.library.modules.penalty.entity.PenaltyCaseEntity;
import com.huit.library.modules.penalty.repository.PenaltyCaseRepository;
import com.huit.library.modules.penalty.service.PenaltyCaseService;
import com.huit.library.modules.notification.repository.NotificationRepository;
import com.huit.library.modules.notification.dto.NotificationPreferenceRequestDTO;
import com.huit.library.modules.notification.repository.NotificationPreferenceRepository;
import com.huit.library.modules.notification.service.NotificationPreferenceService;
import com.huit.library.modules.notification.service.NotificationService;
import com.huit.library.modules.inventory.dto.GoodsReceiptResponseDTO;
import com.huit.library.modules.inventory.dto.ImportReceiptRequestDTO;
import com.huit.library.modules.inventory.dto.InventoryAuditResponseDTO;
import com.huit.library.modules.inventory.dto.InventoryScanRequestDTO;
import com.huit.library.modules.inventory.entity.SupplierEntity;
import com.huit.library.modules.inventory.repository.GoodsReceiptRepository;
import com.huit.library.modules.inventory.repository.InventoryAuditRepository;
import com.huit.library.modules.inventory.repository.InventoryDetailRepository;
import com.huit.library.modules.inventory.repository.ReceiptDetailRepository;
import com.huit.library.modules.inventory.repository.SupplierRepository;
import com.huit.library.modules.inventory.service.InventoryService;
import com.huit.library.modules.inventory.service.InventoryAuditService;
import com.huit.library.modules.digital.dto.DigitalAssetResponseDTO;
import com.huit.library.modules.digital.repository.DigitalDocumentRepository;
import com.huit.library.modules.digital.repository.DigitalUsageLogRepository;
import com.huit.library.modules.digital.service.DigitalAssetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.data.mongodb.repositories.enabled=false",
        "spring.data.elasticsearch.repositories.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=none",
        "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=drop-and-create",
        "app.database-seeding.enabled=false",
        "library.rag.enabled=false",
        "library.circulation.scheduler-cron=0 0 0 1 1 *",
        "app.jwtSecret=week-6-integration-test-secret-key-with-more-than-32-characters"
})
class Week6IntegrationTest {
    private static final Path DIGITAL_TEST_STORAGE = Path.of(
            System.getProperty("java.io.tmpdir"), "huit-week7-digital-" + UUID.randomUUID());

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("huit_library_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static final GenericContainer<?> MAILPIT = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.20"))
            .withExposedPorts(1025, 8025);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
        registry.add("spring.mail.properties.mail.smtp.auth", () -> false);
        registry.add("spring.mail.properties.mail.smtp.ssl.enable", () -> false);
        registry.add("library.digital.storage-path", DIGITAL_TEST_STORAGE::toString);
        // Keep reservation scenarios deterministic regardless of the CI runner's clock.
        registry.add("library.circulation.default-open-time", () -> "00:00");
        registry.add("library.circulation.default-close-time", () -> "23:59:59.999999999");
    }

    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager inventoryTransactionManager;
    @Autowired
    private com.huit.library.modules.inventory.service.CatalogIdentificationService inventoryIdentification;
    @Autowired
    private InventoryAuditService inventoryAuditService;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private GoodsReceiptRepository goodsReceiptRepository;
    @Autowired
    private com.huit.library.modules.dashboard.service.FinanceStockAnalyticsService financeStockAnalytics;
    @Autowired
    private ReceiptDetailRepository receiptDetailRepository;
    @Autowired
    private InventoryAuditRepository inventoryAuditRepository;
    @Autowired
    private InventoryDetailRepository inventoryDetailRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private BookCopyRepository bookCopyRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private CirculationStateService circulationStateService;
    @Autowired
    private com.huit.library.modules.circulation.service.LoanService loanService;
    @Autowired
    private LoanTicketRepository loanTicketRepository;
    @Autowired
    private LoanDetailRepository loanDetailRepository;
    @Autowired
    private LoanStatusHistoryRepository loanStatusHistoryRepository;
    @Autowired
    private CirculationOperationRepository circulationOperationRepository;
    @Autowired
    private CirculationMaintenanceService circulationMaintenanceService;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private NotificationPreferenceRepository notificationPreferenceRepository;
    @Autowired
    private NotificationPreferenceService notificationPreferenceService;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private PenaltyCaseService penaltyCaseService;
    @Autowired
    private DebtLedgerService debtLedgerService;
    @Autowired
    private PenaltyCaseRepository penaltyCaseRepository;
    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;
    @Autowired
    private DigitalAssetService digitalAssetService;
    @Autowired
    private DigitalDocumentRepository digitalDocumentRepository;
    @Autowired
    private DigitalUsageLogRepository digitalUsageLogRepository;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.huit.library.modules.circulation.service.ReturnInspectionService returnInspectionService;
    @Autowired
    private com.huit.library.modules.circulation.repository.WaitlistRepository inspectionWaitlists;

    @BeforeEach
    void clearOtpState() {
        Set<String> keys = redisTemplate.keys("auth:otp:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void week7DigitalAssetPersistsFileMetadataAndUsageOnPostgres() {
        UserEntity librarian = createUser("librarian-digital");
        BookEntity book = createBook();
        Long documentId = null;
        try {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "week7.pdf", "application/pdf", "%PDF-week7".getBytes());

            DigitalAssetResponseDTO uploaded = digitalAssetService.upload(
                    file, book.getBookId(), "AUTHENTICATED", librarian.getUserId());
            documentId = uploaded.id();
            DigitalAssetService.DigitalDownload download = digitalAssetService.download(
                    uploaded.id(), librarian.getUserId(), true);

            assertThat(digitalDocumentRepository.findById(uploaded.id())).isPresent();
            assertThat(download.resource().exists()).isTrue();
            assertThat(digitalUsageLogRepository.findAll()).anyMatch(log ->
                    uploaded.id().equals(log.getDocumentId())
                            && librarian.getUserId().equals(log.getUserId()));
        } finally {
            digitalUsageLogRepository.deleteAll();
            if (documentId != null && digitalDocumentRepository.existsById(documentId)) {
                digitalAssetService.delete(documentId);
            }
            bookRepository.deleteById(book.getBookId());
            userRepository.deleteById(librarian.getUserId());
        }
    }

    @Test
    void week9LossCaseAndPartialPaymentPersistItemizedReceiptTransactionally() {
        UserEntity reader = createUser("reader-week9-policy");
        BookEntity book = createBook();
        BookCopyEntity copy = null;
        LoanTicketEntity ticket = null;
        Long paymentTransactionId = null;
        try {
            copy = createCopy(book.getBookId(), "LOSS-" + UUID.randomUUID(), "BORROWING", null);
            ticket = createLoan(reader.getUserId(), copy.getBarcode(), "BORROWING", "BORROWING");
            LoanDetailEntity detail = loanDetailRepository.findByTicketId(ticket.getTicketId()).get(0);
            detail.setDueDate(LocalDateTime.now().minusDays(2));
            detail.setLateFineAccrued(BigDecimal.ZERO);
            detail = loanDetailRepository.saveAndFlush(detail);

            PenaltyCaseRequestDTO caseRequest = new PenaltyCaseRequestDTO();
            caseRequest.setLoanDetailId(detail.getDetailId());
            caseRequest.setIncidentType("LOST");
            caseRequest.setDescription("Báo mất sau hạn trả");
            caseRequest.setMaterialType("BOOK");
            caseRequest.setLanguageCategory("VIETNAMESE");
            caseRequest.setStillInMarket(false);
            caseRequest.setSettlementMethod("CASH");
            caseRequest.setCoverPrice(new BigDecimal("100000"));

            PenaltyCaseEntity penaltyCase = penaltyCaseService.createCase(caseRequest, reader.getUserId());
            assertThat(penaltyCase.getCompensationAmount()).isEqualByComparingTo("500000");
            assertThat(penaltyCase.getTechnicalFee()).isEqualByComparingTo("50000");
            assertThat(loanDetailRepository.findById(detail.getDetailId()).orElseThrow().getFineStoppedAt())
                    .isNotNull();

            DebtPaymentRequestDTO paymentRequest = new DebtPaymentRequestDTO();
            paymentRequest.setUserId(reader.getUserId());
            paymentRequest.setAmount(new BigDecimal("100000"));
            paymentRequest.setMethod("BANK_TRANSFER");
            paymentRequest.setIdempotencyKey("week9-payment-" + UUID.randomUUID());
            PaymentReceiptDTO receipt = debtLedgerService.recordPayment(paymentRequest, reader.getUserId());
            paymentTransactionId = receipt.getPaymentTransactionId();

            assertThat(receipt.getAllocations()).isNotEmpty();
            assertThat(receipt.getAllocations().stream()
                    .map(PaymentReceiptDTO.AllocationLine::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100000");
            assertThat(paymentAllocationRepository
                    .findByPaymentTransactionIdOrderByAllocationId(paymentTransactionId)).isNotEmpty();
            assertThat(walletRepository.findByUserId(reader.getUserId()).orElseThrow().getTotalDebt())
                    .isPositive();
        } finally {
            if (paymentTransactionId != null) {
                paymentAllocationRepository.deleteAll(paymentAllocationRepository
                        .findByPaymentTransactionIdOrderByAllocationId(paymentTransactionId));
            }
            notificationRepository.deleteAll(
                    notificationRepository.findByUserIdOrderByCreatedAtDesc(reader.getUserId()));
            transactionRepository.deleteAll(transactionRepository.findByUserIdOrderByCreatedAtAsc(reader.getUserId()));
            penaltyCaseRepository.deleteAll(penaltyCaseRepository.findByUserIdOrderByCreatedAtDesc(reader.getUserId()));
            walletRepository.findByUserId(reader.getUserId()).ifPresent(walletRepository::delete);
            cleanupCirculation(ticket, copy, book, List.of());
            userRepository.deleteById(reader.getUserId());
        }
    }

    @Test
    void inventoryImportPersistsReceiptDetailsAndBookCopies() {
        SupplierEntity supplier = createSupplier();
        BookEntity book = createBook();
        GoodsReceiptResponseDTO receipt = null;
        try {
            ImportReceiptRequestDTO request = importRequest(supplier.getSupplierId(),
                    List.of(line(book.getBookId(), 2, "125000")));

            receipt = inventoryService.importBooks(request);

            assertThat(receipt.getQuantity()).isEqualTo(2);
            assertThat(goodsReceiptRepository.findById(receipt.getReceiptId()).orElseThrow().getCompletedAt()).isNotNull();
            assertThat(receipt.getTotalAmount()).isEqualByComparingTo("250000");
            assertThat(receiptDetailRepository.findByReceiptId(receipt.getReceiptId())).hasSize(1);
            assertThat(bookCopyRepository.findByBookId(book.getBookId())).hasSize(2);
            var reread = inventoryService.getReceipt(receipt.getReceiptId());
            assertThat(reread.getLines()).hasSize(1);
            assertThat(reread.getLines().get(0).barcodes()).hasSize(2);
            assertThat(reread.getLines().get(0).locationPath()).contains("Tầng nhập", "Kệ nhập", "Ngăn nhập");
            Long importedShelf = reread.getLines().get(0).shelfId();
            assertThat(bookCopyRepository.findByBookId(book.getBookId())).allMatch(copy -> copy.getShelf().getShelfId().equals(importedShelf));
        } finally {
            if (receipt != null) {
                receiptDetailRepository.deleteAll(receiptDetailRepository.findByReceiptId(receipt.getReceiptId()));
                goodsReceiptRepository.deleteById(receipt.getReceiptId());
            }
            bookCopyRepository.deleteAll(bookCopyRepository.findByBookId(book.getBookId()));
            bookRepository.deleteById(book.getBookId());
            supplierRepository.deleteById(supplier.getSupplierId());
        }
    }

    @Test
    void inventoryImportRollsBackEveryWriteWhenOneLineIsInvalid() {
        SupplierEntity supplier = createSupplier();
        BookEntity book = createBook();
        long receiptsBefore = goodsReceiptRepository.count();
        long detailsBefore = receiptDetailRepository.count();
        long copiesBefore = bookCopyRepository.count();
        try {
            ImportReceiptRequestDTO request = importRequest(supplier.getSupplierId(), List.of(
                    line(book.getBookId(), 1, "100000"),
                    line(Long.MAX_VALUE, 1, "200000")));

            assertThatThrownBy(() -> inventoryService.importBooks(request))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                    .hasMessageContaining("Không tìm thấy đầu sách");

            assertThat(goodsReceiptRepository.count()).isEqualTo(receiptsBefore);
            assertThat(receiptDetailRepository.count()).isEqualTo(detailsBefore);
            assertThat(bookCopyRepository.count()).isEqualTo(copiesBefore);
        } finally {
            bookCopyRepository.deleteAll(bookCopyRepository.findByBookId(book.getBookId()));
            bookRepository.deleteById(book.getBookId());
            supplierRepository.deleteById(supplier.getSupplierId());
        }
    }

    @Test
    void inventoryAuditPersistsScansAndCompletedTotals() {
        UserEntity conductor = new UserEntity();
        conductor.setEmail("audit-" + UUID.randomUUID() + "@test.local");
        conductor.setFullName("Inventory Audit Test");
        conductor.setPasswordHash("not-used");
        conductor = userRepository.saveAndFlush(conductor);
        BookEntity book = createBook();
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode("AUDIT-" + UUID.randomUUID());
        copy.setBookId(book.getBookId());
        copy.setStatus("AVAILABLE");
        copy = bookCopyRepository.saveAndFlush(copy);
        InventoryAuditResponseDTO audit = null;
        try {
            audit = inventoryAuditService.createAudit(conductor);
            InventoryScanRequestDTO request = new InventoryScanRequestDTO();
            request.setBarcode(copy.getBarcode());

            assertThat(inventoryAuditService.scan(audit.getAuditId(), request).getScanStatus())
                    .isEqualTo("FOUND");
            InventoryAuditResponseDTO completed = inventoryAuditService.complete(audit.getAuditId());

            assertThat(completed.getStatus()).isEqualTo("COMPLETED");
            assertThat(completed.getTotalActualBooks()).isEqualTo(1);
            assertThat(inventoryDetailRepository.findByAuditIdOrderByCreatedAtAsc(audit.getAuditId().toString()))
                    .singleElement()
                    .extracting("barcode")
                    .isEqualTo(copy.getBarcode());
            assertThat(inventoryAuditRepository.findById(audit.getAuditId()))
                    .get()
                    .extracting("status")
                    .isEqualTo("COMPLETED");
        } finally {
            if (audit != null) {
                inventoryDetailRepository.deleteAll(
                        inventoryDetailRepository.findByAuditIdOrderByCreatedAtAsc(audit.getAuditId().toString()));
                inventoryAuditRepository.deleteById(audit.getAuditId());
            }
            bookCopyRepository.deleteById(copy.getBarcode());
            bookRepository.deleteById(book.getBookId());
            userRepository.deleteById(conductor.getUserId());
        }
    }

    @Test
    void postgresRejectsBookCopyWithUnknownShelf() {
        BookEntity book = createBook();
        String barcode = "FK-" + UUID.randomUUID();
        try {
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO book_copies (barcode, book_id, status, shelf_id) VALUES (?, ?, ?, ?)",
                    barcode, book.getBookId(), "AVAILABLE", Long.MAX_VALUE))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.update("DELETE FROM book_copies WHERE barcode = ?", barcode);
            bookRepository.deleteById(book.getBookId());
        }
    }

    @Test
    void redisThrottlesOtpResendLocksAfterFiveFailuresAndSendsSmtpMail() throws Exception {
        UserEntity user = new UserEntity();
        user.setEmail("week6-" + UUID.randomUUID() + "@test.local");
        user.setFullName("Week 6 OTP Test");
        user.setPasswordHash("not-used");
        user = userRepository.saveAndFlush(user);
        String email = user.getEmail().trim().toLowerCase();
        try {
            authService.forgotPassword(email);
            authService.forgotPassword(email);

            assertThat(redisTemplate.opsForValue().get("auth:otp:" + email)).matches("\\d{6}");
            assertThat(redisTemplate.getExpire("auth:otp:resend:" + email, TimeUnit.SECONDS))
                    .isBetween(1L, 10L);
            String mailpitResponse = waitForCapturedMail(email);
            assertThat(mailpitResponse)
                    .contains("Mã xác nhận khôi phục mật khẩu")
                    .contains(email);

            for (int attempt = 1; attempt <= 4; attempt++) {
                assertThatThrownBy(() -> authService.verifyOtp(email, "000000"))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("không hợp lệ");
            }
            assertThatThrownBy(() -> authService.verifyOtp(email, "000000"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("quá nhiều lần");
            assertThat(redisTemplate.hasKey("auth:otp:lock:" + email)).isTrue();
            assertThat(redisTemplate.hasKey("auth:otp:" + email)).isFalse();
        } finally {
            userRepository.deleteById(user.getUserId());
        }
    }

    @Test
    void optedInLibraryNotificationIsStoredAndDeliveredByEmail() throws Exception {
        UserEntity reader = createUser("email-notification");
        String dedupKey = "LOAN_APPROVED:INTEGRATION:" + UUID.randomUUID();
        try {
            notificationPreferenceService.update(reader.getUserId(),
                    new NotificationPreferenceRequestDTO(true, true));

            assertThat(notificationService.createOnce(
                    reader.getUserId(),
                    "LOAN_APPROVED",
                    "Yêu cầu mượn tài liệu của bạn đã được duyệt.",
                    "LOAN_TICKET",
                    "integration-test",
                    dedupKey,
                    LocalDateTime.now()))
                    .isTrue();

            String mailpitResponse = waitForCapturedMail(reader.getEmail());
            assertThat(mailpitResponse)
                    .contains("Tài liệu đã được hệ thống giữ chỗ")
                    .contains(reader.getEmail());
            assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(reader.getUserId()))
                    .extracting("type")
                    .contains("LOAN_APPROVED");
        } finally {
            notificationRepository.deleteAll(
                    notificationRepository.findByUserIdOrderByCreatedAtDesc(reader.getUserId()));
            notificationPreferenceRepository.deleteById(reader.getUserId());
            userRepository.deleteById(reader.getUserId());
        }
    }

    @Test
    void circulationCheckoutAndReturnCommitAllAggregateChanges() {
        UserEntity reader = createUser("reader");
        UserEntity librarian = createUser("librarian");
        BookEntity book = createBook();
        BookCopyEntity copy = null;
        LoanTicketEntity ticket = null;
        String checkoutKey = "checkout-" + UUID.randomUUID();
        String returnKey = "return-" + UUID.randomUUID();
        try {
            copy = createCopy(book.getBookId(), "CIRC-" + UUID.randomUUID(), "RESERVED", reader.getUserId());
            ticket = createLoan(reader.getUserId(), copy.getBarcode(), "RESERVED", "PENDING");

            CheckoutRequestDTO checkout = checkoutRequest(checkoutKey, ticket.getTicketId(), copy.getBarcode());
            assertThat(circulationStateService.checkout(checkout, librarian.getUserId()).getStatus())
                    .isEqualTo("BORROWING");

            LoanDetailEntity checkedOutDetail = loanDetailRepository.findByTicketId(ticket.getTicketId()).get(0);
            assertThat(checkedOutDetail.getCheckoutAt()).isNotNull();
            assertThat(checkedOutDetail.getCheckoutTimeSource()).isEqualTo("LIVE_CHECKOUT");
            assertThat(checkedOutDetail.getCirculationBookId()).isEqualTo(book.getBookId());
            assertThat(loanTicketRepository.findById(ticket.getTicketId()).orElseThrow().getStatus())
                    .isEqualTo("BORROWING");
            assertThat(checkedOutDetail.getReturnStatus()).isEqualTo("BORROWING");
            assertThat(checkedOutDetail.getDueDate()).isBetween(
                    LocalDateTime.now().plusDays(10).minusMinutes(1),
                    LocalDateTime.now().plusDays(10).plusMinutes(1));
            assertThat(bookCopyRepository.findById(copy.getBarcode()).orElseThrow().getStatus())
                    .isEqualTo("BORROWING");

            ReturnRequestDTO returned = returnRequest(returnKey, copy.getBarcode());
            assertThat(circulationStateService.returnBooks(returned, librarian.getUserId()).getStatus())
                    .isEqualTo("RETURNED");

            assertThat(loanTicketRepository.findById(ticket.getTicketId()).orElseThrow().getStatus())
                    .isEqualTo("RETURNED");
            assertThat(loanDetailRepository.findByTicketId(ticket.getTicketId()).get(0).getReturnDate())
                    .isNotNull();
            assertThat(loanDetailRepository.findByTicketId(ticket.getTicketId()).get(0).getPhysicalReturnAt()).isNotNull();
            assertThat(bookCopyRepository.findById(copy.getBarcode()).orElseThrow().getStatus())
                    .isEqualTo("AVAILABLE");
            assertThat(loanStatusHistoryRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getTicketId()))
                    .extracting("toStatus")
                    .containsExactly(
                            com.huit.library.modules.circulation.model.LoanTicketStatus.BORROWING,
                            com.huit.library.modules.circulation.model.LoanTicketStatus.RETURNED);
        } finally {
            cleanupCirculation(ticket, copy, book, List.of(checkoutKey, returnKey));
            userRepository.deleteById(librarian.getUserId());
            userRepository.deleteById(reader.getUserId());
        }
    }

    @Test
    void concurrentCheckoutCannotCreateDoubleLoanForSameTicketAndBarcode() throws Exception {
        UserEntity reader = createUser("reader-race");
        UserEntity librarian = createUser("librarian-race");
        BookEntity book = createBook();
        BookCopyEntity copy = null;
        LoanTicketEntity ticket = null;
        String firstKey = "race-a-" + UUID.randomUUID();
        String secondKey = "race-b-" + UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            copy = createCopy(book.getBookId(), "RACE-" + UUID.randomUUID(), "RESERVED", reader.getUserId());
            ticket = createLoan(reader.getUserId(), copy.getBarcode(), "RESERVED", "PENDING");
            Long ticketId = ticket.getTicketId();
            String barcode = copy.getBarcode();
            CountDownLatch start = new CountDownLatch(1);

            Future<Boolean> first = executor.submit(() -> checkoutAfterSignal(
                    start, checkoutRequest(firstKey, ticketId, barcode), librarian.getUserId()));
            Future<Boolean> second = executor.submit(() -> checkoutAfterSignal(
                    start, checkoutRequest(secondKey, ticketId, barcode), librarian.getUserId()));
            start.countDown();

            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(loanTicketRepository.findById(ticketId).orElseThrow().getStatus()).isEqualTo("BORROWING");
            assertThat(loanStatusHistoryRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)).hasSize(1);
            assertThat(circulationOperationRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            cleanupCirculation(ticket, copy, book, List.of(firstKey, secondKey));
            userRepository.deleteById(librarian.getUserId());
            userRepository.deleteById(reader.getUserId());
        }
    }

    @Test
    void checkoutRollsBackTicketDetailAndCopyWhenOperationAuditFails() {
        UserEntity reader = createUser("reader-rollback");
        UserEntity librarian = createUser("librarian-rollback");
        BookEntity book = createBook();
        BookCopyEntity copy = null;
        LoanTicketEntity ticket = null;
        String key = "force-rollback";
        try {
            copy = createCopy(book.getBookId(), "ROLLBACK-" + UUID.randomUUID(), "RESERVED", reader.getUserId());
            ticket = createLoan(reader.getUserId(), copy.getBarcode(), "RESERVED", "PENDING");
            jdbcTemplate.execute("""
                    CREATE OR REPLACE FUNCTION reject_forced_circulation_operation()
                    RETURNS trigger AS $$
                    BEGIN
                      IF NEW.idempotency_key = 'force-rollback' THEN
                        RAISE EXCEPTION 'forced operation audit failure';
                      END IF;
                      RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);
            jdbcTemplate.execute("""
                    CREATE TRIGGER trg_reject_forced_circulation_operation
                    BEFORE INSERT ON circulation_operations
                    FOR EACH ROW EXECUTE FUNCTION reject_forced_circulation_operation()
                    """);

            Long ticketId = ticket.getTicketId();
            String barcode = copy.getBarcode();
            assertThatThrownBy(() -> circulationStateService.checkout(
                    checkoutRequest(key, ticketId, barcode), librarian.getUserId()))
                    .isInstanceOf(RuntimeException.class);

            assertThat(loanTicketRepository.findById(ticketId).orElseThrow().getStatus()).isEqualTo("RESERVED");
            assertThat(loanDetailRepository.findByTicketId(ticketId).get(0).getReturnStatus()).isEqualTo("PENDING");
            assertThat(bookCopyRepository.findById(barcode).orElseThrow().getStatus()).isEqualTo("RESERVED");
            assertThat(loanStatusHistoryRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)).isEmpty();
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_reject_forced_circulation_operation ON circulation_operations");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_forced_circulation_operation()");
            cleanupCirculation(ticket, copy, book, List.of(key));
            userRepository.deleteById(librarian.getUserId());
            userRepository.deleteById(reader.getUserId());
        }
    }

    @Test
    void circulationMaintenanceMarksOverdueAccruesFineAndNotifiesIdempotently() {
        UserEntity reader = createUser("reader-overdue");
        BookEntity book = createBook();
        BookCopyEntity copy = null;
        LoanTicketEntity ticket = null;
        LocalDateTime processedAt = LocalDateTime.of(2026, 10, 17, 12, 0);
        try {
            copy = createCopy(book.getBookId(), "OVERDUE-" + UUID.randomUUID(), "BORROWING", null);
            ticket = createLoan(reader.getUserId(), copy.getBarcode(), "BORROWING", "BORROWING");
            LoanDetailEntity detail = loanDetailRepository.findByTicketId(ticket.getTicketId()).get(0);
            detail.setDueDate(processedAt.minusHours(25));
            detail.setLateFineAccrued(BigDecimal.ZERO);
            loanDetailRepository.saveAndFlush(detail);

            CirculationMaintenanceResult first = circulationMaintenanceService.processAt(processedAt);
            CirculationMaintenanceResult replay = circulationMaintenanceService.processAt(processedAt);

            assertThat(first.overdueTickets()).isEqualTo(1);
            assertThat(replay.overdueTickets()).isEqualTo(1);
            assertThat(loanTicketRepository.findById(ticket.getTicketId()).orElseThrow().getStatus())
                    .isEqualTo("OVERDUE");
            assertThat(loanDetailRepository.findById(detail.getDetailId()).orElseThrow().getLateFineAccrued())
                    .isEqualByComparingTo("1000");
            assertThat(bookCopyRepository.findById(copy.getBarcode()).orElseThrow().getStatus())
                    .isEqualTo("OVERDUE");
            assertThat(walletRepository.findByUserId(reader.getUserId()).orElseThrow().getTotalDebt())
                    .isEqualByComparingTo("1000");
            assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(reader.getUserId()))
                    .singleElement()
                    .extracting("type")
                    .isEqualTo("LOAN_OVERDUE");
            assertThat(transactionRepository.findAll().stream()
                    .filter(entry -> reader.getUserId().equals(entry.getUserId())))
                    .singleElement()
                    .extracting("amount")
                    .isEqualTo(new BigDecimal("1000.00"));
        } finally {
            notificationRepository.deleteAll(
                    notificationRepository.findByUserIdOrderByCreatedAtDesc(reader.getUserId()));
            transactionRepository.deleteAll(transactionRepository.findAll().stream()
                    .filter(entry -> reader.getUserId().equals(entry.getUserId()))
                    .toList());
            walletRepository.findByUserId(reader.getUserId()).ifPresent(walletRepository::delete);
            cleanupCirculation(ticket, copy, book, List.of());
            userRepository.deleteById(reader.getUserId());
        }
    }

    @Test
    void circulationMaintenanceExpiresReservationAndReleasesCopy() {
        UserEntity reader = createUser("reader-expired-reservation");
        BookEntity book = createBook();
        BookCopyEntity copy = null;
        LoanTicketEntity ticket = null;
        LocalDateTime processedAt = LocalDateTime.of(2026, 10, 17, 12, 0);
        try {
            copy = createCopy(book.getBookId(), "EXPIRED-" + UUID.randomUUID(), "RESERVED", reader.getUserId());
            copy.setLockedUntil(processedAt.minusMinutes(1));
            copy = bookCopyRepository.saveAndFlush(copy);
            ticket = createLoan(reader.getUserId(), copy.getBarcode(), "RESERVED", "PENDING");

            CirculationMaintenanceResult result = circulationMaintenanceService.processAt(processedAt);

            assertThat(result.expiredReservations()).isEqualTo(1);
            assertThat(loanTicketRepository.findById(ticket.getTicketId()).orElseThrow().getStatus())
                    .isEqualTo("CANCELLED");
            assertThat(loanDetailRepository.findByTicketId(ticket.getTicketId()).get(0).getReturnStatus())
                    .isEqualTo("CANCELLED");
            BookCopyEntity releasedCopy = bookCopyRepository.findById(copy.getBarcode()).orElseThrow();
            assertThat(releasedCopy.getStatus()).isEqualTo("AVAILABLE");
            assertThat(releasedCopy.getLockedByUser()).isNull();
            assertThat(releasedCopy.getLockedUntil()).isNull();
            assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(reader.getUserId()))
                    .extracting("type")
                    .containsExactlyInAnyOrder("RESERVATION_EXPIRED", "RESERVATION_NO_SHOW");
        } finally {
            notificationRepository.deleteAll(
                    notificationRepository.findByUserIdOrderByCreatedAtDesc(reader.getUserId()));
            cleanupCirculation(ticket, copy, book, List.of());
            userRepository.deleteById(reader.getUserId());
        }
    }

    @Test
    void week11HttpE2eCoversLoginSearchReservationQrCheckoutOverdueFineAndReturn() throws Exception {
        String password = "Week11@123";
        UserEntity reader = createUserWithRole("week11-reader", "STUDENT", password);
        UserEntity librarian = createUserWithRole("week11-librarian", "LIBRARIAN", password);
        BookEntity book = createBook();
        BookCopyEntity copy = null;
        LoanTicketEntity ticket = null;
        String checkoutKey = "week11-checkout-" + UUID.randomUUID();
        String returnKey = "week11-return-" + UUID.randomUUID();
        try {
            copy = createCopy(book.getBookId(), "W11-" + UUID.randomUUID(), "AVAILABLE", null);
            String readerToken = loginAndGetToken(reader.getEmail(), password);
            String librarianToken = loginAndGetToken(librarian.getEmail(), password);

            mockMvc.perform(get("/books")
                            .param("query", book.getTitle())
                            .param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].bookId").value(book.getBookId()));

            String reservationJson = mockMvc.perform(post("/loans/reserve")
                            .header("Authorization", "Bearer " + readerToken)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("""
                                    {"bookId":%d,"ticketType":"BORROW"}
                                    """.formatted(book.getBookId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RESERVED"))
                    .andReturn().getResponse().getContentAsString();
            Long ticketId = objectMapper.readTree(reservationJson).get("ticketId").asLong();
            ticket = loanTicketRepository.findById(ticketId).orElseThrow();

            assertThat(ticket.getProcessedBy()).isNull();
            assertThat(loanStatusHistoryRepository.findByTicketIdOrderByCreatedAtAsc(ticketId))
                    .singleElement().satisfies(history -> {
                        assertThat(history.getChangedBy()).isNull();
                        assertThat(history.getToStatus().name()).isEqualTo("RESERVED");
                    });

            String qrJson = mockMvc.perform(get("/api/v1/qr/tickets/{ticketId}", ticketId)
                            .header("Authorization", "Bearer " + readerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.payload").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();
            String qrPayload = objectMapper.readTree(qrJson).get("payload").asText();
            mockMvc.perform(post("/api/v1/qr/resolve")
                            .header("Authorization", "Bearer " + librarianToken)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of("payload", qrPayload))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ticketId").value(ticketId))
                    .andExpect(jsonPath("$.allowedActions[0]").value("CHECKOUT"));

            mockMvc.perform(post("/loans/checkout")
                            .header("Authorization", "Bearer " + librarianToken)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of(
                                    "idempotencyKey", checkoutKey,
                                    "ticketId", ticketId,
                                    "barcodes", List.of(copy.getBarcode())))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("BORROWING"));

            LoanDetailEntity detail = loanDetailRepository.findByTicketId(ticketId).get(0);
            LocalDateTime processedAt = LocalDateTime.now().withNano(0);
            detail.setDueDate(processedAt.minusDays(2));
            detail.setLateFineAccrued(BigDecimal.ZERO);
            loanDetailRepository.saveAndFlush(detail);
            circulationMaintenanceService.processAt(processedAt);

            assertThat(walletRepository.findByUserId(reader.getUserId()).orElseThrow().getTotalDebt())
                    .isEqualByComparingTo("2000");
            assertThat(transactionRepository.findByUserIdOrderByCreatedAtAsc(reader.getUserId()))
                    .anyMatch(transaction -> transaction.getAmount().compareTo(new BigDecimal("2000")) == 0);

            mockMvc.perform(post("/loans/return")
                            .header("Authorization", "Bearer " + librarianToken)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of(
                                    "idempotencyKey", returnKey,
                                    "barcodes", List.of(copy.getBarcode()),
                                    "returnConditionNote", "Tốt"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RETURNED"));

            assertThat(bookCopyRepository.findById(copy.getBarcode()).orElseThrow().getStatus())
                    .isEqualTo("AVAILABLE");
        } finally {
            notificationRepository.deleteAll(
                    notificationRepository.findByUserIdOrderByCreatedAtDesc(reader.getUserId()));
            transactionRepository.deleteAll(
                    transactionRepository.findByUserIdOrderByCreatedAtAsc(reader.getUserId()));
            walletRepository.findByUserId(reader.getUserId()).ifPresent(walletRepository::delete);
            cleanupCirculation(ticket, copy, book, List.of(checkoutKey, returnKey));
            userRepository.deleteById(librarian.getUserId());
            userRepository.deleteById(reader.getUserId());
        }
    }

    @Test
    void week11CorsAllowsConfiguredPortalAndRejectsUnknownOrigins() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));

        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void concurrentReservationsOfLastCopyCreateExactlyOneAutomaticallyApprovedTicket() throws Exception {
        UserEntity firstReader = createUser("reservation-race-a");
        UserEntity secondReader = createUser("reservation-race-b");
        BookEntity book = createBook();
        BookCopyEntity copy = createCopy(book.getBookId(), "RESERVE-" + UUID.randomUUID(), "AVAILABLE", null);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            java.util.concurrent.Callable<String> firstTask = () -> {
                start.await();
                return loanService.reserveOrBorrowBook(reservationRequest(book.getBookId()), firstReader.getUserId()).getStatus();
            };
            java.util.concurrent.Callable<String> secondTask = () -> {
                start.await();
                return loanService.reserveOrBorrowBook(reservationRequest(book.getBookId()), secondReader.getUserId()).getStatus();
            };
            Future<String> first = executor.submit(firstTask);
            Future<String> second = executor.submit(secondTask);
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("RESERVED", "WAITLIST");
            assertThat(bookCopyRepository.findById(copy.getBarcode()).orElseThrow().getStatus()).isEqualTo("RESERVED");
            var tickets = java.util.stream.Stream.concat(
                    loanTicketRepository.findByUserIdOrderByCreatedAtDesc(firstReader.getUserId()).stream(),
                    loanTicketRepository.findByUserIdOrderByCreatedAtDesc(secondReader.getUserId()).stream()).toList();
            assertThat(tickets).singleElement().satisfies(ticket -> {
                assertThat(ticket.getStatus()).isEqualTo("RESERVED");
                assertThat(loanStatusHistoryRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getTicketId())).hasSize(1);
            });
        } finally {
            executor.shutdownNow();
            cleanupReservationReader(firstReader);
            cleanupReservationReader(secondReader);
            cleanupCirculation(null, copy, book, List.of());
        }
    }

    @Test
    void batchReservationCommitsReservedCopiesAndAuditAndRollsBackWhenOneBookIsUnavailable() {
        UserEntity reader = createUser("reservation-batch");
        BookEntity first = createBook();
        BookEntity second = createBook();
        BookCopyEntity firstCopy = createCopy(first.getBookId(), "BATCH-A-" + UUID.randomUUID(), "AVAILABLE", null);
        BookCopyEntity secondCopy = createCopy(second.getBookId(), "BATCH-B-" + UUID.randomUUID(), "MAINTENANCE", null);
        try {
            var request = new com.huit.library.modules.circulation.dto.BatchBorrowRequestDTO();
            request.setBookIds(List.of(second.getBookId(), first.getBookId()));
            request.setTicketType("BORROW");
            assertThatThrownBy(() -> loanService.reserveBooks(request, reader.getUserId()))
                    .hasMessageContaining("hết bản sẵn sàng");
            assertThat(loanTicketRepository.findByUserIdOrderByCreatedAtDesc(reader.getUserId())).isEmpty();
            assertThat(bookCopyRepository.findById(firstCopy.getBarcode()).orElseThrow().getStatus()).isEqualTo("AVAILABLE");
            secondCopy.setStatus("AVAILABLE");
            bookCopyRepository.saveAndFlush(secondCopy);
            var result = loanService.reserveBooks(request, reader.getUserId());
            assertThat(result.getStatus()).isEqualTo("RESERVED");
            assertThat(loanDetailRepository.findByTicketId(result.getTicketId())).hasSize(2)
                    .allSatisfy(detail -> assertThat(detail.getReturnStatus()).isEqualTo("PENDING"));
            assertThat(bookCopyRepository.findById(firstCopy.getBarcode()).orElseThrow().getStatus()).isEqualTo("RESERVED");
            assertThat(bookCopyRepository.findById(secondCopy.getBarcode()).orElseThrow().getStatus()).isEqualTo("RESERVED");
            assertThat(loanStatusHistoryRepository.findByTicketIdOrderByCreatedAtAsc(result.getTicketId()))
                    .singleElement().satisfies(history -> assertThat(history.getChangedBy()).isNull());
        } finally {
            cleanupReservationReader(reader);
            cleanupCirculation(null, firstCopy, first, List.of());
            cleanupCirculation(null, secondCopy, second, List.of());
        }
    }

    private com.huit.library.modules.circulation.dto.BorrowRequestDTO reservationRequest(Long bookId) {
        var request = new com.huit.library.modules.circulation.dto.BorrowRequestDTO();
        request.setBookId(bookId);
        request.setTicketType("BORROW");
        return request;
    }

    private void cleanupReservationReader(UserEntity reader) {
        for (var ticket : loanTicketRepository.findByUserIdOrderByCreatedAtDesc(reader.getUserId())) {
            cleanupCirculation(ticket, null, null, List.of());
        }
        jdbcTemplate.update("DELETE FROM waitlists WHERE user_id = ?", reader.getUserId());
        notificationRepository.deleteAll(notificationRepository.findByUserIdOrderByCreatedAtDesc(reader.getUserId()));
        userRepository.deleteById(reader.getUserId());
    }

    @Test
    void inspectionMixedReturnKeepsIncidentsOutOfStockAndReplaysOriginalFinancialSnapshot() {
        InspectionFixture fixture = inspectionFixture(3, "50000");
        UserEntity waiter = createUser("inspection-waiter");
        String key = "inspection-mixed-" + UUID.randomUUID();
        try {
            for (BookEntity book : fixture.books()) inspectionWaiter(book, waiter);
            var request = inspectionRequest(key, fixture, "NORMAL", "DAMAGED", "LOST");
            var receipt = returnInspectionService.inspect(request, UUID.randomUUID());
            assertThat(receipt.items()).hasSize(3);
            assertThat(receipt.items().get(0).detailStatus()).isEqualTo("RETURNED");
            assertThat(receipt.items().get(0).copyStatus()).isEqualTo("RESERVED"); // FIFO recipient, not incident stock
            assertThat(receipt.items().get(1).detailStatus()).isEqualTo("DAMAGED");
            assertThat(receipt.items().get(1).copyStatus()).isEqualTo("MAINTENANCE");
            assertThat(receipt.items().get(1).penaltyAmount()).isEqualByComparingTo("200000");
            assertThat(receipt.items().get(1).depositApplied()).isEqualByComparingTo("50000");
            assertThat(receipt.items().get(1).debtAdded()).isEqualByComparingTo("150000");
            assertThat(receipt.items().get(2).copyStatus()).isEqualTo("LOST");
            assertThat(receipt.items().get(2).penaltyAmount()).isEqualByComparingTo("50000");
            assertThat(receipt.items().get(2).returnDate()).isNull();
            assertThat(receipt.items().get(2).fineStoppedAt()).isNotNull();
            assertThat(loanDetailRepository.findById(receipt.items().get(0).loanDetailId()).orElseThrow().getPhysicalReturnAt()).isNotNull();
            assertThat(loanDetailRepository.findById(receipt.items().get(1).loanDetailId()).orElseThrow().getPhysicalReturnTimeSource()).isEqualTo("LIVE_DAMAGED_RECEIPT");
            assertThat(loanDetailRepository.findById(receipt.items().get(2).loanDetailId()).orElseThrow().getPhysicalReturnAt()).isNull();
            assertThat(receipt.tickets()).singleElement().satisfies(ticket -> assertThat(ticket.status()).isEqualTo("BORROWING"));
            assertThat(receipt.balances()).singleElement().satisfies(balance -> {
                assertThat(balance.depositBalance()).isEqualByComparingTo("0");
                assertThat(balance.totalDebt()).isEqualByComparingTo("200000");
            });
            assertThat(inspectionWaitlists.findByBookIdAndStatus(fixture.books().get(0).getBookId(), "NOTIFIED")).hasSize(1);
            for (int i = 1; i < 3; i++) {
                assertThat(inspectionWaitlists.findByBookIdAndStatus(fixture.books().get(i).getBookId(), "WAITING")).hasSize(1);
            }
            // A subsequent real deposit event must not rewrite the receipt from the earlier operation.
            debtLedgerService.recordDepositTopUp(fixture.reader().getUserId(), new BigDecimal("1000"), "inspection-topup-" + key);
            var reorderedItems = new java.util.ArrayList<>(request.getItems());
            java.util.Collections.reverse(reorderedItems);
            request.setItems(reorderedItems);
            request.getItems().stream().filter(item -> item.getPenalty() != null && item.getPenalty().getRepairFee() != null)
                    .forEach(item -> item.getPenalty().setRepairFee(item.getPenalty().getRepairFee().setScale(2)));
            var replay = returnInspectionService.inspect(request, UUID.randomUUID());
            assertThat(replay.replayed()).isTrue();
            assertThat(replay.operationId()).isEqualTo(receipt.operationId());
            assertThat(replay.items()).isEqualTo(receipt.items());
            assertThat(replay.balances()).isEqualTo(receipt.balances());
            assertThat(penaltyCaseRepository.findByUserIdOrderByCreatedAtDesc(fixture.reader().getUserId())).hasSize(2);
            assertThat(walletRepository.findByUserId(fixture.reader().getUserId()).orElseThrow().getDepositBalance())
                    .isEqualByComparingTo("1000");
            assertThatThrownBy(() -> returnInspectionService.inspect(
                    inspectionRequest(key, fixture, "NORMAL", "DAMAGED", "NORMAL"), UUID.randomUUID()))
                    .hasMessageContaining("yêu cầu khác");
            assertThatThrownBy(() -> returnInspectionService.inspect(
                    inspectionRequest(key + "-duplicate", fixture, "NORMAL", "DAMAGED", "LOST"), UUID.randomUUID()))
                    .hasMessageContaining("không còn");
        } finally {
            cleanupInspection(fixture, key, key + "-duplicate");
            cleanupInspectionWaiter(waiter);
        }
    }

    @Test
    void inspectionFailureOnLastItemRollsBackNormalReturnPenaltyDepositLateFineAndFifoPromotion() {
        InspectionFixture fixture = inspectionFixture(3, "50000");
        UserEntity waiter = createUser("inspection-rollback-waiter");
        String key = "inspection-rollback-" + UUID.randomUUID();
        try {
            inspectionWaiter(fixture.books().get(0), waiter);
            for (LoanDetailEntity detail : fixture.details()) {
                detail.setDueDate(LocalDateTime.now().minusDays(2));
                loanDetailRepository.saveAndFlush(detail);
            }
            var request = inspectionRequest(key, fixture, "NORMAL", "DAMAGED", "LOST");
            var lastPenalty = request.getItems().get(2).getPenalty();
            lastPenalty.setStillInMarket(false);
            lastPenalty.setSettlementMethod("CASH");
            lastPenalty.setMaterialType("NO_RATE_B02");
            assertThatThrownBy(() -> returnInspectionService.inspect(request, UUID.randomUUID()))
                    .hasMessageContaining("Chưa có biểu phí");
            assertThat(loanDetailRepository.findByTicketId(fixture.ticket().getTicketId()))
                    .allSatisfy(detail -> {
                        assertThat(detail.getReturnStatus()).isEqualTo("BORROWING");
                        assertThat(detail.getReturnDate()).isNull();
                        assertThat(detail.getFineStoppedAt()).isNull();
                        assertThat(detail.getLateFineAccrued()).isEqualByComparingTo("0");
                    });
            for (BookCopyEntity copy : fixture.copies()) {
                assertThat(bookCopyRepository.findById(copy.getBarcode()).orElseThrow().getStatus()).isEqualTo("BORROWING");
            }
            var wallet = walletRepository.findByUserId(fixture.reader().getUserId()).orElseThrow();
            assertThat(wallet.getDepositBalance()).isEqualByComparingTo("50000");
            assertThat(wallet.getTotalDebt()).isEqualByComparingTo("0");
            assertThat(penaltyCaseRepository.findByUserIdOrderByCreatedAtDesc(fixture.reader().getUserId())).isEmpty();
            assertThat(transactionRepository.findByUserIdOrderByCreatedAtAsc(fixture.reader().getUserId())).isEmpty();
            assertThat(circulationOperationRepository.findByIdempotencyKey(key)).isEmpty();
            assertThat(inspectionWaitlists.findByBookIdAndStatus(fixture.books().get(0).getBookId(), "WAITING")).hasSize(1);
            assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(waiter.getUserId())).isEmpty();
        } finally {
            cleanupInspection(fixture, key);
            cleanupInspectionWaiter(waiter);
        }
    }

    @Test
    void concurrentInspectionRetryCreatesOnePenaltyAndAppliesDepositOnce() throws Exception {
        InspectionFixture fixture = inspectionFixture(1, "50000");
        String key = "inspection-race-" + UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var request = inspectionRequest(key, fixture, "DAMAGED");
            CountDownLatch start = new CountDownLatch(1);
            java.util.concurrent.Callable<ReturnInspectionResponseDTO> task = () -> {
                start.await();
                return returnInspectionService.inspect(request, UUID.randomUUID());
            };
            var firstTask = executor.submit(task);
            var secondTask = executor.submit(task);
            start.countDown();
            var first = firstTask.get(30, TimeUnit.SECONDS);
            var second = secondTask.get(30, TimeUnit.SECONDS);
            assertThat(first.operationId()).isEqualTo(second.operationId());
            assertThat(List.of(first.replayed(), second.replayed())).containsExactlyInAnyOrder(false, true);
            assertThat(penaltyCaseRepository.findByUserIdOrderByCreatedAtDesc(fixture.reader().getUserId())).hasSize(1);
            var wallet = walletRepository.findByUserId(fixture.reader().getUserId()).orElseThrow();
            assertThat(wallet.getDepositBalance()).isEqualByComparingTo("0");
            assertThat(wallet.getTotalDebt()).isEqualByComparingTo("150000");
            assertThat(transactionRepository.findByUserIdOrderByCreatedAtAsc(fixture.reader().getUserId())).hasSize(1);
        } finally {
            executor.shutdownNow();
            cleanupInspection(fixture, key);
        }
    }

    @Test
    void inspectionHttpEnforcesStaffRoleValidatesSeverityAndAcceptsLostItemWithoutBarcode() throws Exception {
        InspectionFixture fixture = inspectionFixture(1, "0");
        UserEntity student = createUserWithRole("inspection-http-student", "STUDENT", "Inspection@123");
        UserEntity librarian = createUserWithRole("inspection-http-staff", "LIBRARIAN", "Inspection@123");
        String key = "inspection-http-" + UUID.randomUUID();
        try {
            String studentToken = loginAndGetToken(student.getEmail(), "Inspection@123");
            String staffToken = loginAndGetToken(librarian.getEmail(), "Inspection@123");
            var request = inspectionRequest(key, fixture, "LOST");
            mockMvc.perform(post("/loans/return-inspection")
                            .header("Authorization", "Bearer " + studentToken)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
            var invalid = inspectionRequest(key + "-invalid", fixture, "DAMAGED");
            invalid.getItems().get(0).getPenalty().setDamageSeverity(null);
            mockMvc.perform(post("/loans/return-inspection")
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
            assertThat(penaltyCaseRepository.findByUserIdOrderByCreatedAtDesc(fixture.reader().getUserId())).isEmpty();
            mockMvc.perform(post("/loans/return-inspection")
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].penaltyCaseId").isNumber())
                    .andExpect(jsonPath("$.items[0].detailStatus").value("LOST"))
                    .andExpect(jsonPath("$.items[0].returnDate").doesNotExist());
        } finally {
            cleanupInspection(fixture, key, key + "-invalid");
            userRepository.deleteById(student.getUserId());
            userRepository.deleteById(librarian.getUserId());
        }
    }

    @Test
    void inspectionKeepsReplacementPendingUntilAcceptanceAndLegacyReturnCanFinishOtherItems() {
        InspectionFixture fixture = inspectionFixture(2, "50000");
        String key = "inspection-replacement-" + UUID.randomUUID();
        String replacementBarcode = "REPLACEMENT-" + UUID.randomUUID();
        try {
            var receipt = returnInspectionService.inspect(inspectionRequest(key, fixture, "LOST"), UUID.randomUUID());
            var incident = receipt.items().get(0);
            assertThat(incident.penaltyStatus()).isEqualTo("WAITING_REPLACEMENT");
            assertThat(incident.penaltyOutstanding()).isEqualByComparingTo("0");
            assertThat(incident.returnDate()).isNull();
            assertThat(loanTicketRepository.findById(fixture.ticket().getTicketId()).orElseThrow().getStatus()).isEqualTo("BORROWING");
            var acceptance = new ReplacementAcceptanceRequestDTO();
            acceptance.setReplacementBarcode(replacementBarcode);
            acceptance.setVerificationNote("Verified replacement edition and condition");
            penaltyCaseService.acceptReplacement(incident.penaltyCaseId(), acceptance, UUID.randomUUID());
            assertThat(loanDetailRepository.findById(incident.loanDetailId()).orElseThrow().getReturnStatus()).isEqualTo("SETTLED");
            assertThat(bookCopyRepository.findById(fixture.copies().get(0).getBarcode()).orElseThrow().getStatus()).isEqualTo("LOST");
            circulationStateService.returnBooks(returnRequest(key + "-normal", fixture.copies().get(1).getBarcode()), UUID.randomUUID());
            assertThat(loanTicketRepository.findById(fixture.ticket().getTicketId()).orElseThrow().getStatus()).isEqualTo("RETURNED");
            var replay = returnInspectionService.inspect(inspectionRequest(key, fixture, "LOST"), UUID.randomUUID());
            assertThat(replay.items().get(0).penaltyStatus()).isEqualTo("WAITING_REPLACEMENT"); // original snapshot
        } finally {
            bookCopyRepository.findById(replacementBarcode).ifPresent(bookCopyRepository::delete);
            cleanupInspection(fixture, key, key + "-normal");
        }
    }

    private record InspectionFixture(UserEntity reader, LoanTicketEntity ticket, List<BookEntity> books,
            List<BookCopyEntity> copies, List<LoanDetailEntity> details) { }

    private InspectionFixture inspectionFixture(int count, String deposit) {
        UserEntity reader = createUser("inspection-reader");
        List<BookEntity> books = new java.util.ArrayList<>();
        List<BookCopyEntity> copies = new java.util.ArrayList<>();
        List<LoanDetailEntity> details = new java.util.ArrayList<>();
        LoanTicketEntity ticket = null;
        for (int i = 0; i < count; i++) {
            BookEntity book = createBook();
            BookCopyEntity copy = createCopy(book.getBookId(), "INSPECT-" + UUID.randomUUID(), "BORROWING", null);
            books.add(book);
            copies.add(copy);
            LoanDetailEntity detail;
            if (ticket == null) {
                ticket = createLoan(reader.getUserId(), copy.getBarcode(), "BORROWING", "BORROWING");
                detail = loanDetailRepository.findByTicketId(ticket.getTicketId()).get(0);
            } else {
                detail = new LoanDetailEntity();
                detail.setTicketId(ticket.getTicketId());
                detail.setBarcode(copy.getBarcode());
                detail.setReturnStatus("BORROWING");
            }
            detail.setDueDate(LocalDateTime.now().plusDays(1));
            details.add(loanDetailRepository.saveAndFlush(detail));
        }
        WalletEntity wallet = new WalletEntity();
        wallet.setUserId(reader.getUserId());
        wallet.setDepositBalance(new BigDecimal(deposit));
        wallet.setTotalDebt(BigDecimal.ZERO);
        walletRepository.saveAndFlush(wallet);
        return new InspectionFixture(reader, ticket, books, copies, details);
    }

    private ReturnInspectionRequestDTO inspectionRequest(String key, InspectionFixture fixture, String... conditions) {
        var request = new ReturnInspectionRequestDTO();
        request.setIdempotencyKey(key);
        List<ReturnInspectionRequestDTO.Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < conditions.length; i++) {
            var item = new ReturnInspectionRequestDTO.Item();
            item.setLoanDetailId(fixture.details().get(i).getDetailId());
            item.setCondition(ReturnInspectionRequestDTO.Condition.valueOf(conditions[i]));
            if (!"NORMAL".equals(conditions[i])) {
                PenaltyCaseRequestDTO penalty = new PenaltyCaseRequestDTO();
                penalty.setLoanDetailId(item.getLoanDetailId());
                penalty.setIncidentType(conditions[i]);
                penalty.setDescription("Inspection integration incident");
                penalty.setMaterialType("BOOK");
                penalty.setLanguageCategory("VIETNAMESE");
                penalty.setStillInMarket(true);
                penalty.setSettlementMethod("LOST".equals(conditions[i]) ? "REPLACEMENT" : "REPAIR");
                penalty.setDamageSeverity("DAMAGED".equals(conditions[i]) ? "LIGHT" : null);
                penalty.setRepairFee("DAMAGED".equals(conditions[i]) ? new BigDecimal("200000") : null);
                item.setPenalty(penalty);
            }
            items.add(item);
        }
        request.setItems(items);
        return request;
    }

    private void inspectionWaiter(BookEntity book, UserEntity reader) {
        WaitlistEntity entry = new WaitlistEntity();
        entry.setBookId(book.getBookId());
        entry.setUserId(reader.getUserId());
        entry.setStatus("WAITING");
        inspectionWaitlists.saveAndFlush(entry);
    }

    private void cleanupInspection(InspectionFixture fixture, String... keys) {
        UUID userId = fixture.reader().getUserId();
        transactionRepository.deleteAll(transactionRepository.findByUserIdOrderByCreatedAtAsc(userId));
        penaltyCaseRepository.deleteAll(penaltyCaseRepository.findByUserIdOrderByCreatedAtDesc(userId));
        notificationRepository.deleteAll(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId));
        for (BookEntity book : fixture.books()) {
            inspectionWaitlists.deleteAll(inspectionWaitlists.findByBookIdAndStatus(book.getBookId(), "WAITING"));
            inspectionWaitlists.deleteAll(inspectionWaitlists.findByBookIdAndStatus(book.getBookId(), "NOTIFIED"));
        }
        cleanupCirculation(fixture.ticket(), null, null, List.of(keys));
        for (BookCopyEntity copy : fixture.copies()) bookCopyRepository.deleteById(copy.getBarcode());
        for (BookEntity book : fixture.books()) bookRepository.deleteById(book.getBookId());
        walletRepository.findByUserId(userId).ifPresent(walletRepository::delete);
        userRepository.deleteById(userId);
    }

    private void cleanupInspectionWaiter(UserEntity reader) {
        notificationRepository.deleteAll(notificationRepository.findByUserIdOrderByCreatedAtDesc(reader.getUserId()));
        userRepository.deleteById(reader.getUserId());
    }

    private SupplierEntity createSupplier() {
        SupplierEntity supplier = new SupplierEntity();
        supplier.setName("Week 6 Test Supplier " + UUID.randomUUID());
        supplier.setContactInfo("integration@test.local");
        return supplierRepository.saveAndFlush(supplier);
    }

    private UserEntity createUser(String prefix) {
        UserEntity user = new UserEntity();
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@test.local");
        user.setFullName(prefix);
        user.setPasswordHash("not-used");
        user.setCardStatus("ACTIVE");
        return userRepository.saveAndFlush(user);
    }

    private UserEntity createUserWithRole(String prefix, String role, String password) {
        UserEntity user = createUser(prefix);
        user.setRoleId(role);
        user.setMssv(prefix + "-" + UUID.randomUUID());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setIsFirstLogin(false);
        return userRepository.saveAndFlush(user);
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    @Test
    void librarianReportsEnforceHttpRolesAndExportTheSameFilteredDataset() throws Exception {
        String base = "/api/v1/librarian-reports/student-obligations";
        String marker = "b04-http-" + UUID.randomUUID();
        UserEntity student = createUserWithRole(marker, "STUDENT", "Report@123");
        student.setUserType("STUDENT");
        student.setDepartment("Chưa ánh xạ B04");
        userRepository.saveAndFlush(student);
        WalletEntity wallet = new WalletEntity();
        wallet.setUserId(student.getUserId()); wallet.setTotalDebt(new BigDecimal("150000")); wallet.setDepositBalance(BigDecimal.ZERO);
        walletRepository.saveAndFlush(wallet);
        com.huit.library.modules.finance.entity.TransactionEntity charge = new com.huit.library.modules.finance.entity.TransactionEntity();
        charge.setUserId(student.getUserId()); charge.setType("REPAIR_FEE"); charge.setAmount(new BigDecimal("200000"));
        charge.setPaidAmount(new BigDecimal("50000")); charge.setStatus("PARTIALLY_PAID"); charge.setPaymentMethod("SECURITY_DEPOSIT");
        transactionRepository.saveAndFlush(charge);
        String studentToken = loginAndGetToken(student.getEmail(), "Report@123");
        for (String path : List.of(base, base + "/export", base + "/faculties", base + "/classification-coverage")) {
            mockMvc.perform(get(path)).andExpect(status().isForbidden());
            mockMvc.perform(get(path).header("Authorization", "Bearer " + studentToken)).andExpect(status().isForbidden());
        }
        for (String role : List.of("LIBRARIAN", "ADMIN")) {
            UserEntity staff = createUserWithRole(marker + role, role, "Report@123");
            String token = loginAndGetToken(staff.getEmail(), "Report@123");
            mockMvc.perform(get(base).header("Authorization", "Bearer " + token)
                            .param("mode", "DEBT").param("facultyId", "UNCLASSIFIED").param("q", marker).param("size", "1"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.totalStudents").value(1))
                    .andExpect(jsonPath("$.students[0].outstandingAmount").value(150000))
                    .andExpect(jsonPath("$.students[0].paidOrDepositAmount").value(50000))
                    .andExpect(jsonPath("$.students[0].facultyId").value("UNCLASSIFIED"));
            byte[] xlsx = mockMvc.perform(get(base + "/export").header("Authorization", "Bearer " + token)
                            .param("mode", "DEBT").param("facultyId", "UNCLASSIFIED").param("q", marker)
                            .param("page", "99").param("size", "1")) // export never adopts preview pagination
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment; filename=\"thu-thu-debt-")))
                    .andReturn().getResponse().getContentAsByteArray();
            try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
                assertThat(book.getSheet("Tong hop").getLastRowNum()).isEqualTo(5);
                assertThat(book.getSheet("Tong hop").getRow(5).getCell(14).getNumericCellValue()).isEqualTo(150000);
            }
            mockMvc.perform(get(base + "/faculties").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
            mockMvc.perform(get(base + "/classification-coverage").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalStudents").isNumber())
                    .andExpect(jsonPath("$.complete").isBoolean());
            mockMvc.perform(get(base).header("Authorization", "Bearer " + token).param("mode", "INVALID")).andExpect(status().isBadRequest());
            mockMvc.perform(get(base).header("Authorization", "Bearer " + token).param("size", "0")).andExpect(status().isBadRequest());
            mockMvc.perform(get(base + "/export").header("Authorization", "Bearer " + token).param("facultyId", "UNKNOWN"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get(base + "/export").header("Authorization", "Bearer " + token).header("Origin", "http://localhost:4200").param("q", marker))
                    .andExpect(status().isOk()).andExpect(header().string("Access-Control-Expose-Headers", org.hamcrest.Matchers.containsString("Content-Disposition")));
        }
    }

    private BookCopyEntity createCopy(Long bookId, String barcode, String status, UUID lockedBy) {
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode(barcode);
        copy.setBookId(bookId);
        copy.setStatus(status);
        copy.setLockedByUser(lockedBy);
        copy.setLockedUntil(LocalDateTime.now().plusDays(2));
        return bookCopyRepository.saveAndFlush(copy);
    }

    private LoanTicketEntity createLoan(UUID readerId, String barcode, String ticketStatus, String detailStatus) {
        LoanTicketEntity ticket = new LoanTicketEntity();
        ticket.setUserId(readerId);
        ticket.setTicketType("BORROW");
        ticket.setStatus(ticketStatus);
        ticket = loanTicketRepository.saveAndFlush(ticket);

        LoanDetailEntity detail = new LoanDetailEntity();
        detail.setTicketId(ticket.getTicketId());
        detail.setBarcode(barcode);
        detail.setReturnStatus(detailStatus);
        loanDetailRepository.saveAndFlush(detail);
        return ticket;
    }

    private CheckoutRequestDTO checkoutRequest(String key, Long ticketId, String barcode) {
        CheckoutRequestDTO request = new CheckoutRequestDTO();
        request.setIdempotencyKey(key);
        request.setTicketId(ticketId);
        request.setBarcodes(List.of(barcode));
        return request;
    }

    private ReturnRequestDTO returnRequest(String key, String barcode) {
        ReturnRequestDTO request = new ReturnRequestDTO();
        request.setIdempotencyKey(key);
        request.setBarcodes(List.of(barcode));
        request.setReturnConditionNote("Tốt");
        return request;
    }

    private boolean checkoutAfterSignal(
            CountDownLatch start,
            CheckoutRequestDTO request,
            UUID librarianId) throws InterruptedException {
        start.await();
        try {
            circulationStateService.checkout(request, librarianId);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void cleanupCirculation(
            LoanTicketEntity ticket,
            BookCopyEntity copy,
            BookEntity book,
            List<String> operationKeys) {
        for (String key : operationKeys) {
            circulationOperationRepository.findByIdempotencyKey(key)
                    .ifPresent(circulationOperationRepository::delete);
        }
        if (ticket != null) {
            loanStatusHistoryRepository.deleteAll(
                    loanStatusHistoryRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getTicketId()));
            loanDetailRepository.deleteAll(loanDetailRepository.findByTicketId(ticket.getTicketId()));
            loanTicketRepository.deleteById(ticket.getTicketId());
        }
        if (copy != null) {
            bookCopyRepository.deleteById(copy.getBarcode());
        }
        if (book != null) {
            bookRepository.deleteById(book.getBookId());
        }
    }

    private BookEntity createBook() {
        BookEntity book = new BookEntity();
        book.setTitle("Week 6 Integration " + UUID.randomUUID());
        book.setIsbn("W6-" + UUID.randomUUID());
        book.setAuthor("Integration Test");
        book.setPublisher("HUIT");
        book.setLanguage("vi");
        book.setIsDigital(false);
        return bookRepository.saveAndFlush(book);
    }

    @Test
    void importKeepsEachLineShelfAndBarcodeProvenanceAfterLocationRename() {
        var supplier = createSupplier(); var first = createBook(); var second = createBook();
        second.setIsbn("9780201616224"); bookRepository.saveAndFlush(second);
        var a = line(first.getBookId(), 2, "100000"); var b = line(first.getBookId(), 1, "100000");
        var c = line(null, 3, "150000"); c.setIsbn("0-201-61622-X");
        var receipt = inventoryService.importBooks(importRequest(supplier.getSupplierId(), List.of(a, b, c)));
        assertThat(receipt.getQuantity()).isEqualTo(6);
        assertThat(receipt.getTotalAmount()).isEqualByComparingTo("750000");
        assertThat(receipt.getLines()).hasSize(3);
        var allBarcodes = new java.util.HashSet<String>();
        for (var row : receipt.getLines()) {
            assertThat(row.barcodes()).hasSize(row.quantity());
            for (var barcode : row.barcodes()) {
                assertThat(allBarcodes.add(barcode)).isTrue();
                var copy = bookCopyRepository.findById(barcode).orElseThrow();
                assertThat(copy.getShelf().getShelfId()).isEqualTo(row.shelfId());
                assertThat(copy.getReceiptDetailId()).isEqualTo(row.detailId());
                assertThat(copy.getStatus()).isEqualTo("AVAILABLE");
            }
        }
        jdbcTemplate.update("UPDATE floors SET name='Tên tầng sau khi nhập' WHERE floor_id=?", a.getFloorId());
        var reread = inventoryService.getReceipt(receipt.getReceiptId());
        assertThat(reread.getLines()).allMatch(row -> row.locationPath().startsWith("Tầng nhập kiểm thử"));
        assertThat(reread.getLines().stream().flatMap(row -> row.barcodes().stream())).containsExactlyInAnyOrderElementsOf(allBarcodes);
    }

    @Test
    void invalidMissingOrMovedLocationRejectsWholeImportWithoutUnlocatedCopies() {
        var supplier = createSupplier(); var book = createBook();
        long receipts = goodsReceiptRepository.count(), details = receiptDetailRepository.count(), copies = bookCopyRepository.count();
        var valid = line(book.getBookId(), 2, "100"); var bad = line(book.getBookId(), 1, "200");
        bad.setShelfId(Long.MAX_VALUE);
        assertThatThrownBy(() -> inventoryService.importBooks(importRequest(supplier.getSupplierId(), List.of(valid, bad))))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("Ngăn không tồn tại");
        bad.setShelfId(valid.getShelfId()); // wrong submitted parent path, even though the shelf itself exists
        assertThatThrownBy(() -> inventoryService.importBooks(importRequest(supplier.getSupplierId(), List.of(valid, bad))))
                .hasMessageContaining("Cây vị trí đã thay đổi");
        bad.setShelfId(null);
        assertThatThrownBy(() -> inventoryService.importBooks(importRequest(supplier.getSupplierId(), List.of(bad))))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(goodsReceiptRepository.count()).isEqualTo(receipts);
        assertThat(receiptDetailRepository.count()).isEqualTo(details);
        assertThat(bookCopyRepository.count()).isEqualTo(copies);
    }

    @Test
    void importRollsBackReceiptLinesCopiesAndNotificationsWhenLastCopyInsertFails() {
        var supplier = createSupplier(); var first = createBook(); var last = createBook();
        var request = importRequest(supplier.getSupplierId(), List.of(line(first.getBookId(), 2, "100"), line(last.getBookId(), 1, "100")));
        long receipts = goodsReceiptRepository.count(), details = receiptDetailRepository.count(), copies = bookCopyRepository.count(), notifications = notificationRepository.count();
        jdbcTemplate.execute("""
                CREATE FUNCTION b06_reject_copy() RETURNS trigger AS $$ BEGIN
                  IF NEW.book_id = %d THEN RAISE EXCEPTION 'forced inventory copy failure'; END IF;
                  RETURN NEW;
                END; $$ LANGUAGE plpgsql
                """.formatted(last.getBookId()));
        jdbcTemplate.execute("CREATE TRIGGER b06_reject_copy_trigger BEFORE INSERT ON book_copies FOR EACH ROW EXECUTE FUNCTION b06_reject_copy()");
        try {
            assertThatThrownBy(() -> inventoryService.importBooks(request)).isInstanceOf(RuntimeException.class).hasStackTraceContaining("forced inventory copy failure");
            assertThat(goodsReceiptRepository.count()).isEqualTo(receipts);
            assertThat(receiptDetailRepository.count()).isEqualTo(details);
            assertThat(bookCopyRepository.count()).isEqualTo(copies);
            assertThat(notificationRepository.count()).isEqualTo(notifications);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER b06_reject_copy_trigger ON book_copies"); jdbcTemplate.execute("DROP FUNCTION b06_reject_copy()");
        }
    }

    @Test
    void independentImportsCanShareALocationWhileTheFirstReceiptIsStillUncommitted() throws Exception {
        var supplier = createSupplier(); var book = createBook();
        var request = importRequest(supplier.getSupplierId(), List.of(line(book.getBookId(), 1, "100")));
        var firstImported = new CountDownLatch(1); var release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<GoodsReceiptResponseDTO> first = executor.submit(() -> new org.springframework.transaction.support.TransactionTemplate(inventoryTransactionManager).execute(status -> {
                var result = inventoryService.importBooks(request); firstImported.countDown();
                try { if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Import test barrier timeout"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                return result;
            }));
            assertThat(firstImported.await(10, TimeUnit.SECONDS)).isTrue();
            Future<GoodsReceiptResponseDTO> second = executor.submit(() -> inventoryService.importBooks(request));
            var secondReceipt = second.get(8, TimeUnit.SECONDS); // must not wait for the first location's shared lock
            release.countDown(); var firstReceipt = first.get(8, TimeUnit.SECONDS);
            assertThat(firstReceipt.getReceiptId()).isNotEqualTo(secondReceipt.getReceiptId());
            assertThat(firstReceipt.getLines().get(0).barcodes()).doesNotContainAnyElementsOf(secondReceipt.getLines().get(0).barcodes());
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void identificationUsesCatalogEditionAndExistingClassificationButNeverInventsAdminRules() {
        var book = createBook(); book.setIsbn("9780132350884"); bookRepository.saveAndFlush(book);
        var anotherEdition = createBook(); anotherEdition.setIsbn("9780134494166"); bookRepository.saveAndFlush(anotherEdition);
        long subject = jdbcTemplate.queryForObject("INSERT INTO recommendation_subjects(code,name,active) VALUES (?, 'Lĩnh vực kiểm thử', true) RETURNING subject_id", Long.class, "B07-" + UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO book_subject_assignments(book_id,subject_id,confidence,classification_source,evidence,primary_subject) VALUES (?,?,0.9,'MANUAL','Đối chiếu danh mục',true)", book.getBookId(), subject);
        var result = inventoryIdentification.identify("0-13-235088-2", null);
        assertThat(result.bookId()).isEqualTo(book.getBookId()); assertThat(result.canonicalIsbn()).isEqualTo("9780132350884");
        assertThat(result.subjects()).hasSize(1); assertThat(result.subjects().get(0).source()).isEqualTo("MANUAL");
        assertThat(result.classificationStatus()).isEqualTo("AVAILABLE");
        assertThat(result.placementStatus()).isEqualTo("WAITING_ADMIN_RULES"); assertThat(result.suggestedShelfId()).isNull();
        assertThat(inventoryIdentification.identify("9780134494166", null).bookId()).isEqualTo(anotherEdition.getBookId());
        var modern = createBook(); modern.setIsbn("9791090636071"); bookRepository.saveAndFlush(modern);
        var noIsbn = createBook(); noIsbn.setIsbn(null); bookRepository.saveAndFlush(noIsbn);
        assertThat(inventoryIdentification.identify("9791090636071", null).bookId()).isEqualTo(modern.getBookId());
        assertThatThrownBy(() -> inventoryIdentification.identify("9780132350885", null)).hasMessageContaining("không hợp lệ");
        var duplicate = createBook(); duplicate.setIsbn("0-13-235088-2"); bookRepository.saveAndFlush(duplicate);
        assertThatThrownBy(() -> inventoryIdentification.identify("9780132350884", null)).hasMessageContaining("nhiều đầu sách");
        assertThat(inventoryIdentification.identify(null, book.getBookId()).bookId()).isEqualTo(book.getBookId());
        var badLine = line(book.getBookId(), 1, "100"); badLine.setIsbn("9780134494166");
        assertThatThrownBy(() -> inventoryService.importBooks(importRequest(createSupplier().getSupplierId(), List.of(badLine))))
                .hasMessageContaining("ISBN không khớp");
    }

    @Test
    void importLocationAndIdentificationHttpContractsRequireStaffAndValidateEveryLine() throws Exception {
        var supplier = createSupplier(); var book = createBook(); book.setIsbn("9780201530827"); bookRepository.saveAndFlush(book);
        var request = importRequest(supplier.getSupplierId(), List.of(line(book.getBookId(), 2, "100000")));
        var student = createUserWithRole("b06-student", "STUDENT", "Import@123");
        String studentToken = loginAndGetToken(student.getEmail(), "Import@123");
        mockMvc.perform(get("/inventory/catalog-identification").param("isbn", "9780201530827").header("Authorization", "Bearer " + studentToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/inventory/receipts/1").header("Authorization", "Bearer " + studentToken)).andExpect(status().isForbidden());
        mockMvc.perform(post("/inventory/receipts").header("Authorization", "Bearer " + studentToken).contentType("application/json").content(objectMapper.writeValueAsString(request))).andExpect(status().isForbidden());
        for (String role : List.of("LIBRARIAN", "ADMIN")) {
            var staff = createUserWithRole("b06-" + role, role, "Import@123"); String token = loginAndGetToken(staff.getEmail(), "Import@123");
            mockMvc.perform(get("/inventory/catalog-identification").param("isbn", "0-201-53082-1").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.bookId").value(book.getBookId()))
                    .andExpect(jsonPath("$.placementStatus").value("WAITING_ADMIN_RULES"));
            var line = request.getLines().get(0); Long shelf = line.getShelfId(); line.setShelfId(null);
            mockMvc.perform(post("/inventory/receipts").header("Authorization", "Bearer " + token).contentType("application/json").content(objectMapper.writeValueAsString(request))).andExpect(status().isBadRequest());
            line.setShelfId(shelf);
            var saved = mockMvc.perform(post("/inventory/receipts").header("Authorization", "Bearer " + token).contentType("application/json").content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].shelfId").value(shelf))
                    .andExpect(jsonPath("$.lines[0].barcodes.length()").value(2)).andReturn().getResponse().getContentAsString();
            long receiptId = objectMapper.readTree(saved).get("receiptId").asLong();
            mockMvc.perform(get("/inventory/receipts/" + receiptId).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].locationPath").isNotEmpty());
        }
    }

    private ImportReceiptRequestDTO importRequest(Long supplierId, List<ImportReceiptRequestDTO.Line> lines) {
        ImportReceiptRequestDTO request = new ImportReceiptRequestDTO();
        request.setSupplierId(supplierId);
        request.setLines(lines);
        return request;
    }

    private ImportReceiptRequestDTO.Line line(Long bookId, int quantity, String unitPrice) {
        ImportReceiptRequestDTO.Line line = new ImportReceiptRequestDTO.Line();
        line.setBookId(bookId);
        line.setQuantity(quantity);
        line.setUnitPrice(new BigDecimal(unitPrice));
        long floor = jdbcTemplate.queryForObject("INSERT INTO floors(name) VALUES ('Tầng nhập kiểm thử') RETURNING floor_id", Long.class);
        long bookshelf = jdbcTemplate.queryForObject("INSERT INTO bookshelves(name,floor_id) VALUES ('Kệ nhập kiểm thử',?) RETURNING bookshelf_id", Long.class, floor);
        long shelf = jdbcTemplate.queryForObject("INSERT INTO shelves(name,bookshelf_id) VALUES ('Ngăn nhập kiểm thử',?) RETURNING shelf_id", Long.class, bookshelf);
        line.setShelfId(shelf); line.setFloorId(floor); line.setBookshelfId(bookshelf);
        return line;
    }

    @Test
    void proposalSummaryRestrictsRolesAndExportsAllFilteredGroupsWithoutChangingApprovals() throws Exception {
        String base = "/proposals/admin/summary";
        String faculty = "B08_" + UUID.randomUUID().toString().substring(0, 8);
        String department = "Department " + faculty;
        jdbcTemplate.update("INSERT INTO academic_faculties(faculty_id,faculty_name) VALUES (?,?)", faculty, "Khoa B08");
        jdbcTemplate.update("INSERT INTO academic_department_mappings(department_value,faculty_id,major_name) VALUES (?,?,?)", department, faculty, "Ngành B08");
        var student = createUserWithRole("b08-http-student", "STUDENT", "Proposal@123");
        student.setUserType("STUDENT"); student.setDepartment(department); userRepository.saveAndFlush(student);
        for (int i = 0; i < 3; i++) jdbcTemplate.update("INSERT INTO book_proposals(user_id,title,author_name,publisher,edition,publication_year,status,urgency_level,created_at) VALUES (?,?,'B08 Author','NXB','1',2026,'PENDING','NORMAL',?)",
                student.getUserId(), "Book " + i, java.time.LocalDateTime.parse("2026-10-01T12:00:00"));
        String studentToken = loginAndGetToken(student.getEmail(), "Proposal@123");
        for (String path : List.of(base, base + "/export", base + "/faculties")) {
            mockMvc.perform(get(path)).andExpect(status().isForbidden());
            mockMvc.perform(get(path).header("Authorization", "Bearer " + studentToken)).andExpect(status().isForbidden());
        }
        for (String role : List.of("LIBRARIAN", "ADMIN")) {
            var staff = createUserWithRole("b08-http-" + role, role, "Proposal@123");
            String token = loginAndGetToken(staff.getEmail(), "Proposal@123");
            mockMvc.perform(get(base).header("Authorization", "Bearer " + token).param("facultyId", faculty).param("size", "1"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.totalGroups").value(3)).andExpect(jsonPath("$.totalProposals").value(3))
                    .andExpect(jsonPath("$.totalPeople").value(1)).andExpect(jsonPath("$.groups.length()").value(1));
            byte[] bytes = mockMvc.perform(get(base + "/export").header("Authorization", "Bearer " + token)
                            .header("Origin", "http://localhost:4200").param("facultyId", faculty).param("status", "PENDING")
                            .param("from", "2026-10-01").param("to", "2026-10-01").param("page", "99").param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .andExpect(header().string("Access-Control-Expose-Headers", org.hamcrest.Matchers.containsString("Content-Disposition")))
                    .andReturn().getResponse().getContentAsByteArray();
            try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
                assertThat(book.getSheet("Tong hop").getLastRowNum()).isEqualTo(7);
                assertThat(book.getSheet("De xuat goc").getLastRowNum()).isEqualTo(7);
            }
            mockMvc.perform(get(base + "/faculties").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
            mockMvc.perform(get(base).header("Authorization", "Bearer " + token).param("status", "INVALID")).andExpect(status().isBadRequest());
            mockMvc.perform(get(base).header("Authorization", "Bearer " + token).param("from", "29/10/2026")).andExpect(status().isBadRequest());
            mockMvc.perform(get(base + "/export").header("Authorization", "Bearer " + token).param("facultyId", "UNKNOWN")).andExpect(status().isBadRequest());
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM book_proposals WHERE user_id=? AND status='PENDING'", Long.class, student.getUserId())).isEqualTo(3);
    }

    @Test
    void dashboardAnalyticsProtectRolesAndLibrarianClassificationIsVersionedAndAudited() throws Exception {
        String base="/dashboard/analytics";
        var book=createBook();String major="DASH-"+UUID.randomUUID();String faculty="FAC-"+UUID.randomUUID().toString().substring(0,8);
        jdbcTemplate.update("INSERT INTO academic_faculties(faculty_id,faculty_name) VALUES (?,?)",faculty,"Khoa API");
        jdbcTemplate.update("INSERT INTO academic_majors(major_id,faculty_id,major_name,source_reference) VALUES (?,?,?,?)",major,faculty,"Ngành API","Danh mục kiểm thử");
        var student=createUserWithRole("b11-reader","STUDENT","Analytics@123");String studentToken=loginAndGetToken(student.getEmail(),"Analytics@123");
        String mapping=base+"/materials/BOOK/"+book.getBookId()+"/classification";
        for(String path:List.of(base+"/circulation",base+"/rankings",base+"/overdue",base+"/holdings",base+"/materials",base+"/academic-catalog",base+"/finance",base+"/finance/entries",base+"/finance/payments/999999/allocations",base+"/stock-movements",base+"/stock-movements/receipts",mapping)){
            mockMvc.perform(get(path)).andExpect(status().isForbidden());mockMvc.perform(get(path).header("Authorization","Bearer "+studentToken)).andExpect(status().isForbidden());
        }
        mockMvc.perform(put(mapping).header("Authorization","Bearer "+studentToken).contentType("application/json").content("{\"version\":0,\"majorIds\":[\""+major+"\"],\"sourceReference\":\"Nguồn API\"}")).andExpect(status().isForbidden());
        for(String role:List.of("LIBRARIAN","ADMIN")){
            var staff=createUserWithRole("b11-"+role,role,"Analytics@123");String token=loginAndGetToken(staff.getEmail(),"Analytics@123");
            for(String path:List.of(base+"/circulation",base+"/rankings",base+"/overdue",base+"/holdings",base+"/materials",base+"/academic-catalog",base+"/finance",base+"/finance/entries",base+"/stock-movements",base+"/stock-movements/receipts",mapping))mockMvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
            mockMvc.perform(get(base+"/finance").header("Authorization","Bearer "+token).param("from","2026-03-01").param("until","2026-02-01")).andExpect(status().isBadRequest());
            mockMvc.perform(get(base+"/finance/entries").header("Authorization","Bearer "+token).param("kind","UNKNOWN")).andExpect(status().isBadRequest());
            mockMvc.perform(get(base+"/stock-movements/receipts").header("Authorization","Bearer "+token).param("size","101")).andExpect(status().isBadRequest());
            mockMvc.perform(get(base+"/stock-movements").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.disposals.status").value("WAITING_ADMIN_WORKFLOW")).andExpect(jsonPath("$.disposals.liquidatedCopies").isEmpty());
            long version=role.equals("LIBRARIAN")?0:1;
            String body=objectMapper.writeValueAsString(java.util.Map.of("version",version,"majorIds",List.of(major),"sourceReference","Nguồn API "+role));
            mockMvc.perform(put(mapping).header("Authorization","Bearer "+token).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.current.version").value(version+1)).andExpect(jsonPath("$.current.updatedBy").value(staff.getUserId().toString())).andExpect(jsonPath("$.current.majors[0].majorId").value(major));
            mockMvc.perform(get(base+"/holdings").header("Authorization","Bearer "+token).param("majorId",major)).andExpect(status().isOk()).andExpect(jsonPath("$.totals.materials").value(1));
            mockMvc.perform(get(base+"/materials/BOOK/"+book.getBookId()+"/copies").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
            mockMvc.perform(get(base+"/circulation").header("Authorization","Bearer "+token).param("from","2026-02-01").param("until","2026-01-01")).andExpect(status().isBadRequest());
            mockMvc.perform(get(base+"/rankings").header("Authorization","Bearer "+token).param("size","101")).andExpect(status().isBadRequest());
            mockMvc.perform(get(base+"/materials").header("Authorization","Bearer "+token).param("majorId","NO-SUCH-MAJOR")).andExpect(status().isBadRequest());
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM material_classification_history h JOIN material_classifications p ON p.profile_id=h.profile_id WHERE p.material_id=? AND p.material_kind='BOOK'",Long.class,book.getBookId())).isEqualTo(2);
    }

    @Test
    void financeReportReconcilesLiveDepositAndTwoPaymentsWithReplay() {
        var fixture=inspectionFixture(1,"50000");String key="b13-live-"+UUID.randomUUID();
        var ids=new java.util.HashSet<Long>();
        java.time.LocalDate today=java.time.LocalDate.now();
        var period=com.huit.library.modules.dashboard.dto.CirculationAnalytics.Period.MONTH;
        try {
            var before=financeStockAnalytics.finance(today,today.plusDays(1),period);
            returnInspectionService.inspect(inspectionRequest(key,fixture,"DAMAGED"),UUID.randomUUID());
            var charge=transactionRepository.findByUserIdOrderByCreatedAtAsc(fixture.reader().getUserId()).stream()
                    .filter(t->"REPAIR_FEE".equals(t.getType())).findFirst().orElseThrow();
            assertThat(charge.getDepositAppliedAmount()).isEqualByComparingTo("50000");
            assertThat(charge.getDepositAppliedAt()).isNotNull();
            assertThat(walletRepository.findByUserId(fixture.reader().getUserId()).orElseThrow().getTotalDebt()).isEqualByComparingTo("150000");
            for(int amount:new int[]{60000,90000}) {
                var request=new DebtPaymentRequestDTO();request.setUserId(fixture.reader().getUserId());
                request.setAmount(BigDecimal.valueOf(amount));request.setMethod(amount==60000?"CASH":"BANK_TRANSFER");
                request.setIdempotencyKey("b13-payment-"+UUID.randomUUID());
                var payment=debtLedgerService.recordPayment(request,fixture.reader().getUserId());ids.add(payment.getPaymentTransactionId());
                assertThat(debtLedgerService.recordPayment(request,fixture.reader().getUserId()).getPaymentTransactionId()).isEqualTo(payment.getPaymentTransactionId());
            }
            var reread=transactionRepository.findById(charge.getTransactionId()).orElseThrow();
            assertThat(reread.getPaymentMethod()).isEqualTo("BANK_TRANSFER");
            assertThat(reread.getDepositAppliedAmount()).isEqualByComparingTo("50000");
            assertThat(reread.getDepositAppliedAt()).isEqualTo(charge.getDepositAppliedAt());
            var after=financeStockAnalytics.finance(today,today.plusDays(1),period);
            assertThat(after.totals().chargesAccrued().subtract(before.totals().chargesAccrued())).isEqualByComparingTo("200000");
            assertThat(after.totals().depositApplied().subtract(before.totals().depositApplied())).isEqualByComparingTo("50000");
            assertThat(after.totals().moneyReceived().subtract(before.totals().moneyReceived())).isEqualByComparingTo("150000");
            assertThat(after.totals().allocatedPayments().subtract(before.totals().allocatedPayments())).isEqualByComparingTo("150000");
            assertThat(walletRepository.findByUserId(fixture.reader().getUserId()).orElseThrow().getTotalDebt()).isZero();
        } finally {
            for(Long id:ids)paymentAllocationRepository.deleteAll(paymentAllocationRepository.findByPaymentTransactionIdOrderByAllocationId(id));
            cleanupInspection(fixture,key);
        }
    }

    private String waitForCapturedMail(String recipient) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025)
                + "/api/v1/messages");
        for (int attempt = 0; attempt < 20; attempt++) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            // Other integration scenarios can legitimately send their own notifications to
            // the same Mailpit container. Wait for this test's unique recipient instead of
            // assuming that the mailbox contains exactly one message.
            if (response.statusCode() == 200 && response.body().contains(recipient)) {
                return response.body();
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("Mailpit did not capture the OTP email");
    }
}
