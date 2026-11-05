package com.huit.library.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.FirebaseMessaging;
import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.core.security.JwtUtils;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.finance.repository.TransactionRepository;
import com.huit.library.modules.notification.repository.NotificationRepository;
import com.huit.library.modules.penalty.repository.PenaltyCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.data.mongodb.repositories.enabled=false", "spring.data.elasticsearch.repositories.enabled=false",
        "spring.kafka.listener.auto-startup=false", "management.health.mail.enabled=false",
        "spring.liquibase.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=none",
        "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=drop-and-create",
        "app.database-seeding.enabled=false", "library.rag.enabled=false",
        "library.circulation.scheduler-cron=0 0 0 1 1 *",
        "library.circulation.default-open-time=00:00", "library.circulation.default-close-time=23:59:59.999999999",
        "app.jwtSecret=f04-isolated-test-secret-never-a-real-account-secret"
})
class LoanLossReportIntegrationTest {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl); r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword); r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired BookCopyRepository copies;
    @Autowired LoanTicketRepository tickets;
    @Autowired LoanDetailRepository details;
    @Autowired WalletRepository wallets;
    @Autowired TransactionRepository transactions;
    @Autowired PenaltyCaseRepository cases;
    @Autowired NotificationRepository notifications;
    @Autowired ObjectMapper json;
    @Autowired JwtUtils jwt;
    @MockBean JavaMailSender mail;
    @MockBean FirebaseMessaging firebase;
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test void concurrentReportsPersistOneOriginalReportNotifyStaffAndDoNotChargeOrChangeCustody() throws Exception {
        var f = fixture("OVERDUE", "OVERDUE");
        var staff = reader("LIBRARIAN");
        var admin = reader("ADMIN");
        long transactionCount = transactions.count(), caseCount = cases.count();
        var dueAt = f.detail().getDueDate();
        var requests = new ArrayList<java.util.concurrent.CompletableFuture<HttpResponse<String>>>();
        for (int i = 0; i < 4; i++) requests.add(client.sendAsync(
                request(f.reader(), path(f), "POST", Map.of("description", "Mất sách ở phòng học " + i)), HttpResponse.BodyHandlers.ofString()));
        for (var result : requests) assertThat(result.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        var saved = details.findById(f.detail().getDetailId()).orElseThrow();
        assertThat(saved.getLossReportedAt()).isNotNull();
        assertThat(saved.getLossReportDescription()).startsWith("Mất sách ở phòng học ");
        for (var result : requests) {
            var body = json.readTree(result.get().body());
            assertThat(body.path("lossReportDescription").asText()).isEqualTo(saved.getLossReportDescription());
            assertThat(LocalDateTime.parse(body.path("lossReportedAt").asText())).isEqualTo(saved.getLossReportedAt());
        }
        assertThat(saved.getReturnStatus()).isEqualTo("OVERDUE");
        assertThat(saved.getDueDate()).isEqualTo(dueAt);
        assertThat(saved.getReturnDate()).isNull(); assertThat(saved.getPhysicalReturnAt()).isNull();
        assertThat(saved.getFineStoppedAt()).isNull();
        assertThat(copies.findById(saved.getBarcode()).orElseThrow().getStatus()).isEqualTo("BORROWING");
        assertThat(tickets.findById(f.ticket().getTicketId()).orElseThrow().getStatus()).isEqualTo("OVERDUE");
        assertThat(transactions.count()).isEqualTo(transactionCount); assertThat(cases.count()).isEqualTo(caseCount);
        var wallet = wallets.findByUserId(f.reader().getUserId()).orElseThrow();
        assertThat(wallet.getDepositBalance()).isEqualByComparingTo("50000");
        assertThat(wallet.getTotalDebt()).isEqualByComparingTo("12345");
        for (var recipient : List.of(staff, admin)) {
            assertThat(notifications.findByUserIdOrderByCreatedAtDesc(recipient.getUserId()).stream()
                    .filter(n -> "LOAN_LOSS_REPORTED".equals(n.getType()) && saved.getDetailId().toString().equals(n.getReferenceId())).toList())
                    .singleElement().satisfies(n -> assertThat(n.getReferenceType()).isEqualTo("LOAN_LOSS_REPORT"));
        }
        var response = send(staff, "/loans/loss-reports/" + saved.getDetailId(), "GET", null);
        assertThat(response.statusCode()).isEqualTo(200);
        var report = json.readTree(response.body());
        assertThat(report.path("userId").asText()).isEqualTo(f.reader().getUserId().toString());
        assertThat(report.path("mssv").asText()).isEqualTo(f.reader().getMssv());
        assertThat(report.path("items")).hasSize(1);
        assertThat(report.path("items").get(0).path("detailId").asLong()).isEqualTo(saved.getDetailId());
        assertThat(report.path("items").get(0).path("barcode").asText()).isEqualTo(saved.getBarcode());
        var history = send(f.reader(), "/loans/my-loans", "GET", null);
        assertThat(history.statusCode()).isEqualTo(200);
        assertThat(history.body()).contains(saved.getLossReportDescription());
    }

    @Test void reportRequiresStudentOwnerMatchingTicketAndValidDescriptionAndStaffReadPermission() throws Exception {
        var f = fixture("BORROWING", "BORROWING");
        var other = fixture("BORROWING", "BORROWING");
        var staff = reader("LIBRARIAN");
        assertThat(send(null, path(f), "POST", Map.of("description", "Mất")).statusCode()).isIn(401, 403);
        assertThat(send(staff, path(f), "POST", Map.of("description", "Mất")).statusCode()).isEqualTo(403);
        assertThat(send(other.reader(), path(f), "POST", Map.of("description", "Mất")).statusCode()).isEqualTo(403);
        assertThat(send(f.reader(), "/loans/" + f.ticket().getTicketId() + "/items/" + other.detail().getDetailId() + "/loss-report", "POST", Map.of("description", "Mất")).statusCode()).isEqualTo(404);
        for (var description : List.of("  ", "x".repeat(1001)))
            assertThat(send(f.reader(), path(f), "POST", Map.of("description", description)).statusCode()).isEqualTo(400);
        assertThat(send(f.reader(), path(f), "POST", Map.of("description", "  Rơi trên xe buýt  ")).statusCode()).isEqualTo(200);
        assertThat(send(f.reader(), "/loans/loss-reports/" + f.detail().getDetailId(), "GET", null).statusCode()).isEqualTo(403);
        assertThat(send(staff, "/loans/loss-reports/" + other.detail().getDetailId(), "GET", null).statusCode()).isEqualTo(404);
        assertThat(details.findById(f.detail().getDetailId()).orElseThrow().getLossReportDescription()).isEqualTo("Rơi trên xe buýt");
    }

    @Test void uncollectedReturnedOrIncidentItemsCannotBeReported() throws Exception {
        for (var state : List.of("PENDING", "CANCELLED", "RETURNED", "DAMAGED", "LOST", "SETTLED")) {
            var f = fixture("BORROWING", state);
            assertThat(send(f.reader(), path(f), "POST", Map.of("description", "Báo mất")).statusCode()).as(state).isEqualTo(409);
            assertThat(details.findById(f.detail().getDetailId()).orElseThrow().getLossReportedAt()).isNull();
        }
        var f = fixture("BORROWING", "BORROWING");
        var detail = f.detail(); detail.setPhysicalReturnAt(LocalDateTime.now()); details.saveAndFlush(detail);
        assertThat(send(f.reader(), path(f), "POST", Map.of("description", "Báo mất")).statusCode()).isEqualTo(409);
    }

    @Test void aReportedItemCannotRenewAndANormalReturnStillWorksWithSafeReportReplay() throws Exception {
        var f = fixture("BORROWING", "BORROWING");
        var staff = reader("LIBRARIAN");
        var response = send(f.reader(), path(f), "POST", Map.of("description", "Không tìm thấy sách"));
        assertThat(response.statusCode()).isEqualTo(200);
        var renew = send(f.reader(), "/loans/renew/" + f.ticket().getTicketId() + "/items/" + f.detail().getDetailId(), "POST", Map.of());
        assertThat(renew.statusCode()).isEqualTo(409);
        assertThat(details.findById(f.detail().getDetailId()).orElseThrow().getRenewalCount()).isZero();
        var returned = send(staff, "/loans/return", "POST", Map.of("barcodes", List.of(f.detail().getBarcode()), "idempotencyKey", "f04-" + UUID.randomUUID()));
        assertThat(returned.statusCode()).isEqualTo(200);
        long count = notifications.count();
        var replay = send(f.reader(), path(f), "POST", Map.of("description", "Gửi lại sau khi đã tìm thấy sách"));
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(json.readTree(replay.body()).path("lossReportDescription").asText()).isEqualTo("Không tìm thấy sách");
        assertThat(notifications.count()).isEqualTo(count);
        assertThat(tickets.findById(f.ticket().getTicketId()).orElseThrow().getStatus()).isEqualTo("RETURNED");
        assertThat(cases.findByUserIdOrderByCreatedAtDesc(f.reader().getUserId())).isEmpty();
    }

    record Fixture(UserEntity reader, LoanTicketEntity ticket, LoanDetailEntity detail) { }
    Fixture fixture(String ticketState, String itemState) {
        var reader = reader("STUDENT");
        var book = new BookEntity(); book.setTitle("F04 isolated book"); book = books.saveAndFlush(book);
        var copy = new BookCopyEntity(); copy.setBarcode("F04-" + UUID.randomUUID()); copy.setBookId(book.getBookId()); copy.setStatus("BORROWING"); copies.saveAndFlush(copy);
        var ticket = new LoanTicketEntity(); ticket.setUserId(reader.getUserId()); ticket.setTicketType("BORROW"); ticket.setStatus(ticketState); ticket = tickets.saveAndFlush(ticket);
        var detail = new LoanDetailEntity(); detail.setTicketId(ticket.getTicketId()); detail.setBarcode(copy.getBarcode()); detail.setReturnStatus(itemState);
        detail.setDueDate(LocalDateTime.now().withNano(0).plusDays("OVERDUE".equals(ticketState) ? -2 : 3)); detail = details.saveAndFlush(detail);
        var wallet = new WalletEntity(); wallet.setUserId(reader.getUserId()); wallet.setDepositBalance(new BigDecimal("50000")); wallet.setTotalDebt(new BigDecimal("12345")); wallets.saveAndFlush(wallet);
        return new Fixture(reader, ticket, detail);
    }
    UserEntity reader(String role) {
        var user = new UserEntity(); user.setRoleId(role); user.setUserType("STUDENT".equals(role) ? "STUDENT" : "STAFF");
        user.setEmail("f04-" + UUID.randomUUID() + "@example.invalid"); user.setMssv("F04-" + UUID.randomUUID());
        user.setFullName("F04 isolated reader"); user.setPasswordHash("not-used-by-test-jwt"); user.setCardStatus("ACTIVE"); user.setCardExpiryDate(LocalDateTime.now().plusYears(1));
        return users.saveAndFlush(user);
    }
    String path(Fixture f) { return "/loans/" + f.ticket().getTicketId() + "/items/" + f.detail().getDetailId() + "/loss-report"; }
    HttpRequest request(UserEntity user, String path, String method, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(20));
        if (user != null) {
            var principal = new CustomUserDetails(user);
            builder.header("Authorization", "Bearer " + jwt.generateJwtToken(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
        }
        return builder.header("Content-Type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
    }
    HttpResponse<String> send(UserEntity user, String path, String method, Object body) throws Exception {
        return client.send(request(user, path, method, body), HttpResponse.BodyHandlers.ofString());
    }
}
