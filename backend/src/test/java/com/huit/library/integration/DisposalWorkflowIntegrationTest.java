package com.huit.library.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.*;
import com.huit.library.modules.catalog.repository.*;
import com.huit.library.modules.inventory.dto.DisposalModels.*;
import com.huit.library.modules.inventory.dto.InventoryScanRequestDTO;
import com.huit.library.modules.inventory.service.*;
import com.huit.library.modules.inventory.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers @AutoConfigureMockMvc
@SpringBootTest(properties = {"spring.data.mongodb.repositories.enabled=false", "spring.data.elasticsearch.repositories.enabled=false",
        "spring.kafka.listener.auto-startup=false", "spring.liquibase.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=none",
        "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=drop-and-create", "app.database-seeding.enabled=false",
        "library.rag.enabled=false", "library.circulation.scheduler-cron=0 0 0 1 1 *", "app.jwtSecret=disposal-integration-secret-key-with-more-than-32-characters"})
class DisposalWorkflowIntegrationTest {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void config(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl); r.add("spring.datasource.username", pg::getUsername); r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    @Autowired DisposalService service;
    @Autowired InventoryAuditService auditService;
    @Autowired DisposalProposalRepository proposals;
    @Autowired DisposalBatchRepository batches;
    @Autowired DisposalHistoryRepository history;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired BookCopyRepository copies;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private UserEntity owner;
    private long auditId;
    @BeforeEach void setup() { owner = staff("LIBRARIAN"); auditId = auditService.createAudit(owner).getAuditId(); }
    @Test void batchDraftEditSubmitCancelPreservesCopiesAndImmutableSnapshots() {
        String a = scanned("AVAILABLE"), b = scanned("DAMAGED"); auditService.complete(auditId);
        Create request = request(a, b); var batch = service.create(request, owner);
        assertThat(batch.proposals()).hasSize(2); var id = batch.proposals().get(0).proposalId();
        var detail = service.get(id, owner); assertThat(detail.saved().source().auditStatus()).isEqualTo("COMPLETED");
        assertThat(detail.history()).hasSize(1); assertThat(detail.canSubmit()).isTrue();
        detail = service.edit(id, new Edit(0L, detail.current().fingerprint(), "Bìa rách nhiều", ReasonCode.WORN, "Cần phục hồi", Action.REPAIR, "https://example.org/evidence"), owner);
        assertThat(detail.item().version()).isEqualTo(1); assertThat(detail.history()).hasSize(2);
        assertThat(detail.history().get(1).before().observedCondition()).isEqualTo("Sách cũ");
        detail = service.transition(id, new Transition(1L), true, owner);
        assertThat(detail.item().status()).isEqualTo("SUBMITTED"); assertThat(detail.canEdit()).isFalse();
        assertThat(service.transition(id, new Transition(1L), true, owner).history()).hasSize(3);
        assertThatThrownBy(() -> service.edit(id, new Edit(2L, service.get(id, owner).current().fingerprint(), "Khác", ReasonCode.WORN, "Khác", Action.REPAIR, null), owner)).isInstanceOf(ResponseStatusException.class);
        detail = service.transition(id, new Transition(2L), false, owner);
        assertThat(detail.history()).extracting(History::eventType).containsExactly("CREATED", "UPDATED", "SUBMITTED", "CANCELLED");
        assertThat(detail.item().status()).isEqualTo("CANCELLED"); assertThat(detail.adminIntegrationStatus()).isEqualTo("WAITING_ADMIN_WORKFLOW");
        assertThat(copies.findById(a).orElseThrow().getStatus()).isEqualTo("AVAILABLE");
        assertThat(copies.findById(b).orElseThrow().getStatus()).isEqualTo("DAMAGED");
    }
    @Test void idempotencyReplaysOriginalIdsAndRejectsChangedPayloadOrActor() {
        String a = scanned("AVAILABLE"); auditService.complete(auditId); Create request = request(a);
        var first = service.create(request, owner); var again = service.create(request, owner);
        assertThat(again.replay()).isTrue(); assertThat(again.proposals()).extracting(Item::proposalId).containsExactly(first.proposals().get(0).proposalId());
        assertThat(history.findByProposalIdOrderByProposalVersion(first.proposals().get(0).proposalId())).hasSize(1);
        var line = request.lines().get(0);
        var changed = new Line(a, line.sourceDetailId(), line.expectedFingerprint(), "Khác", ReasonCode.WORN, "Khác", Action.REPAIR, null);
        assertThatThrownBy(() -> service.create(new Create(request.requestId(), auditId, List.of(changed)), owner)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Mã gửi");
        assertThatThrownBy(() -> service.create(request, staff("ADMIN"))).isInstanceOf(ResponseStatusException.class);
    }
    @Test void duplicateWithinBatchOrExistingActiveProposalAndInvalidSourceRejectWholeRequest() {
        String a = scanned("AVAILABLE"), b = scanned("AVAILABLE"); auditService.complete(auditId);
        Create request = request(a); var l = request.lines().get(0);
        assertThatThrownBy(() -> service.create(new Create(UUID.randomUUID(), auditId, List.of(l,l)), owner)).isInstanceOf(ResponseStatusException.class);
        var batch = service.create(request, owner);
        assertThat(service.candidates(auditId).stream().filter(c -> c.source().barcode().equals(a)).findFirst().orElseThrow().eligible()).isFalse();
        Create mixed = request(a,b);
        assertThatThrownBy(() -> service.create(mixed, owner)).isInstanceOf(ResponseStatusException.class);
        assertThat(batches.existsById(mixed.requestId())).isFalse();
        var bad = new Line(b, l.sourceDetailId(), l.expectedFingerprint(), "Sách cũ", ReasonCode.WORN, "Cũ", Action.REPAIR, null);
        assertThatThrownBy(() -> service.create(new Create(UUID.randomUUID(), auditId, List.of(bad)), owner)).isInstanceOf(ResponseStatusException.class);
        service.transition(batch.proposals().get(0).proposalId(), new Transition(0L), false, owner);
        assertThat(service.create(request(a), owner).proposals()).hasSize(1);
    }
    @Test void auditMustBeCompleteAndUnknownBorrowedHeldLostCopiesAreIneligible() {
        for (String state : List.of("AVAILABLE", "BORROWING", "BORROWED", "OVERDUE", "RESERVED", "LOST")) scanned(state);
        var unknown = new InventoryScanRequestDTO(); unknown.setBarcode("unknown-" + UUID.randomUUID()); auditService.scan(auditId, unknown);
        assertThat(service.candidates(auditId)).allMatch(c -> !c.eligible());
        auditService.complete(auditId);
        assertThat(service.candidates(auditId).stream().filter(Candidate::eligible)).hasSize(1);
        var bad = service.candidates(auditId).stream().filter(c -> !c.eligible()).findFirst().orElseThrow();
        assertThatThrownBy(() -> service.create(new Create(UUID.randomUUID(), auditId, List.of(line(bad))), owner)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void staleCopyOrScanAndActiveLoanAreDetectedAgainBeforeSaveAndSubmit() {
        String a = scanned("AVAILABLE"); auditService.complete(auditId); var stale = request(a);
        jdbc.update("UPDATE book_copies SET condition_note='Đã đổi' WHERE barcode=?", a);
        assertThatThrownBy(() -> service.create(stale, owner)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("đã đổi");
        var p = service.create(request(a), owner).proposals().get(0);
        jdbc.update("UPDATE inventory_details SET scanned_location='Đổi nguồn' WHERE audit_id=? AND barcode=?", String.valueOf(auditId), a);
        assertThatThrownBy(() -> service.transition(p.proposalId(), new Transition(0L), true, owner)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("đã đổi");
        var d = service.get(p.proposalId(), owner);
        d = service.edit(p.proposalId(), new Edit(0L, d.current().fingerprint(), "Đã đối chiếu lại", ReasonCode.WORN, "Cũ", Action.REPAIR, null), owner);
        long ticket = jdbc.queryForObject("INSERT INTO loan_tickets(user_id,status) VALUES (?,'BORROWING') RETURNING ticket_id", Long.class, owner.getUserId());
        jdbc.update("INSERT INTO loan_details(ticket_id,barcode,return_status) VALUES (?,?,'BORROWING')", ticket,a);
        long version = d.item().version();
        assertThatThrownBy(() -> service.transition(p.proposalId(), new Transition(version), true, owner)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("phiếu mượn");
        assertThat(service.get(p.proposalId(),owner).item().status()).isEqualTo("DRAFT");
    }
    @Test void ownerOnlyCanMutateAndStaleVersionsCannotOverwriteNewerWork() {
        String a = scanned("AVAILABLE"); auditService.complete(auditId); var p = service.create(request(a),owner).proposals().get(0);
        var other = staff("LIBRARIAN"); assertThat(service.get(p.proposalId(),other).canEdit()).isFalse();
        assertThatThrownBy(() -> service.transition(p.proposalId(),new Transition(0L),false,other)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(() -> service.transition(p.proposalId(),new Transition(99L),true,owner)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("đã đổi");
        var c = service.get(p.proposalId(),owner).current();
        assertThatThrownBy(() -> service.edit(p.proposalId(),new Edit(0L,c.fingerprint(),"Cũ",ReasonCode.WORN,"Cũ",Action.REPAIR,"javascript:alert(1)"),owner)).isInstanceOf(ResponseStatusException.class);
        assertThat(history.findByProposalIdOrderByProposalVersion(p.proposalId())).hasSize(1);
    }
    @Test void twoRequestsForSameCopyCannotCreateConflictingActiveProposals() throws Exception {
        String a = scanned("AVAILABLE"); auditService.complete(auditId); Create one=request(a), two=request(a);
        var start = new CountDownLatch(1); var pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> jobs = List.of(pool.submit(() -> compete(one,start)), pool.submit(() -> compete(two,start)));
            start.countDown(); var results = new ArrayList<String>(); for(var j:jobs) results.add(j.get(20,TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder("OK","409");
            assertThat(proposals.findByBarcodeInAndStatusIn(List.of(a),List.of("DRAFT","SUBMITTED"))).hasSize(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void simultaneousSameRequestReplaysOnceWithoutDuplicateHistory() throws Exception {
        String a=scanned("AVAILABLE"); auditService.complete(auditId); Create request=request(a); var start=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(2);
        try {
            var one=pool.submit(() -> {start.await();return service.create(request,owner);}); var two=pool.submit(() -> {start.await();return service.create(request,owner);}); start.countDown();
            var b1=one.get(20,TimeUnit.SECONDS); var b2=two.get(20,TimeUnit.SECONDS);
            assertThat(b1.proposals().get(0).proposalId()).isEqualTo(b2.proposals().get(0).proposalId()); assertThat(b1.replay()).isNotEqualTo(b2.replay());
            assertThat(history.findByProposalIdOrderByProposalVersion(b1.proposals().get(0).proposalId())).hasSize(1);
        } finally {pool.shutdownNow();}
    }
    @Test void lastHistoryFailureRollsBackBatchAndAllProposals() {
        String a=scanned("AVAILABLE"), b=scanned("AVAILABLE");auditService.complete(auditId);Create req=request(a,b);
        String last=req.lines().stream().map(Line::barcode).max(String::compareTo).orElseThrow();
        jdbc.execute("CREATE FUNCTION reject_b09_history() RETURNS trigger AS $$ BEGIN IF (SELECT barcode FROM disposal_proposals WHERE proposal_id=NEW.proposal_id)='"+last+"' THEN RAISE EXCEPTION 'forced B09 history failure'; END IF; RETURN NEW; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER b09_history_failure BEFORE INSERT ON disposal_history FOR EACH ROW EXECUTE FUNCTION reject_b09_history()");
        try { assertThatThrownBy(() -> service.create(req,owner)).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("DROP TRIGGER b09_history_failure ON disposal_history"); jdbc.execute("DROP FUNCTION reject_b09_history()"); }
        assertThat(batches.existsById(req.requestId())).isFalse();assertThat(proposals.findByRequestIdOrderByBarcode(req.requestId())).isEmpty();
        assertThat(copies.findById(a).orElseThrow().getStatus()).isEqualTo("AVAILABLE");
    }
    @Test void httpRolesOwnerValidationAndPersistedReadback() throws Exception {
        String a=scanned("AVAILABLE");auditService.complete(auditId);Create req=request(a);String base="/inventory/disposals";
        for(String path:List.of(base,base+"/candidates?auditId="+auditId)) {
            mvc.perform(get(path)).andExpect(status().isForbidden());mvc.perform(get(path).with(user(new CustomUserDetails(staff("STUDENT"))))).andExpect(status().isForbidden());
        }
        var auth=user(new CustomUserDetails(owner));
        String response=mvc.perform(post(base).with(auth).contentType("application/json").content(json.writeValueAsString(req))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
        String id=json.readTree(response).get("proposals").get(0).get("proposalId").asText();
        mvc.perform(get(base+"/"+id).with(auth)).andExpect(status().isOk()).andExpect(jsonPath("$.saved.source.barcode").value(a)).andExpect(jsonPath("$.history[0].eventType").value("CREATED"));
        mvc.perform(post(base+"/"+id+"/submit").with(auth).contentType("application/json").content("{\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.item.status").value("SUBMITTED"));
        mvc.perform(post(base+"/"+id+"/cancel").with(user(new CustomUserDetails(staff("ADMIN")))).contentType("application/json").content("{\"version\":1}")).andExpect(status().isForbidden());
        mvc.perform(get(base).with(auth).param("auditId",String.valueOf(auditId)).param("size","1")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get(base).with(auth).param("status","APPROVED")).andExpect(status().isBadRequest());
        mvc.perform(post(base).with(auth).contentType("application/json").content("{\"requestId\":\""+UUID.randomUUID()+"\",\"auditId\":"+auditId+",\"lines\":[]}")).andExpect(status().isBadRequest());
    }
    private String compete(Create req, CountDownLatch start) throws Exception {start.await();try {service.create(req,owner);return "OK";}catch(ResponseStatusException e){return e.getStatusCode().toString().startsWith("409")?"409":e.getStatusCode().toString();}}
    private Create request(String... barcodes) {var selected=Set.of(barcodes);return new Create(UUID.randomUUID(),auditId,service.candidates(auditId).stream().filter(c->selected.contains(c.source().barcode())).map(this::line).toList());}
    private Line line(Candidate c) {return new Line(c.source().barcode(),c.source().detailId(),c.fingerprint(),"Sách cũ",ReasonCode.WORN,"Cần sửa chữa",Action.REPAIR,null);}
    private String scanned(String status) {
        var book=new BookEntity();book.setTitle("B09 book");book.setIsDigital(false);book=books.saveAndFlush(book);
        var copy=new BookCopyEntity();copy.setBarcode("B09-"+UUID.randomUUID());copy.setBookId(book.getBookId());copy.setStatus(status);copies.saveAndFlush(copy);
        var scan=new InventoryScanRequestDTO();scan.setBarcode(copy.getBarcode());auditService.scan(auditId,scan);return copy.getBarcode();
    }
    private UserEntity staff(String role) {var u=new UserEntity();u.setRoleId(role);u.setFullName("B09 "+role);u.setEmail(UUID.randomUUID()+"@test.local");u.setPasswordHash("unused");return users.saveAndFlush(u);}
}
