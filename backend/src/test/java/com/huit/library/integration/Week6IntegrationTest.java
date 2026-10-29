package com.huit.library.integration;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.auth.service.AuthService;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.circulation.dto.CheckoutRequestDTO;
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
    private InventoryAuditService inventoryAuditService;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private GoodsReceiptRepository goodsReceiptRepository;
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
            assertThat(receipt.getTotalAmount()).isEqualByComparingTo("250000");
            assertThat(receiptDetailRepository.findByReceiptId(receipt.getReceiptId())).hasSize(1);
            assertThat(bookCopyRepository.findByBookId(book.getBookId())).hasSize(2);
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
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Book not found");

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
                    .contains("Yêu cầu mượn tài liệu đã được duyệt")
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
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andReturn().getResponse().getContentAsString();
            Long ticketId = objectMapper.readTree(reservationJson).get("ticketId").asLong();
            ticket = loanTicketRepository.findById(ticketId).orElseThrow();

            mockMvc.perform(post("/loans/{ticketId}/approve", ticketId)
                            .header("Authorization", "Bearer " + librarianToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RESERVED"));

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
        return line;
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
