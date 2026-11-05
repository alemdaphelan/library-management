package com.huit.library.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.core.security.JwtUtils;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.notification.entity.PushDeviceRegistrationEntity;
import com.huit.library.modules.notification.repository.PushDeviceRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/** Real HTTP + PostgreSQL regression: a committed hold must not wait for SMTP. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.data.mongodb.repositories.enabled=false",
        "spring.data.elasticsearch.repositories.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "management.health.mail.enabled=false",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=none",
        "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=drop-and-create",
        "app.database-seeding.enabled=false",
        "library.rag.enabled=false",
        "library.circulation.scheduler-cron=0 0 0 1 1 *",
        "library.circulation.default-open-time=00:00",
        "library.circulation.default-close-time=23:59:59.999999999",
        "app.jwtSecret=f02-isolated-test-secret-never-a-real-account-secret"
})
class BorrowResponseIntegrationTest {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired BookCopyRepository copies;
    @Autowired LoanTicketRepository tickets;
    @Autowired WalletRepository wallets;
    @Autowired ObjectMapper json;
    @Autowired JwtUtils jwt;
    @MockBean JavaMailSender mail;
    @MockBean FirebaseMessaging firebase;
    @Autowired PushDeviceRegistrationRepository devices;

    @Test void committedReservationRespondsWhileDeliveryIsBlockedAndDoesNotCreateDuplicateTickets() throws Exception {
        UserEntity reader = reader("STUDENT");
        reader("LIBRARIAN");
        BookEntity book = book();
        PushDeviceRegistrationEntity device = new PushDeviceRegistrationEntity();
        device.setUserId(reader.getUserId()); device.setToken("isolated-f02-device");
        device.setPlatform("WEB"); device.setLastSeenAt(LocalDateTime.now()); devices.saveAndFlush(device);
        CountDownLatch deliveryStarted = new CountDownLatch(1), releaseDelivery = new CountDownLatch(1);
        CountDownLatch pushStarted = new CountDownLatch(1);
        doAnswer(call -> {
            deliveryStarted.countDown();
            if (!releaseDelivery.await(90, TimeUnit.SECONDS)) throw new IllegalStateException("Test delivery was never released");
            return null;
        }).when(mail).send(any(SimpleMailMessage.class));
        doAnswer(call -> {
            pushStarted.countDown();
            if (!releaseDelivery.await(90, TimeUnit.SECONDS)) throw new IllegalStateException("Test push was never released");
            return "isolated-message";
        }).when(firebase).send(any(Message.class));

        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var request = request(reader, book);
        var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            assertThat(deliveryStarted.await(10, TimeUnit.SECONDS)).isTrue();
            // Proves the request has already committed, even if its HTTP response is hanging.
            assertThat(tickets.findByUserIdOrderByCreatedAtDesc(reader.getUserId())).singleElement()
                    .satisfies(ticket -> assertThat(ticket.getStatus()).isEqualTo("RESERVED"));
            var received = response.get(3, TimeUnit.SECONDS);
            assertThat(received.statusCode()).isEqualTo(200);
            var body = json.readTree(received.body());
            assertThat(body.path("success").asBoolean()).isTrue();
            assertThat(body.path("bookTitles").get(0).asText()).isEqualTo(book.getTitle());
            assertThat(body.path("barcodes").size()).isEqualTo(1);
            assertThat(releaseDelivery.getCount()).isEqualTo(1); // SMTP is still blocked.
            assertThat(pushStarted.await(3, TimeUnit.SECONDS)).isTrue(); // FCM cannot hold the HTTP response either.
            var duplicate = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(duplicate.statusCode()).isEqualTo(409);
            assertThat(tickets.findByUserIdOrderByCreatedAtDesc(reader.getUserId())).hasSize(1);

            // Opt-in local browser driver; CI still always exercises the actual HTTP/DB contract above.
            String script = System.getProperty("f02.ui-script");
            if (script != null && !script.isBlank()) {
                UserEntity browserReader = reader("STUDENT");
                BookEntity browserBook = book();
                var process = new ProcessBuilder("node", Path.of(script).toAbsolutePath().toString())
                        .redirectErrorStream(true).start();
                try {
                    try (var stdin = process.getOutputStream()) {
                        stdin.write(json.writeValueAsBytes(Map.of("apiBaseUrl", "http://127.0.0.1:" + port,
                                "token", token(browserReader), "userId", browserReader.getUserId(),
                                "bookId", browserBook.getBookId(), "title", browserBook.getTitle())));
                    }
                    assertThat(process.waitFor(75, TimeUnit.SECONDS)).as("Browser driver finished").isTrue();
                    String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    assertThat(process.exitValue()).withFailMessage(output).isZero();
                    System.out.println("F02 browser: " + output);
                } finally {
                    if (process.isAlive()) {
                        process.descendants().forEach(child -> child.destroyForcibly()); process.destroyForcibly();
                    }
                }
                assertThat(tickets.findByUserIdOrderByCreatedAtDesc(browserReader.getUserId())).hasSize(1);
            }
        } finally {
            releaseDelivery.countDown();
            response.get(15, TimeUnit.SECONDS);
        }
    }

    private UserEntity reader(String role) {
        UserEntity user = new UserEntity();
        user.setRoleId(role); user.setUserType(role.equals("STUDENT") ? "STUDENT" : "STAFF");
        user.setEmail("f02-" + UUID.randomUUID() + "@example.invalid"); user.setFullName("F02 isolated test");
        user.setMssv("F02-" + UUID.randomUUID()); user.setPasswordHash("not-used-by-test-jwt");
        user.setCardStatus("ACTIVE"); user.setCardExpiryDate(LocalDateTime.now().plusYears(1)); user.setIsFirstLogin(false);
        user = users.saveAndFlush(user);
        if ("STUDENT".equals(role)) {
            WalletEntity wallet = new WalletEntity();
            wallet.setUserId(user.getUserId());
            wallet.setDepositBalance(new java.math.BigDecimal("100000"));
            wallet.setTotalDebt(java.math.BigDecimal.ZERO);
            wallets.saveAndFlush(wallet);
        }
        return user;
    }
    private BookEntity book() {
        BookEntity book = new BookEntity(); book.setTitle("F02 test " + UUID.randomUUID());
        book.setAuthor("Test"); book.setIsDigital(false);
        book.setDefaultPrice(new java.math.BigDecimal("10000"));
        book.setPriceStatus("KNOWN");
        book = books.saveAndFlush(book);
        BookCopyEntity copy = new BookCopyEntity(); copy.setBarcode("F02-" + UUID.randomUUID());
        copy.setBookId(book.getBookId()); copy.setStatus("AVAILABLE"); copies.saveAndFlush(copy);
        return book;
    }
    private String token(UserEntity reader) {
        CustomUserDetails details = new CustomUserDetails(reader);
        return jwt.generateJwtToken(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
    private HttpRequest request(UserEntity reader, BookEntity book) throws Exception {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/loans/reserve-batch"))
                .timeout(Duration.ofSeconds(25)).header("Authorization", "Bearer " + token(reader))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("bookIds", java.util.List.of(book.getBookId()), "ticketType", "BORROW")))).build();
    }
}
