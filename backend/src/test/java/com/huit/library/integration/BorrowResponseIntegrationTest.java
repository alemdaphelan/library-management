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
import com.huit.library.modules.notification.repository.NotificationRepository;
import com.huit.library.modules.orientation.repository.OrientationClassRepository;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/** Real HTTP and PostgreSQL regressions for the reservation entry point. */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
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
            "library.circulation.default-security-deposit=100000",
            "app.jwtSecret=f02-isolated-test-secret-never-a-real-account-secret"
        })
class BorrowResponseIntegrationTest {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
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
    @Autowired OrientationClassRepository orientationClasses;
    @Autowired NotificationRepository notifications;

    @Test
    void committedReservationRespondsWhileDeliveryIsBlockedAndDoesNotCreateDuplicateTickets()
            throws Exception {
        UserEntity reader = reader("STUDENT");
        reader("LIBRARIAN");
        BookEntity book = book();
        PushDeviceRegistrationEntity device = new PushDeviceRegistrationEntity();
        device.setUserId(reader.getUserId());
        device.setToken("isolated-f02-device");
        device.setPlatform("WEB");
        device.setLastSeenAt(LocalDateTime.now());
        devices.saveAndFlush(device);
        CountDownLatch deliveryStarted = new CountDownLatch(1),
                releaseDelivery = new CountDownLatch(1);
        CountDownLatch pushStarted = new CountDownLatch(1);
        doAnswer(
                        call -> {
                            deliveryStarted.countDown();
                            if (!releaseDelivery.await(90, TimeUnit.SECONDS))
                                throw new IllegalStateException("Test delivery was never released");
                            return null;
                        })
                .when(mail)
                .send(any(SimpleMailMessage.class));
        doAnswer(
                        call -> {
                            pushStarted.countDown();
                            if (!releaseDelivery.await(90, TimeUnit.SECONDS))
                                throw new IllegalStateException("Test push was never released");
                            return "isolated-message";
                        })
                .when(firebase)
                .send(any(Message.class));

        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var request = request(reader, book);
        var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            assertThat(deliveryStarted.await(10, TimeUnit.SECONDS)).isTrue();
            // Proves the request has already committed, even if its HTTP response is hanging.
            assertThat(tickets.findByUserIdOrderByCreatedAtDesc(reader.getUserId()))
                    .singleElement()
                    .satisfies(ticket -> assertThat(ticket.getStatus()).isEqualTo("RESERVED"));
            var received = response.get(3, TimeUnit.SECONDS);
            assertThat(received.statusCode()).isEqualTo(200);
            var body = json.readTree(received.body());
            assertThat(body.path("success").asBoolean()).isTrue();
            assertThat(body.path("bookTitles").get(0).asText()).isEqualTo(book.getTitle());
            assertThat(body.path("barcodes").size()).isEqualTo(1);
            assertThat(releaseDelivery.getCount()).isEqualTo(1); // SMTP is still blocked.
            assertThat(pushStarted.await(3, TimeUnit.SECONDS))
                    .isTrue(); // FCM cannot hold the HTTP response either.
            var duplicate = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(duplicate.statusCode()).isEqualTo(409);
            assertThat(tickets.findByUserIdOrderByCreatedAtDesc(reader.getUserId())).hasSize(1);

            // Opt-in local browser driver; CI still always exercises the actual HTTP/DB contract
            // above.
            String script = System.getProperty("f02.ui-script");
            if (script != null && !script.isBlank()) {
                UserEntity browserReader = reader("STUDENT");
                BookEntity browserBook = book();
                var process =
                        new ProcessBuilder("node", Path.of(script).toAbsolutePath().toString())
                                .redirectErrorStream(true)
                                .start();
                try {
                    try (var stdin = process.getOutputStream()) {
                        stdin.write(
                                json.writeValueAsBytes(
                                        Map.of(
                                                "apiBaseUrl",
                                                "http://127.0.0.1:" + port,
                                                "token",
                                                token(browserReader),
                                                "userId",
                                                browserReader.getUserId(),
                                                "bookId",
                                                browserBook.getBookId(),
                                                "title",
                                                browserBook.getTitle())));
                    }
                    assertThat(process.waitFor(75, TimeUnit.SECONDS))
                            .as("Browser driver finished")
                            .isTrue();
                    String output =
                            new String(
                                    process.getInputStream().readAllBytes(),
                                    java.nio.charset.StandardCharsets.UTF_8);
                    assertThat(process.exitValue()).withFailMessage(output).isZero();
                    System.out.println("F02 browser: " + output);
                } finally {
                    if (process.isAlive()) {
                        process.descendants().forEach(child -> child.destroyForcibly());
                        process.destroyForcibly();
                    }
                }
                assertThat(tickets.findByUserIdOrderByCreatedAtDesc(browserReader.getUserId()))
                        .hasSize(1);
            }
        } finally {
            releaseDelivery.countDown();
            response.get(15, TimeUnit.SECONDS);
        }
    }

    @Test
    void firstCardIsIssuedByAttendanceAndThenAllowsReservation() throws Exception {
        var client = HttpClient.newHttpClient();
        var admin = reader("ADMIN");
        var librarian = reader("LIBRARIAN");
        var book = book();
        String mssv = "HD-" + UUID.randomUUID().toString().substring(0, 12);
        var createStudent =
                call(
                        client,
                        admin,
                        "POST",
                        "/users/students",
                        Map.of(
                                "mssv",
                                mssv,
                                "fullName",
                                "Sinh viên lớp hướng dẫn",
                                "email",
                                mssv + "@example.invalid"));
        assertThat(createStudent.statusCode()).withFailMessage(createStudent.body()).isEqualTo(201);
        var student = users.findByMssv(mssv).orElseThrow();
        assertThat(
                        json.readTree(
                                        call(client, student, "GET", "/api/v1/library-card", null)
                                                .body())
                                .path("issued")
                                .asBoolean())
                .isFalse();
        assertThat(
                        client.send(request(student, book), HttpResponse.BodyHandlers.ofString())
                                .statusCode())
                .isEqualTo(409);

        var start = LocalDateTime.now().plusDays(1);
        var createClass =
                call(
                        client,
                        admin,
                        "POST",
                        "/orientation-classes",
                        Map.of(
                                "title",
                                "Hướng dẫn thư viện",
                                "location",
                                "Phòng A1",
                                "startAt",
                                start,
                                "endAt",
                                start.plusHours(1),
                                "facilitatorId",
                                librarian.getUserId()));
        assertThat(createClass.statusCode()).withFailMessage(createClass.body()).isEqualTo(200);
        long classId = json.readTree(createClass.body()).path("classId").asLong();
        String classPath = "/orientation-classes/" + classId;
        var candidates = call(client, admin, "GET", classPath + "/available-students", null);
        assertThat(candidates.body()).contains(mssv);
        assertThat(
                        call(
                                        client,
                                        admin,
                                        "POST",
                                        classPath + "/assign",
                                        Map.of("userIds", List.of(student.getUserId())))
                                .statusCode())
                .isEqualTo(200);
        var present =
                Map.of(
                        "items",
                        List.of(Map.of("userId", student.getUserId(), "status", "ATTENDED")));
        assertThat(call(client, librarian, "POST", classPath + "/attendance", present).statusCode())
                .isEqualTo(409);

        var session = orientationClasses.findById(classId).orElseThrow();
        session.setStartAt(start.minusDays(2));
        session.setEndAt(start.minusDays(2).plusHours(1));
        orientationClasses.saveAndFlush(session);
        assertThat(call(client, student, "POST", classPath + "/attendance", present).statusCode())
                .isEqualTo(403);
        var attendance = call(client, librarian, "POST", classPath + "/attendance", present);
        assertThat(attendance.statusCode()).withFailMessage(attendance.body()).isEqualTo(200);
        assertThat(
                        json.readTree(attendance.body())
                                .path("enrollments")
                                .get(0)
                                .path("cardStatus")
                                .asText())
                .isEqualTo("ACTIVE");
        assertThat(call(client, librarian, "POST", classPath + "/attendance", present).statusCode())
                .isEqualTo(200);

        var card = json.readTree(call(client, student, "GET", "/api/v1/library-card", null).body());
        assertThat(card.path("issued").asBoolean()).isTrue();
        assertThat(LocalDateTime.parse(card.path("expiryDate").asText()))
                .isEqualTo(LocalDateTime.parse(card.path("issuedAt").asText()).plusYears(1));
        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(student.getUserId()))
                .filteredOn(
                        notification -> "ORIENTATION_CARD_ACTIVATED".equals(notification.getType()))
                .hasSize(1);
        var reservation = client.send(request(student, book), HttpResponse.BodyHandlers.ofString());
        assertThat(reservation.statusCode()).withFailMessage(reservation.body()).isEqualTo(200);
    }

    @Test
    void cardMigrationPreservesLegacyRecordsAndProtectsNewAttendanceReferences() throws Exception {
        var base =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
        base.execute("CREATE SCHEMA orientation_migration_probe");
        String jdbcUrl = pg.getJdbcUrl();
        var source =
                new DriverManagerDataSource(
                        jdbcUrl
                                + (jdbcUrl.contains("?") ? "&" : "?")
                                + "currentSchema=orientation_migration_probe",
                        pg.getUsername(),
                        pg.getPassword());
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE users (user_id uuid PRIMARY KEY, card_status varchar(20))");
        jdbc.execute("CREATE TABLE library_orientation_enrollments (user_id uuid NOT NULL)");
        UUID issued = UUID.randomUUID();
        UUID historicalOrphan = UUID.randomUUID();
        jdbc.update("INSERT INTO users VALUES (?, 'ACTIVE')", issued);
        jdbc.update("INSERT INTO library_orientation_enrollments VALUES (?)", historicalOrphan);
        try (var connection = source.getConnection()) {
            var database =
                    DatabaseFactory.getInstance()
                            .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase =
                    new Liquibase(
                            "db/changelog/changesets/48-orientation-card-issued-at.yaml",
                            new ClassLoaderResourceAccessor(),
                            database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT card_status FROM users WHERE user_id = ?",
                                String.class,
                                issued))
                .isEqualTo("ACTIVE");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT card_issued_at::text FROM users WHERE user_id = ?",
                                String.class,
                                issued))
                .isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM library_orientation_enrollments WHERE user_id = ?",
                                Long.class,
                                historicalOrphan))
                .isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO library_orientation_enrollments VALUES (?)",
                                        UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO library_orientation_enrollments VALUES (?)", issued);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE user_id = ?", issued))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private HttpResponse<String> call(
            HttpClient client, UserEntity actor, String method, String path, Object body)
            throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(25))
                        .header("Authorization", "Bearer " + token(actor));
        var publisher =
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        if (body != null) request.header("Content-Type", "application/json");
        return client.send(
                request.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }

    private UserEntity reader(String role) {
        UserEntity user = new UserEntity();
        user.setRoleId(role);
        user.setUserType(role.equals("STUDENT") ? "STUDENT" : "STAFF");
        user.setEmail("f02-" + UUID.randomUUID() + "@example.invalid");
        user.setFullName("F02 isolated test");
        user.setMssv("F02-" + UUID.randomUUID());
        user.setPasswordHash("not-used-by-test-jwt");
        user.setCardStatus("ACTIVE");
        user.setCardExpiryDate(LocalDateTime.now().plusYears(1));
        user.setIsFirstLogin(false);
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
        BookEntity book = new BookEntity();
        book.setTitle("F02 test " + UUID.randomUUID());
        book.setAuthor("Test");
        book.setIsDigital(false);
        book.setDefaultPrice(new java.math.BigDecimal("10000"));
        book.setPriceStatus("KNOWN");
        book = books.saveAndFlush(book);
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode("F02-" + UUID.randomUUID());
        copy.setBookId(book.getBookId());
        copy.setStatus("AVAILABLE");
        copies.saveAndFlush(copy);
        return book;
    }

    private String token(UserEntity reader) {
        CustomUserDetails details = new CustomUserDetails(reader);
        return jwt.generateJwtToken(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private HttpRequest request(UserEntity reader, BookEntity book) throws Exception {
        return HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/loans/reserve-batch"))
                .timeout(Duration.ofSeconds(25))
                .header("Authorization", "Bearer " + token(reader))
                .header("Content-Type", "application/json")
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                json.writeValueAsString(
                                        Map.of(
                                                "bookIds",
                                                java.util.List.of(book.getBookId()),
                                                "ticketType",
                                                "BORROW"))))
                .build();
    }
}
