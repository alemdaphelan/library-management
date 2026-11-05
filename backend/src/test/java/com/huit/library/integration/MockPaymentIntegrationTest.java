package com.huit.library.integration;

import com.fasterxml.jackson.databind.*;
import com.google.firebase.messaging.FirebaseMessaging;
import com.huit.library.core.security.*;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.circulation.entity.*;
import com.huit.library.modules.circulation.repository.*;
import com.huit.library.modules.finance.entity.*;
import com.huit.library.modules.finance.repository.*;
import com.huit.library.modules.penalty.entity.PenaltyCaseEntity;
import com.huit.library.modules.penalty.repository.PenaltyCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.data.mongodb.repositories.enabled=false", "spring.data.elasticsearch.repositories.enabled=false",
        "spring.kafka.listener.auto-startup=false", "management.health.mail.enabled=false", "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=none",
        "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=drop-and-create",
        "app.database-seeding.enabled=false", "library.rag.enabled=false", "library.circulation.scheduler-cron=0 0 0 1 1 *",
        "library.circulation.default-open-time=00:00", "library.circulation.default-close-time=23:59:59.999999999",
        "app.jwtSecret=f05-isolated-test-secret-never-a-real-account-secret", "library.payment.mock.enabled=true"
})
class MockPaymentIntegrationTest {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl); r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword); r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @LocalServerPort int port;
    @Autowired UserRepository users; @Autowired WalletRepository wallets;
    @Autowired TransactionRepository transactions; @Autowired OnlinePaymentSessionRepository sessions;
    @Autowired PaymentAllocationRepository allocations; @Autowired PenaltyCaseRepository cases;
    @Autowired LoanTicketRepository tickets; @Autowired LoanDetailRepository details;
    @Autowired ObjectMapper json; @Autowired JwtUtils jwt;
    @MockBean JavaMailSender mail; @MockBean FirebaseMessaging firebase;
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test void concurrentCreateAndFinishCreditDepositOnceWithoutPayingAnyDebt() throws Exception {
        var user = reader("STUDENT"); wallet(user, "10000", "30000");
        var payload = Map.of("amount", 50000, "purpose", "DEPOSIT", "idempotencyKey", UUID.randomUUID());
        var creates = new ArrayList<java.util.concurrent.CompletableFuture<HttpResponse<String>>>();
        for (int i = 0; i < 4; i++) creates.add(client.sendAsync(request(user, base(), payload), HttpResponse.BodyHandlers.ofString()));
        String reference = null;
        for (var pending : creates) {
            var result = pending.get(20, TimeUnit.SECONDS); assertThat(result.statusCode()).isEqualTo(200);
            String value = json.readTree(result.body()).path("reference").asText();
            if (reference == null) reference = value; else assertThat(value).isEqualTo(reference);
        }
        assertThat(wallets.findByUserId(user.getUserId()).orElseThrow().getDepositBalance()).isEqualByComparingTo("10000");
        var finishes = new ArrayList<java.util.concurrent.CompletableFuture<HttpResponse<String>>>();
        for (int i = 0; i < 4; i++) finishes.add(client.sendAsync(request(user, base() + "/" + reference + "/finish", Map.of("outcome", "SUCCESS")), HttpResponse.BodyHandlers.ofString()));
        long paymentId = 0;
        for (var pending : finishes) {
            var result = pending.get(20, TimeUnit.SECONDS); assertThat(result.statusCode()).isEqualTo(200);
            var body = json.readTree(result.body()); assertThat(body.path("status").asText()).isEqualTo("COMPLETED");
            if (paymentId == 0) paymentId = body.path("paymentTransactionId").asLong();
            else assertThat(body.path("paymentTransactionId").asLong()).isEqualTo(paymentId);
        }
        var wallet = wallets.findByUserId(user.getUserId()).orElseThrow();
        assertThat(wallet.getDepositBalance()).isEqualByComparingTo("60000"); assertThat(wallet.getTotalDebt()).isEqualByComparingTo("30000");
        assertThat(transactions.findByUserIdOrderByCreatedAtAsc(user.getUserId())).singleElement().satisfies(t -> {
            assertThat(t.getPaymentMethod()).isEqualTo("MOCK"); assertThat(t.getType()).isEqualTo("DEPOSIT_TOPUP");
            assertThat(t.getReason()).contains("GIẢ LẬP"); assertThat(t.getReceiptNumber()).startsWith("MOCK-");
        });
        assertThat(post(user, base(), payload).statusCode()).isEqualTo(200);
        assertThat(post(user, base(), Map.of("amount", 60000, "purpose", "DEPOSIT", "idempotencyKey", payload.get("idempotencyKey"))).statusCode()).isEqualTo(409);
    }

    @Test void debtPaymentsAllocatePartiallyAndFullyWithoutChangingDepositOrTheDamagedReceiptTime() throws Exception {
        var user = reader("STUDENT"); wallet(user, "10000", "30000");
        var ticket = new LoanTicketEntity(); ticket.setUserId(user.getUserId()); ticket.setTicketType("BORROW"); ticket.setStatus("RETURNED"); ticket = tickets.saveAndFlush(ticket);
        var receivedAt = LocalDateTime.of(2026, 10, 31, 10, 0);
        var detail = new LoanDetailEntity(); detail.setTicketId(ticket.getTicketId()); detail.setReturnStatus("DAMAGED"); detail.setPhysicalReturnAt(receivedAt); detail.setReturnDate(receivedAt); detail = details.saveAndFlush(detail);
        var penalty = new PenaltyCaseEntity(); penalty.setUserId(user.getUserId()); penalty.setTicketId(ticket.getTicketId()); penalty.setLoanDetailId(detail.getDetailId());
        penalty.setTotalAmount(new BigDecimal("30000")); penalty.setPaidAmount(BigDecimal.ZERO); penalty.setStatus("OUTSTANDING"); penalty.setSettlementMethod("REPAIR"); penalty = cases.saveAndFlush(penalty);
        var charge = charge(user, "30000"); charge.setPenaltyCaseId(penalty.getCaseId()); transactions.saveAndFlush(charge);
        var first = create(user, "DEBT", 10000);
        assertThat(post(user, finish(first), Map.of("outcome", "SUCCESS", "amount", 999999)).statusCode()).isEqualTo(200);
        assertThat(wallets.findByUserId(user.getUserId()).orElseThrow().getTotalDebt()).isEqualByComparingTo("20000");
        assertThat(transactions.findById(charge.getTransactionId()).orElseThrow().getStatus()).isEqualTo("PARTIALLY_PAID");
        var second = create(user, "DEBT", 20000);
        assertThat(post(user, finish(second), Map.of("outcome", "SUCCESS")).statusCode()).isEqualTo(200);
        var wallet = wallets.findByUserId(user.getUserId()).orElseThrow();
        assertThat(wallet.getTotalDebt()).isEqualByComparingTo("0"); assertThat(wallet.getDepositBalance()).isEqualByComparingTo("10000");
        assertThat(cases.findById(penalty.getCaseId()).orElseThrow().getStatus()).isEqualTo("PAID");
        var saved = details.findById(detail.getDetailId()).orElseThrow();
        assertThat(saved.getReturnDate()).isEqualTo(receivedAt); assertThat(saved.getReturnStatus()).isEqualTo("DAMAGED");
        assertThat(allocations.findByPaymentTransactionIdOrderByAllocationId(json.readTree(post(user, finish(second), Map.of("outcome", "SUCCESS")).body()).path("paymentTransactionId").asLong())).singleElement();
    }

    @Test void cancelFailureAndExpiryNeverWriteMoneyAndCannotLaterBecomeSuccess() throws Exception {
        var user = reader("STUDENT"); wallet(user, "10000", "0");
        for (var outcome : List.of("CANCEL", "FAIL", "EXPIRE")) {
            var session = create(user, "DEPOSIT", 50000);
            if (outcome.equals("EXPIRE")) {
                var row = sessions.findByReferenceAndUserId(session.path("reference").asText(), user.getUserId()).orElseThrow();
                row.setExpiresAt(LocalDateTime.now().minusMinutes(1)); sessions.saveAndFlush(row);
            }
            var response = post(user, finish(session), Map.of("outcome", outcome.equals("EXPIRE") ? "SUCCESS" : outcome));
            String expected = outcome.equals("CANCEL") ? "CANCELLED" : outcome.equals("FAIL") ? "FAILED" : "EXPIRED";
            assertThat(response.statusCode()).isEqualTo(200); assertThat(json.readTree(response.body()).path("status").asText()).isEqualTo(expected);
            assertThat(json.readTree(post(user, finish(session), Map.of("outcome", "SUCCESS")).body()).path("status").asText()).isEqualTo(expected);
        }
        assertThat(transactions.findByUserIdOrderByCreatedAtAsc(user.getUserId())).isEmpty();
        assertThat(wallets.findByUserId(user.getUserId()).orElseThrow().getDepositBalance()).isEqualByComparingTo("10000");
    }

    @Test void authenticationOwnershipValidationAndChangedDebtAreEnforcedAtRealHttpBoundary() throws Exception {
        var user = reader("STUDENT"); var other = reader("STUDENT"); var staff = reader("LIBRARIAN");
        wallet(user, "0", "10000"); var debt = charge(user, "10000");
        var body = Map.of("purpose", "DEPOSIT", "amount", 50000, "idempotencyKey", UUID.randomUUID());
        assertThat(post(null, base(), body).statusCode()).isIn(401,403); assertThat(post(staff, base(), body).statusCode()).isEqualTo(403);
        for (var amount : List.of("0", "1000.5", "100000001"))
            assertThat(post(user, base(), Map.of("purpose", "DEPOSIT", "amount", amount, "idempotencyKey", UUID.randomUUID())).statusCode()).isEqualTo(400);
        assertThat(post(user, base(), Map.of("purpose", "DEBT", "amount", 20000, "idempotencyKey", UUID.randomUUID())).statusCode()).isEqualTo(409);
        var session = create(user, "DEBT", 10000);
        assertThat(post(other, finish(session), Map.of("outcome", "SUCCESS")).statusCode()).isEqualTo(404);
        var otherGet = client.send(get(other, base() + "/" + session.path("reference").asText()), HttpResponse.BodyHandlers.ofString());
        assertThat(otherGet.statusCode()).isEqualTo(404);
        var wallet = wallets.findByUserId(user.getUserId()).orElseThrow(); wallet.setTotalDebt(BigDecimal.ZERO); wallets.saveAndFlush(wallet);
        assertThat(post(user, finish(session), Map.of("outcome", "SUCCESS")).statusCode()).isEqualTo(409);
        assertThat(transactions.findById(debt.getTransactionId()).orElseThrow().getPaidAmount()).isEqualByComparingTo("0");
        assertThat(sessions.findByReferenceAndUserId(session.path("reference").asText(), user.getUserId()).orElseThrow().getStatus()).isEqualTo("PENDING");
        assertThat(post(staff, "/api/v1/transactions/payments", Map.of("userId",user.getUserId(),"amount",1000,"method","MOCK","idempotencyKey",UUID.randomUUID().toString())).statusCode()).isEqualTo(400);
    }

    String base() { return "/api/v1/mock-payments/sessions"; }
    String finish(JsonNode session) { return base() + "/" + session.path("reference").asText() + "/finish"; }
    JsonNode create(UserEntity user, String purpose, int amount) throws Exception {
        var response = post(user, base(), Map.of("purpose", purpose, "amount", amount, "idempotencyKey", UUID.randomUUID()));
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200); return json.readTree(response.body());
    }
    UserEntity reader(String role) {
        var user = new UserEntity(); user.setRoleId(role); user.setUserType("STUDENT".equals(role) ? "STUDENT" : "STAFF");
        user.setEmail("f05-" + UUID.randomUUID() + "@example.invalid"); user.setMssv("F05-" + UUID.randomUUID());
        user.setFullName("F05 isolated test"); user.setPasswordHash("not-used-by-test-jwt"); user.setCardStatus("ACTIVE"); user.setCardExpiryDate(LocalDateTime.now().plusYears(1)); return users.saveAndFlush(user);
    }
    void wallet(UserEntity user, String deposit, String debt) { var w = new WalletEntity(); w.setUserId(user.getUserId()); w.setDepositBalance(new BigDecimal(deposit)); w.setTotalDebt(new BigDecimal(debt)); wallets.saveAndFlush(w); }
    TransactionEntity charge(UserEntity user, String amount) { var t = new TransactionEntity(); t.setUserId(user.getUserId()); t.setType("REPAIR_FEE"); t.setAmount(new BigDecimal(amount)); t.setStatus("OUTSTANDING"); return transactions.saveAndFlush(t); }
    HttpRequest.Builder builder(UserEntity user, String path) {
        var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(20));
        if (user != null) { var d = new CustomUserDetails(user); b.header("Authorization", "Bearer " + jwt.generateJwtToken(new UsernamePasswordAuthenticationToken(d,null,d.getAuthorities()))); }
        return b.header("Content-Type", "application/json");
    }
    HttpRequest request(UserEntity user, String path, Object body) throws Exception { return builder(user,path).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(); }
    HttpRequest get(UserEntity user, String path) { return builder(user,path).GET().build(); }
    HttpResponse<String> post(UserEntity user, String path, Object body) throws Exception { return client.send(request(user,path,body),HttpResponse.BodyHandlers.ofString()); }
}
