package com.huit.library.integration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.dashboard.dto.CirculationAnalytics.Period;
import com.huit.library.modules.dashboard.dto.CirculationAnalytics.Ranking;
import com.huit.library.modules.dashboard.dto.HoldingsAnalytics.*;
import com.huit.library.modules.dashboard.repository.*;
import com.huit.library.modules.dashboard.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@JdbcTest(properties={"spring.liquibase.enabled=true","spring.liquibase.change-log=classpath:db/changelog/dashboard-analytics-test-master.yaml"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({CirculationAnalyticsRepository.class,CirculationAnalyticsService.class,HoldingsAnalyticsRepository.class,HoldingsAnalyticsService.class,MaterialClassificationService.class,DashboardAnalyticsIntegrationTest.Beans.class})
class DashboardAnalyticsIntegrationTest {
    @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:15-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",pg::getJdbcUrl);r.add("spring.datasource.username",pg::getUsername);r.add("spring.datasource.password",pg::getPassword);}
    @TestConfiguration static class Beans {
        @Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules();}
        @Bean LocalValidatorFactoryBean validator(){return new LocalValidatorFactoryBean();}
    }
    @Autowired JdbcTemplate jdbc;@Autowired CirculationAnalyticsService circulation;@Autowired CirculationAnalyticsRepository circulationRepository;
    @Autowired HoldingsAnalyticsService holdings;@Autowired MaterialClassificationService classifications;
    private UserEntity actor;
    private final LocalDate from=LocalDate.parse("2026-01-01"),until=LocalDate.parse("2026-07-01");
    @BeforeEach void setup(){
        jdbc.execute("DELETE FROM material_classification_history; DELETE FROM material_major_assignments; DELETE FROM material_classifications; DELETE FROM loan_status_history; DELETE FROM loan_details; DELETE FROM loan_tickets; DELETE FROM digital_usage_logs; DELETE FROM digital_documents; DELETE FROM book_copies; DELETE FROM books;");
        jdbc.update("INSERT INTO academic_faculties(faculty_id,faculty_name) VALUES ('B12_A','Khoa A'),('B12_B','Khoa B') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO academic_majors(major_id,faculty_id,major_name,source_reference) VALUES ('B12_A1','B12_A','Ngành A1','Test catalog'),('B12_A2','B12_A','Ngành A2','Test catalog'),('B12_B1','B12_B','Ngành B1','Test catalog') ON CONFLICT DO NOTHING");
        actor=new UserEntity();actor.setUserId(UUID.randomUUID());actor.setFullName("Thủ thư kiểm thử");
    }
    @Test void reservationsAndCancellationsAreNotBorrowEvents(){
        long b=book("Reservation",false);String c=copy(b,"RESERVED");long t=ticket("RESERVED",null);
        detail(t,c,"PENDING",null,null);
        jdbc.update("UPDATE loan_details SET created_at='2026-01-01',due_date='2026-01-11'");
        var r=circulation.report(from,until,Period.MONTH);assertThat(r.totals().checkoutItems()).isZero();assertThat(r.totals().physicalReturnItems()).isZero();
        jdbc.update("UPDATE loan_tickets SET status='CANCELLED'");jdbc.update("UPDATE loan_details SET return_status='CANCELLED'");
        assertThat(circulation.report(from,until,Period.MONTH).totals().checkoutItems()).isZero();
        assertThat(circulationRepository.dailyBorrowCounts(from,until)).isEmpty();
    }
    @Test void countsItemsAndTicketsSeparatelyAndPartialReturnsInTheirOwnMonths(){
        long t=ticket("BORROWING",null);for(int i=0;i<3;i++){long b=book("Book "+i,false);detail(t,copy(b,"AVAILABLE"),i<2?"RETURNED":"BORROWING","2026-01-31T23:59:59",i==0?"2026-01-31T23:59:59":i==1?"2026-02-01T00:00:00":null);}
        var r=circulation.report(from,until,Period.MONTH);assertThat(r.totals().checkoutItems()).isEqualTo(3);assertThat(r.totals().checkoutTickets()).isEqualTo(1);
        assertThat(r.totals().physicalReturnItems()).isEqualTo(2);assertThat(r.totals().physicalReturnTickets()).isEqualTo(1);
        assertThat(r.buckets()).hasSize(6);assertThat(r.buckets().get(0).counts().checkoutItems()).isEqualTo(3);assertThat(r.buckets().get(0).counts().physicalReturnItems()).isEqualTo(1);
        assertThat(r.buckets().get(1).counts().checkoutItems()).isZero();assertThat(r.buckets().get(1).counts().physicalReturnItems()).isEqualTo(1);assertThat(r.buckets().get(2).counts().physicalReturnItems()).isZero();
    }
    @Test void halfOpenQuarterYearAndClippedBucketsRespectBoundaries(){
        long b=book("Boundaries",false),t=ticket("BORROWING",null);String c=copy(b,"AVAILABLE");
        for(String at:List.of("2025-12-31T23:59:59","2026-01-01T00:00:00","2026-03-31T23:59:59","2026-04-01T00:00:00","2026-07-01T00:00:00"))detail(t,c,"BORROWING",at,null);
        var r=circulation.report(from,until,Period.QUARTER);assertThat(r.totals().checkoutItems()).isEqualTo(3);assertThat(r.buckets()).hasSize(2);assertThat(r.buckets().get(0).counts().checkoutItems()).isEqualTo(2);
        assertThat(circulation.report(from,LocalDate.parse("2027-01-01"),Period.YEAR).buckets()).hasSize(1);
        var clipped=circulation.report(LocalDate.parse("2026-01-15"),LocalDate.parse("2026-04-15"),Period.QUARTER);assertThat(clipped.buckets().get(0).start()).isEqualTo(LocalDate.parse("2026-01-15"));assertThat(clipped.buckets().get(1).until()).isEqualTo(LocalDate.parse("2026-04-15"));
        assertThatThrownBy(()->circulation.report(until,from,Period.MONTH)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void ranksTiesLeastPreviouslyBorrowedNeverAndCurrentWithStablePages(){
        long a=book("A",false),b=book("B",false),c=book("C",false),never=book("No copies",false),old=book("Old",false),digital=book("Digital only",true);
        long t=ticket("BORROWING",null);for(long head:List.of(a,b))for(int i=0;i<2;i++)detail(t,copy(head,"BORROWING"),"BORROWING","2026-01-01T10:00:00",null);
        detail(t,copy(c,"AVAILABLE"),"RETURNED","2026-02-01T10:00:00","2026-02-02T10:00:00");detail(t,copy(old,"AVAILABLE"),"RETURNED","2025-01-01T10:00:00","2025-01-02T10:00:00");
        var most=circulation.ranking(from,until,Ranking.MOST_BORROWED,0,1);assertThat(most.totalBooks()).isEqualTo(3);assertThat(most.books().get(0).bookId()).isEqualTo(a);assertThat(most.books().get(0).position()).isEqualTo(1);
        assertThat(circulation.ranking(from,until,Ranking.MOST_BORROWED,1,1).books().get(0).position()).isEqualTo(1);
        assertThat(circulation.ranking(from,until,Ranking.LEAST_BORROWED,0,10).books().get(0).bookId()).isEqualTo(c);
        assertThat(circulation.ranking(from,until,Ranking.NEVER_RECORDED,0,10).books()).extracting(x->x.bookId()).containsExactly(never).doesNotContain(old,digital);
        assertThat(circulation.ranking(from,until,Ranking.NO_RECORDED_IN_PERIOD,0,10).books()).extracting(x->x.bookId()).contains(old,never);
        assertThat(circulation.ranking(from,until,Ranking.CURRENTLY_BORROWED,0,10).books()).extracting(x->x.bookId()).containsExactly(a,b);
    }
    @Test void overdueIsCurrentStudentsOnlyAndExcludesIncidentsReservationsAndStoppedFines(){
        UUID student=person("STUDENT"),staff=person("LIBRARIAN");long b=book("Overdue",false);
        long one=detail(ticket("BORROWING",student),copy(b,"BORROWING"),"BORROWING","2026-01-01T10:00:00",null);
        for(String state:List.of("DAMAGED","LOST","PENDING","SETTLED"))detail(ticket("BORROWING",student),copy(b,"MAINTENANCE"),state,null,null);
        long stopped=detail(ticket("BORROWING",student),copy(b,"OVERDUE"),"OVERDUE",null,null);jdbc.update("UPDATE loan_details SET fine_stopped_at=now() WHERE detail_id=?",stopped);
        detail(ticket("BORROWING",staff),copy(b,"BORROWING"),"BORROWING",null,null);jdbc.update("UPDATE loan_details SET due_date='2026-02-01'");
        var r=circulation.overdue(0,25);assertThat(r.totalItems()).isEqualTo(1);assertThat(r.items().get(0).loanDetailId()).isEqualTo(one);assertThat(r.items().get(0).studentReportApi()).contains("mode=OVERDUE");assertThat(r.items().get(0).ticketUrl()).contains("referenceId=");
        assertThat(circulation.report(LocalDate.parse("2025-01-01"),LocalDate.parse("2025-02-01"),Period.MONTH).totals().checkoutItems()).isZero();assertThat(circulation.overdue(0,25).totalItems()).isEqualTo(1);
    }
    @Test void exposesUnknownHistoricalDatesAndNeverTreatsSettledLostCaseAsPhysicalReturn(){
        long b=book("Legacy",false),t=ticket("BORROWING",null);String c=copy(b,"LOST");
        detail(t,c,"BORROWING",null,null);long settled=detail(t,c,"SETTLED",null,null);jdbc.update("UPDATE loan_details SET return_date='2026-01-20' WHERE detail_id=?",settled);
        long noBook=detail(t,"NO-COPY","BORROWING","2026-01-01T10:00:00",null);
        var r=circulation.report(from,until,Period.MONTH);assertThat(r.totals().checkoutItems()).isEqualTo(1);assertThat(r.totals().physicalReturnItems()).isZero();assertThat(r.coverage().checkoutRecordsWithoutTime()).isEqualTo(2);assertThat(r.coverage().periodCheckoutsWithoutCurrentCatalog()).isEqualTo(1);assertThat(r.coverage().complete()).isFalse();
    }
    @Test void unclassifiedBooksAreNotInferredFromNamesOrRecommendationTaxonomy(){
        book("Giáo trình Công nghệ thông tin",false);var r=holdings.summary(null,null,MaterialType.ALL,State.ALL);
        assertThat(r.totals().materials()).isEqualTo(1);assertThat(r.totals().unmappedMaterials()).isEqualTo(1);assertThat(r.faculties().get(0).facultyId()).isEqualTo("UNCLASSIFIED");
        assertThat(holdings.materials("B12_A",null,MaterialType.ALL,State.ALL,0,25).totalMaterials()).isZero();
    }
    @Test void multiMajorAndHybridResourcesDoNotMultiplyGlobalOrFacultyCounts(){
        long b=book("Hybrid",false);copy(b,"AVAILABLE");copy(b,"BORROWING");copy(b,"LOST");file(b,"file-1");file(b,"file-2");
        file(null,"standalone");long digital=book("Digital",true);file(digital,"file-3");
        assign(Kind.BOOK,b,0,List.of("B12_A1","B12_A2","B12_B1"),"Kế hoạch đào tạo #01");
        var r=holdings.summary(null,null,MaterialType.ALL,State.ALL);assertThat(r.totals().materials()).isEqualTo(3);assertThat(r.totals().catalogTitles()).isEqualTo(2);assertThat(r.totals().digitalTitles()).isEqualTo(3);assertThat(r.totals().digitalFiles()).isEqualTo(4);assertThat(r.totals().totalCopies()).isEqualTo(3);
        assertThat(r.faculties().stream().filter(x->x.facultyId().equals("B12_A")).findFirst().orElseThrow().counts().totalCopies()).isEqualTo(3);
        assertThat(r.majors().stream().filter(x->x.facultyId().equals("B12_A"))).hasSize(2);
        assertThat(holdings.summary("B12_A","B12_A1",MaterialType.ALL,State.ALL).totals().materials()).isEqualTo(1);
        assertThat(holdings.materials(null,null,MaterialType.HYBRID,State.ALL,0,25).materials()).hasSize(1);
        assertThat(holdings.assets(Kind.BOOK,b,0,1).total()).isEqualTo(2);
    }
    @Test void classificationHasSourceHistoryOptimisticVersionAndSafeReplay(){
        long b=book("Classify",false);assertThat(classifications.get(Kind.BOOK,b).current().version()).isZero();
        var one=assign(Kind.BOOK,b,0,List.of("B12_A1"),"Nguồn A");assertThat(one.current().version()).isEqualTo(1);assertThat(one.history()).hasSize(1);
        assertThat(assign(Kind.BOOK,b,0,List.of("B12_A1"),"Nguồn A").history()).hasSize(1);
        assertThatThrownBy(()->assign(Kind.BOOK,b,0,List.of("B12_B1"),"Nguồn B")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        var cleared=assign(Kind.BOOK,b,1,List.of(),"Xóa gán sai");assertThat(cleared.history()).hasSize(2);assertThat(cleared.history().get(1).before().majors()).hasSize(1);assertThat(cleared.current().majors()).isEmpty();
        assertThat(holdings.summary("UNCLASSIFIED",null,MaterialType.ALL,State.ALL).totals().materials()).isEqualTo(1);
        assertThatThrownBy(()->assign(Kind.BOOK,b,2,List.of("NOT-A-MAJOR"),"Nguồn")).isInstanceOf(ResponseStatusException.class);
    }
    @Test void standaloneDigitalMappingStopsCountingSeparatelyAfterItIsLinkedToABook(){
        long b=book("Target",false),d=file(null,"Standalone");assign(Kind.DIGITAL,d,0,List.of("B12_A1"),"Nguồn A");assign(Kind.BOOK,b,0,List.of("B12_B1"),"Nguồn B");
        jdbc.update("UPDATE digital_documents SET book_id=? WHERE document_id=?",b,d);
        assertThat(holdings.summary(null,null,MaterialType.ALL,State.ALL).totals().materials()).isEqualTo(1);
        assertThat(holdings.summary("B12_A",null,MaterialType.ALL,State.ALL).totals().materials()).isZero();
        assertThatThrownBy(()->classifications.get(Kind.DIGITAL,d)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("BOOK/");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM material_classification_history",Long.class)).isEqualTo(2);
    }
    @Test void currentCopyStatesFiltersAndLocationsReconcileIncludingRetiredAndUnknown(){
        long b=book("Stock",false);for(String s:Arrays.asList("AVAILABLE","BORROWING","BORROWED","OVERDUE","RESERVED","LOST","DAMAGED","MAINTENANCE","WITHDRAWN","WEIRD",null))copy(b,s);
        String held=copy(b,"AVAILABLE");jdbc.update("UPDATE book_copies SET locked_until=? WHERE barcode=?",LocalDateTime.now().plusDays(2),held);
        var c=holdings.summary(null,null,MaterialType.ALL,State.ALL).totals();assertThat(c.totalCopies()).isEqualTo(12);assertThat(c.availableCopies()).isEqualTo(1);assertThat(c.borrowedCopies()).isEqualTo(3);assertThat(c.reservedCopies()).isEqualTo(2);assertThat(c.retiredCopies()).isEqualTo(1);assertThat(c.otherCopies()).isEqualTo(2);
        assertThat(holdings.materials(null,null,MaterialType.PHYSICAL,State.RETIRED,0,25).totalMaterials()).isEqualTo(1);
        assertThat(holdings.copies(Kind.BOOK,b,0,5).items()).hasSize(5);assertThat(holdings.copies(Kind.BOOK,b,0,5).total()).isEqualTo(12);
        long digital=book("Missing file",true);assertThat(holdings.materials(null,null,MaterialType.ALL,State.NO_DIGITAL_FILE,0,25).materials()).extracting(x->x.id()).containsExactly(digital);
    }
    @Test void materialPaginationEmptyResultsAndBadFiltersAreExplicit(){
        assertThat(holdings.summary(null,null,MaterialType.ALL,State.ALL).totals().materials()).isZero();
        for(int i=0;i<4;i++)book("Book "+i,false);file(null,"File A");file(null,"File B");
        var first=holdings.materials(null,null,MaterialType.ALL,State.ALL,0,2);var second=holdings.materials(null,null,MaterialType.ALL,State.ALL,1,2);assertThat(first.totalMaterials()).isEqualTo(6);assertThat(second.materials()).doesNotContainAnyElementsOf(first.materials());
        assertThatThrownBy(()->holdings.summary("B12_B","B12_A1",MaterialType.ALL,State.ALL)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->holdings.summary("UNKNOWN",null,MaterialType.ALL,State.ALL)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->holdings.materials(null,null,MaterialType.ALL,State.ALL,-1,25)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void orphanCopiesAreReportedInsteadOfClaimingCatalogCountsCoverAllStock(){
        long b=book("Linked book",false);copy(b,"AVAILABLE");
        jdbc.update("INSERT INTO book_copies(barcode,status) VALUES ('ORPHAN-COPY','AVAILABLE')");
        var r=holdings.summary(null,null,MaterialType.ALL,State.ALL);
        assertThat(r.totals().totalCopies()).isEqualTo(1);
        assertThat(r.coverage().totalPhysicalCopies()).isEqualTo(2);
        assertThat(r.coverage().copiesWithoutCurrentCatalog()).isEqualTo(1);
    }
    @Test void materialSearchIsLiteralPagedAndSharesTotalsFilter(){
        book("C++ giáo trình",false);book("C# khác",false);file(null,"Tài liệu C++ số");
        var result=holdings.materials(null,null,MaterialType.ALL,State.ALL,"C++",0,1);
        assertThat(result.totalMaterials()).isEqualTo(2);assertThat(result.materials()).hasSize(1);
        assertThat(holdings.summary(null,null,MaterialType.ALL,State.ALL,"C++").totals().materials()).isEqualTo(2);
        assertThat(holdings.materials(null,null,MaterialType.ALL,State.ALL,"%",0,25).totalMaterials()).isZero();
        assertThatThrownBy(()->holdings.materials(null,null,MaterialType.ALL,State.ALL,"a".repeat(201),0,25)).isInstanceOf(ResponseStatusException.class);
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentClassificationsCannotOverwriteEachOther()throws Exception{
        long b=book("Concurrent",false);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var a=pool.submit(()->race(b,"B12_A1",start));var c=pool.submit(()->race(b,"B12_B1",start));start.countDown();assertThat(List.of(a.get(20,TimeUnit.SECONDS),c.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","409");assertThat(classifications.get(Kind.BOOK,b).history()).hasSize(1);}finally{pool.shutdownNow();}
    }
    @Test void rangeIndexCanServeSelectiveCheckoutPredicate(){
        jdbc.update("INSERT INTO loan_details(checkout_at,checkout_time_source) SELECT timestamp '2020-01-01'+i*interval '1 hour','TEST' FROM generate_series(1,10000) i");jdbc.execute("ANALYZE loan_details");
        String plan=jdbc.queryForObject("EXPLAIN (FORMAT JSON) SELECT count(*) FROM loan_details WHERE checkout_at>='2020-01-01' AND checkout_at<'2020-01-02'",String.class);assertThat(plan).contains("idx_analytics_checkout_at");
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void failedClassificationHistoryRollsBackAssignmentsAndVersion(){
        long b=book("Atomic",false);assign(Kind.BOOK,b,0,List.of("B12_A1"),"Nguồn ban đầu");
        jdbc.execute("CREATE FUNCTION fail_b12_history() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'forced classification audit failure'; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER fail_b12_history BEFORE INSERT ON material_classification_history FOR EACH ROW EXECUTE FUNCTION fail_b12_history()");
        try{assertThatThrownBy(()->assign(Kind.BOOK,b,1,List.of("B12_B1"),"Nguồn mới")).isInstanceOf(RuntimeException.class);}
        finally{jdbc.execute("DROP TRIGGER fail_b12_history ON material_classification_history");jdbc.execute("DROP FUNCTION fail_b12_history()");}
        var result=classifications.get(Kind.BOOK,b);assertThat(result.current().version()).isEqualTo(1);assertThat(result.current().majors()).extracting(Major::majorId).containsExactly("B12_A1");assertThat(result.history()).hasSize(1);
    }
    private String race(long b,String major,CountDownLatch start)throws Exception{start.await();try{assign(Kind.BOOK,b,0,List.of(major),"Nguồn "+major);return "OK";}catch(ResponseStatusException e){return String.valueOf(e.getStatusCode().value());}}
    private Classification assign(Kind kind,long id,long version,List<String> majors,String source){return classifications.put(kind,id,new ClassificationPut(version,majors,source),actor);}
    private long book(String title,boolean digital){return jdbc.queryForObject("INSERT INTO books(title,is_digital) VALUES (?,?) RETURNING book_id",Long.class,title,digital);}
    private String copy(long b,String state){String code="DASH-"+UUID.randomUUID();jdbc.update("INSERT INTO book_copies(barcode,book_id,status) VALUES (?,?,?)",code,b,state);return code;}
    private long file(Long b,String title){return jdbc.queryForObject("INSERT INTO digital_documents(book_id,display_name,format,access_level) VALUES (?,?,'PDF','STAFF_ONLY') RETURNING document_id",Long.class,b,title);}
    private UUID person(String type){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO users(user_id,mssv,full_name,user_type) VALUES (?,?,?,?)",id,id.toString(),"Người kiểm thử",type);return id;}
    private long ticket(String state,UUID user){return jdbc.queryForObject("INSERT INTO loan_tickets(status,user_id) VALUES (?,?) RETURNING ticket_id",Long.class,state,user);}
    private long detail(long t,String barcode,String state,String checkout,String returned){return jdbc.queryForObject("INSERT INTO loan_details(ticket_id,barcode,return_status,checkout_at,physical_return_at,return_date) VALUES (?,?,?,?,?,?) RETURNING detail_id",Long.class,t,barcode,state,checkout==null?null:LocalDateTime.parse(checkout),returned==null?null:LocalDateTime.parse(returned),returned==null?null:LocalDateTime.parse(returned));}
}
