package com.huit.library.integration;

import com.fasterxml.jackson.databind.*;
import com.google.firebase.messaging.FirebaseMessaging;
import com.huit.library.core.security.*;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.*;
import com.huit.library.modules.catalog.repository.*;
import com.huit.library.modules.circulation.entity.*;
import com.huit.library.modules.circulation.repository.*;
import com.huit.library.modules.finance.repository.TransactionRepository;
import com.huit.library.modules.notification.repository.NotificationRepository;
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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.data.mongodb.repositories.enabled=false","spring.data.elasticsearch.repositories.enabled=false",
        "spring.kafka.listener.auto-startup=false","management.health.mail.enabled=false","spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=none",
        "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=drop-and-create",
        "app.database-seeding.enabled=false","library.rag.enabled=false","library.circulation.scheduler-cron=0 0 0 1 1 *",
        "library.circulation.default-open-time=00:00","library.circulation.default-close-time=23:59:59.999999999",
        "app.jwtSecret=f06-isolated-test-secret-never-a-real-account-secret"
})
class LoanRecallIntegrationTest {
    @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static GenericContainer<?> redis=new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",pg::getJdbcUrl);r.add("spring.datasource.username",pg::getUsername);r.add("spring.datasource.password",pg::getPassword);
        r.add("spring.data.redis.host",redis::getHost);r.add("spring.data.redis.port",()->redis.getMappedPort(6379));
    }
    @LocalServerPort int port;
    @Autowired UserRepository users; @Autowired BookRepository books; @Autowired BookCopyRepository copies;
    @Autowired LoanTicketRepository tickets; @Autowired LoanDetailRepository details;
    @Autowired NotificationRepository notifications; @Autowired TransactionRepository transactions;
    @Autowired ObjectMapper json; @Autowired JwtUtils jwt;
    @Autowired com.huit.library.modules.policy.repository.LibraryPolicyRepository policies;
    @MockBean JavaMailSender mail; @MockBean FirebaseMessaging firebase;
    final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test void webAndMobileRecallSharePolicyEvidenceAndReplayWithoutAnotherNotification() throws Exception {
        var previous = policies.findById(1L);
        var policy = new com.huit.library.modules.policy.entity.LibraryPolicyEntity();
        policy.setPolicyId(1L); policy.setMaximumOpenItems(3); policy.setHomeLoanDays(10);
        policy.setRenewalDays(10); policy.setMaximumRenewalCount(1); policy.setWaitlistHoldHours(48);
        policy.setHomeLateFinePerDay(new java.math.BigDecimal("1000"));
        policy.setOnsiteLateFinePerDay(new java.math.BigDecimal("5000"));
        policy.setTechnicalProcessingFee(new java.math.BigDecimal("50000"));
        policy.setCompensationSettlementDays(30);
        policy.setMinimumSecurityDeposit(new java.math.BigDecimal("100000"));
        policy.setBorrowingValueMargin(new java.math.BigDecimal("50000"));
        policy.setRecallReturnDays(3); policy.setOverdueDebtProcessingDays(30);
        policies.saveAndFlush(policy);
        try {
            var staff = user("LIBRARIAN");
            long money = transactions.count();
            for (boolean webFirst : List.of(true, false)) {
                var f = fixture("MergedRecall-" + UUID.randomUUID(), 14);
                var oldDue = f.detail().getDueDate();
                String mobile = "/loans/items/" + f.detail().getDetailId() + "/recall";
                assertThat(post(staff, webFirst ? path(f) : mobile,
                        webFirst ? Map.of("reason", "Cần tài liệu cho học phần") : Map.of()).statusCode()).isEqualTo(200);
                var saved = details.findById(f.detail().getDetailId()).orElseThrow();
                assertThat(saved.getRecallRequestedAt()).isEqualTo(saved.getRecalledAt());
                assertThat(saved.getRecallDueDate()).isEqualTo(saved.getRecallDueAt())
                        .isEqualTo(saved.getRecallRequestedAt().plusDays(3));
                assertThat(saved.getRecallPreviousDueDate()).isEqualTo(oldDue);
                assertThat(saved.getOriginalDueDateBeforeRecall()).isEqualTo(oldDue);
                assertThat(saved.getRecallRequestedBy()).isEqualTo(staff.getUserId());
                assertThat(post(staff, webFirst ? mobile : path(f),
                        webFirst ? Map.of() : Map.of("reason", saved.getRecallReason())).statusCode()).isEqualTo(200);
                var replay = details.findById(saved.getDetailId()).orElseThrow();
                assertThat(replay.getRecallRequestedAt()).isEqualTo(saved.getRecallRequestedAt());
                assertThat(replay.getDueDate()).isEqualTo(saved.getDueDate());
                assertThat(notifications.findByUserIdOrderByCreatedAtDesc(f.reader().getUserId())).hasSize(1);
                var listed = list(staff, f.detail().getBarcode(), "RECALLED", 0, 10);
                assertThat(listed.path("items").get(0).path("recallDueDate").asText()).isNotEmpty();
            }
            assertThat(transactions.count()).isEqualTo(money);
        } finally {
            if (previous.isPresent()) policies.saveAndFlush(previous.get()); else policies.deleteById(1L);
        }
    }

    @Test void mobileRecallRejectsAlreadyReceivedOrReportedItemsWithoutChangingEvidence() throws Exception {
        var staff = user("LIBRARIAN");
        for (boolean received : List.of(true, false)) {
            var f = fixture("MergedRecallGuard-" + UUID.randomUUID(), 14);
            if (received) f.detail().setPhysicalReturnAt(now());
            else f.detail().setLossReportedAt(now());
            details.saveAndFlush(f.detail());
            assertThat(post(staff, "/loans/items/" + f.detail().getDetailId() + "/recall", Map.of()).statusCode()).isEqualTo(409);
            var saved = details.findById(f.detail().getDetailId()).orElseThrow();
            assertThat(saved.getIsRecalled()).isFalse();
            assertThat(saved.getRecallRequestedAt()).isNull();
            assertThat(saved.getDueDate()).isEqualTo(f.detail().getDueDate());
            assertThat(notifications.findByUserIdOrderByCreatedAtDesc(f.reader().getUserId())).isEmpty();
        }
    }

    @Test void recallUsesTheEarlierDeadlineForNearFarAndAlreadyOverdueBooksWithoutCharging()throws Exception{
        var staff=user("LIBRARIAN");long money=transactions.count();
        for(int days:List.of(5,14,-2)){
            var f=fixture("DeadlineF06-"+UUID.randomUUID(),days);var old=f.detail().getDueDate();
            var response=post(staff,path(f),Map.of("reason","Cần tài liệu cho học phần"));assertThat(response.statusCode()).isEqualTo(200);
            var body=json.readTree(response.body());var at=LocalDateTime.parse(body.path("recallRequestedAt").asText());
            var expected=old.isBefore(at.plusDays(7))?old:at.plusDays(7);
            var saved=details.findById(f.detail().getDetailId()).orElseThrow();
            assertThat(saved.getDueDate()).isEqualTo(expected);assertThat(saved.getRecallDueDate()).isEqualTo(expected);
            assertThat(saved.getRecallPreviousDueDate()).isEqualTo(old);assertThat(saved.getRecallRequestedBy()).isEqualTo(staff.getUserId());
            assertThat(saved.getIsRecalled()).isTrue();assertThat(saved.getReturnDate()).isNull();assertThat(saved.getFineStoppedAt()).isNull();
            assertThat(copies.findById(saved.getBarcode()).orElseThrow().getStatus()).isEqualTo("BORROWING");
            assertThat(body.path("isRecalled").asBoolean()).isTrue();
            var notices=notifications.findByUserIdOrderByCreatedAtDesc(f.reader().getUserId());
            assertThat(notices).singleElement().satisfies(n->{assertThat(n.getType()).isEqualTo("LOAN_RECALLED");assertThat(n.getContent()).contains("Cần tài liệu");assertThat(n.getReferenceId()).isEqualTo(f.ticket().getTicketId().toString());});
            var history=get(f.reader(),"/loans/my-loans");assertThat(history.statusCode()).isEqualTo(200);assertThat(history.body()).contains("recallPreviousDueDate","Cần tài liệu");
        }
        assertThat(transactions.count()).isEqualTo(money);
    }

    @Test void concurrentRetryPreservesOriginalEvidenceAndOneNotificationEvenAfterReturn()throws Exception{
        var staff=user("ADMIN");var f=fixture("ConcurrentF06",14);
        String reason="Lý do ".repeat(150); // Full reason stays on the item; notification is a bounded preview.
        var pending=new ArrayList<java.util.concurrent.CompletableFuture<HttpResponse<String>>>();
        for(int i=0;i<4;i++)pending.add(client.sendAsync(request(staff,path(f),Map.of("reason",reason)),HttpResponse.BodyHandlers.ofString()));
        JsonNode original=null;
        for(var p:pending){var response=p.get(20,TimeUnit.SECONDS);assertThat(response.statusCode()).isEqualTo(200);var body=json.readTree(response.body());
            if(original==null)original=body;else{assertThat(body.path("recallRequestedAt")).isEqualTo(original.path("recallRequestedAt"));assertThat(body.path("dueDate")).isEqualTo(original.path("dueDate"));}}
        assertThat(details.findById(f.detail().getDetailId()).orElseThrow().getRecallReason()).isEqualTo(reason.strip());
        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(f.reader().getUserId())).singleElement().satisfies(n->assertThat(n.getContent().codePointCount(0,n.getContent().length())).isLessThanOrEqualTo(255));
        assertThat(post(staff,path(f),Map.of("reason","Đổi lý do")).statusCode()).isEqualTo(409);
        var returned=post(staff,"/loans/return",Map.of("barcodes",List.of(f.detail().getBarcode()),"idempotencyKey","f06-"+UUID.randomUUID()));assertThat(returned.statusCode()).isEqualTo(200);
        var replay=post(staff,path(f),Map.of("reason",reason));assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(json.readTree(replay.body()).path("recallRequestedAt")).isEqualTo(original.path("recallRequestedAt"));
        assertThat(json.readTree(replay.body()).path("itemStatus").asText()).isEqualTo("RETURNED");
        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(f.reader().getUserId())).hasSize(1);
    }

    @Test void listSearchFiltersAndPagingOnlyCountCurrentlyHeldItemsIncludingUnattributedCatalogCopies()throws Exception{
        var staff=user("LIBRARIAN");String marker="PageF06-"+UUID.randomUUID();var fixtures=new ArrayList<Fixture>();
        for(int i=0;i<12;i++)fixtures.add(fixture(marker,i+1));
        var returned=fixture(marker,14);returned.detail().setReturnStatus("RETURNED");returned.detail().setReturnDate(now());details.saveAndFlush(returned.detail());
        var received=fixture(marker,14);received.detail().setPhysicalReturnAt(now());details.saveAndFlush(received.detail());
        assertThat(post(staff,path(fixtures.get(0)),Map.of("reason","Cần dùng sớm")).statusCode()).isEqualTo(200);
        var first=list(staff,marker,"ALL",0,5);var last=list(staff,marker,"ALL",2,5);
        assertThat(first.path("total").asLong()).isEqualTo(12);assertThat(first.path("items")).hasSize(5);assertThat(last.path("items")).hasSize(2);
        assertThat(first.path("items").get(0).path("isRecalled").isBoolean()).isTrue();
        assertThat(list(staff,marker,"RECALLED",0,5).path("total").asLong()).isEqualTo(1);
        assertThat(list(staff,marker,"NOT_RECALLED",0,20).path("total").asLong()).isEqualTo(11);
        assertThat(list(staff,marker,"OVERDUE",0,5).path("total").asLong()).isZero();
        var orphan=fixture("Literal %_ "+UUID.randomUUID(),-1);var copy=copies.findById(orphan.detail().getBarcode()).orElseThrow();copy.setBookId(99999999L);copies.saveAndFlush(copy);
        orphan.detail().setCirculationBookId(null);details.saveAndFlush(orphan.detail());
        var found=list(staff,orphan.detail().getBarcode(),"OVERDUE",0,10);assertThat(found.path("total").asLong()).isEqualTo(1);
        assertThat(found.path("items").get(0).path("bookId").isNull()).isTrue();assertThat(found.path("items").get(0).path("barcode").asText()).isEqualTo(copy.getBarcode());
        assertThat(list(staff,"%_","ALL",0,10).path("total").asLong()).isZero(); // literal search, no LIKE wildcard expansion
    }

    @Test void roleValidationLegacyAndStaleStateGuardsDoNotSendNotifications()throws Exception{
        var staff=user("LIBRARIAN");var f=fixture("GuardsF06",14);
        assertThat(post(null,path(f),Map.of("reason","Cần sách")).statusCode()).isIn(401,403);
        assertThat(post(f.reader(),path(f),Map.of("reason","Cần sách")).statusCode()).isEqualTo(403);
        assertThat(get(f.reader(),"/loans/recall-items").statusCode()).isEqualTo(403);
        assertThat(post(staff,path(f),Map.of("reason"," ")).statusCode()).isEqualTo(400);
        assertThat(post(staff,path(f),Map.of("reason","x".repeat(1001))).statusCode()).isEqualTo(400);
        assertThat(get(staff,"/loans/recall-items?size=101").statusCode()).isEqualTo(400);
        var other=fixture("WrongF06",14);assertThat(post(staff,"/loans/"+f.ticket().getTicketId()+"/items/"+other.detail().getDetailId()+"/recall",Map.of("reason","Cần sách")).statusCode()).isEqualTo(404);
        f.detail().setIsRecalled(true);details.saveAndFlush(f.detail());assertThat(post(staff,path(f),Map.of("reason","Cần sách")).statusCode()).isEqualTo(409);
        assertThat(details.findById(f.detail().getDetailId()).orElseThrow().getRecallRequestedAt()).isNull();
        for(String state:List.of("RETURNED","DAMAGED","LOST","SETTLED","PENDING")){
            var bad=fixture("InactiveF06",14);bad.detail().setReturnStatus(state);details.saveAndFlush(bad.detail());assertThat(post(staff,path(bad),Map.of("reason","Cần sách")).statusCode()).as(state).isEqualTo(409);
        }
        var missing=fixture("MissingDueF06",14);missing.detail().setDueDate(null);details.saveAndFlush(missing.detail());assertThat(post(staff,path(missing),Map.of("reason","Cần sách")).statusCode()).isEqualTo(409);
        var loss=fixture("ReportedF06",14);loss.detail().setLossReportedAt(now());loss.detail().setLossReportDescription("Mất");details.saveAndFlush(loss.detail());assertThat(post(staff,path(loss),Map.of("reason","Cần sách")).statusCode()).isEqualTo(409);
        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(f.reader().getUserId())).isEmpty();
    }

    @Test void recallBlocksRenewalOfOnlyTheSelectedItemOnAnOtherwiseEligibleTicket()throws Exception{
        var staff=user("LIBRARIAN");var f=fixture("RenewF06",14);var second=addItem(f.reader(),f.ticket(),"OtherF06",5);
        assertThat(post(staff,path(f),Map.of("reason","Cần sách")).statusCode()).isEqualTo(200);
        assertThat(post(f.reader(),"/loans/renew/"+f.ticket().getTicketId()+"/items/"+f.detail().getDetailId(),Map.of()).statusCode()).isEqualTo(409);
        assertThat(post(f.reader(),"/loans/renew/"+f.ticket().getTicketId()+"/items/"+second.getDetailId(),Map.of()).statusCode()).isEqualTo(200);
        assertThat(details.findById(f.detail().getDetailId()).orElseThrow().getRenewalCount()).isZero();assertThat(details.findById(second.getDetailId()).orElseThrow().getRenewalCount()).isEqualTo(1);
    }

    record Fixture(UserEntity reader,LoanTicketEntity ticket,LoanDetailEntity detail){}
    Fixture fixture(String title,int days){var user=user("STUDENT");var ticket=new LoanTicketEntity();ticket.setUserId(user.getUserId());ticket.setTicketType("BORROW");ticket.setStatus("BORROWING");ticket=tickets.saveAndFlush(ticket);return new Fixture(user,ticket,addItem(user,ticket,title,days));}
    LoanDetailEntity addItem(UserEntity user,LoanTicketEntity ticket,String title,int days){
        var b=new BookEntity();b.setTitle(title);b=books.saveAndFlush(b);var c=new BookCopyEntity();c.setBookId(b.getBookId());c.setBarcode("F06-"+UUID.randomUUID());c.setStatus("BORROWING");copies.saveAndFlush(c);
        var d=new LoanDetailEntity();d.setTicketId(ticket.getTicketId());d.setBarcode(c.getBarcode());d.setCirculationBookId(b.getBookId());d.setReturnStatus("BORROWING");d.setDueDate(now().truncatedTo(ChronoUnit.SECONDS).plusDays(days));return details.saveAndFlush(d);
    }
    UserEntity user(String role){var u=new UserEntity();u.setRoleId(role);u.setUserType("STUDENT".equals(role)?"STUDENT":"STAFF");u.setEmail("f06-"+UUID.randomUUID()+"@example.invalid");u.setMssv("F06-"+UUID.randomUUID());u.setFullName("F06 isolated test");u.setPasswordHash("not-used-by-test-jwt");u.setCardStatus("ACTIVE");u.setCardExpiryDate(now().plusYears(1));return users.saveAndFlush(u);}
    LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));}
    String path(Fixture f){return "/loans/"+f.ticket().getTicketId()+"/items/"+f.detail().getDetailId()+"/recall";}
    JsonNode list(UserEntity staff,String q,String state,int page,int size)throws Exception{var response=get(staff,"/loans/recall-items?q="+URLEncoder.encode(q,StandardCharsets.UTF_8)+"&state="+state+"&page="+page+"&size="+size);assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);return json.readTree(response.body());}
    HttpRequest.Builder builder(UserEntity u,String path){var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20));if(u!=null){var d=new CustomUserDetails(u);b.header("Authorization","Bearer "+jwt.generateJwtToken(new UsernamePasswordAuthenticationToken(d,null,d.getAuthorities())));}return b.header("Content-Type","application/json");}
    HttpRequest request(UserEntity u,String path,Object body)throws Exception{return builder(u,path).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();}
    HttpResponse<String> post(UserEntity u,String path,Object body)throws Exception{return client.send(request(u,path,body),HttpResponse.BodyHandlers.ofString());}
    HttpResponse<String> get(UserEntity u,String path)throws Exception{return client.send(builder(u,path).GET().build(),HttpResponse.BodyHandlers.ofString());}
}
