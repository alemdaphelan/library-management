package com.huit.library.integration;

import com.fasterxml.jackson.databind.*;
import com.huit.library.modules.catalog.controller.LocationController;
import com.huit.library.modules.catalog.controller.BookCopyController;
import com.huit.library.modules.catalog.repository.*;
import com.huit.library.modules.catalog.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;

/** Actual HTTP and Hibernate/PostgreSQL queries; optional live mode is enforced read-only. */
@SpringBootTest(classes=ShelfLocationIntegrationTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"spring.liquibase.enabled=false","spring.jpa.open-in-view=false","server.address=127.0.0.1",
                "spring.data.mongodb.repositories.enabled=false","spring.data.elasticsearch.repositories.enabled=false",
                "spring.data.redis.repositories.enabled=false","management.health.mail.enabled=false",
                "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=none",
                "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=none"})
@ActiveProfiles("f09-shelf-read-probe")
public class ShelfLocationIntegrationTest {
    static final boolean LIVE=Boolean.getBoolean("f09.live");
    static PostgreSQLContainer<?> pg;
    // Explicit root avoids loading the full app; profile excludes it from other tests' component scans.
    @Configuration(proxyBeanMethods=false)
    @Profile("f09-shelf-read-probe")
    @EnableAutoConfiguration(excludeName={"org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
            "org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration",
            "org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration",
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"})
    @EntityScan("com.huit.library.modules")
    @EnableJpaRepositories(basePackages={"com.huit.library.modules.catalog.repository","com.huit.library.modules.recommendation.repository"})
    @Import({LocationService.class,ShelfSubjectRuleService.class,LocationController.class,
            BookCopyService.class,ShelfPlacementService.class,BookCopyController.class})
    @EnableMethodSecurity
    public static class Config {
        @Bean UserDetailsService readers(){return new InMemoryUserDetailsManager(
                User.withUsername("librarian").password("{noop}f09-test-only").roles("LIBRARIAN").build(),
                User.withUsername("admin").password("{noop}f09-test-only").roles("ADMIN").build(),
                User.withUsername("student").password("{noop}f09-test-only").roles("STUDENT").build());}
        @Bean SecurityFilterChain security(HttpSecurity http)throws Exception {
            return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(a->a.anyRequest().authenticated())
                    .httpBasic(b->{}).build();
        }
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        if(LIVE){
            for(String key:List.of("NAME","USER","PASSWORD"))if(System.getenv("TASK_DB_"+key)==null)throw new IllegalStateException("Missing live probe database configuration");
            r.add("spring.datasource.url",()->"jdbc:postgresql://127.0.0.1:5432/"+System.getenv("TASK_DB_NAME"));
            r.add("spring.datasource.username",()->System.getenv("TASK_DB_USER"));r.add("spring.datasource.password",()->System.getenv("TASK_DB_PASSWORD"));
            r.add("spring.datasource.hikari.connection-init-sql",()->"SET default_transaction_read_only = on");
            r.add("spring.jpa.hibernate.ddl-auto",()->"none");
        }else{
            pg=new PostgreSQLContainer<>("postgres:15-alpine");pg.start();
            r.add("spring.datasource.url",pg::getJdbcUrl);r.add("spring.datasource.username",pg::getUsername);r.add("spring.datasource.password",pg::getPassword);
            r.add("spring.jpa.hibernate.ddl-auto",()->"create-drop");
            r.add("spring.jpa.properties.jakarta.persistence.schema-generation.database.action",()->"drop-and-create");
        }
    }
    @AfterAll static void closeDatabase(){if(pg!=null)pg.stop();}
    @BeforeAll static void installAuthorGroups(@Autowired DataSource dataSource) {
        if (!LIVE) {
            new ResourceDatabasePopulator(false, false, "UTF-8", new ClassPathResource(
                    "db/changelog/changesets/47-shelf-author-groups.sql")).execute(dataSource);
        }
    }
    @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
    @LocalServerPort int port;
    final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    HttpResponse<String> get(String path,String user)throws Exception {
        return request(path, user, "GET", "");
    }

    HttpResponse<String> request(String path, String user, String method, String body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(30));
        if(user!=null)request.header("Authorization","Basic "+Base64.getEncoder().encodeToString((user+":f09-test-only").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return client.send(request.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    JsonNode ok(String path)throws Exception{var response=get(path,"librarian");assertThat(response.statusCode()).as(path+" "+response.body()).isEqualTo(200);return json.readTree(response.body());}

    long shelf(){
        Assumptions.assumeFalse(LIVE,"Fixtures are never written to the live database");
        long floor=jdbc.queryForObject("INSERT INTO floors(name) VALUES ('F09') RETURNING floor_id",Long.class);
        long row=jdbc.queryForObject("INSERT INTO bookshelves(name,floor_id) VALUES ('F09',?) RETURNING bookshelf_id",Long.class,floor);
        return jdbc.queryForObject("INSERT INTO shelves(name,bookshelf_id) VALUES ('F09',?) RETURNING shelf_id",Long.class,row);
    }
    @Test void pagesPreserveOrphanAndNullBookIdsAndStableOrderingWithoutInventingLinks()throws Exception {
        long shelf=shelf();
        long book=jdbc.queryForObject("INSERT INTO books(title,isbn,view_count,material_type,price_status) VALUES ('Giáo trình F09','F09-ISBN',0,'BOOK','UNKNOWN') RETURNING book_id",Long.class);
        String prefix="F09-"+UUID.randomUUID();
        jdbc.update("INSERT INTO book_copies(barcode,book_id,shelf_id,status) VALUES (?,?,?,'AVAILABLE'),(?,999999999,?,'LOST'),(?,NULL,?,'MAINTENANCE')",
                prefix+"-valid",book,shelf,prefix+"-orphan",shelf,prefix+"-null",shelf);
        var barcodes=new HashSet<String>();
        for(int page=0;page<3;page++) {
            var body=ok("/locations/shelves/"+shelf+"/books/page?page="+page+"&size=1");
            assertThat(body.path("total").asLong()).isEqualTo(3);assertThat(body.path("page").asInt()).isEqualTo(page);assertThat(body.path("size").asInt()).isEqualTo(1);
            var item=body.path("items").get(0);assertThat(barcodes.add(item.path("barcode").asText())).isTrue();
            if(item.path("barcode").asText().endsWith("-valid")){assertThat(item.path("bookId").asLong()).isEqualTo(book);assertThat(item.path("catalogMatched").asBoolean()).isTrue();}
            else {assertThat(item.path("catalogMatched").asBoolean()).isFalse();assertThat(item.path("isbn").isNull()).isTrue();
                if(item.path("barcode").asText().endsWith("-null"))assertThat(item.path("bookId").isNull()).isTrue();else assertThat(item.path("bookId").asLong()).isEqualTo(999999999);}
        }
        assertThat(ok("/locations/shelves/"+shelf+"/books").size()).isEqualTo(3);
        assertThat(ok("/locations/shelves/"+shelf+"/books/page?page=9&size=1").path("items").isEmpty()).isTrue();
    }
    @Test void emptyShelvesAndUnplacedCopiesAreDistinctAndReadOnly()throws Exception {
        long empty=shelf();String barcode="F09-free-"+UUID.randomUUID();
        jdbc.update("INSERT INTO book_copies(barcode,book_id,status) VALUES (?,999999999,'AVAILABLE')",barcode);
        var vacant=ok("/locations/shelves/"+empty+"/books/page");assertThat(vacant.path("total").asLong()).isZero();assertThat(vacant.path("items").isEmpty()).isTrue();
        var free=ok("/locations/unplaced-copies");assertThat(free.path("items").toString()).contains(barcode);
        assertThat(jdbc.queryForObject("SELECT shelf_id FROM book_copies WHERE barcode=?",Long.class,barcode)).isNull();
        assertThat(get("/locations/shelves/"+empty+"/books/page","student").statusCode()).isEqualTo(403);
        assertThat(get("/locations/unplaced-copies",null).statusCode()).isEqualTo(401);
    }
    @Test void invalidPaginationAndUnknownShelvesHaveClientErrors()throws Exception {
        long shelf=shelf();
        for(var query:List.of("page=-1","size=0","size=101","page=2147483647&size=100"))
            assertThat(get("/locations/shelves/"+shelf+"/books/page?"+query,"librarian").statusCode()).as(query).isEqualTo(400);
        assertThat(get("/locations/shelves/9223372036854775807/books/page","librarian").statusCode()).isEqualTo(404);
    }

    @Test void unplacedCopiesFollowTheSelectedShelfSubjectRule() throws Exception {
        long target = shelf(), other = shelf();
        long book = jdbc.queryForObject("INSERT INTO books(title,view_count,material_type,price_status) VALUES ('Sách có chủ đề',0,'BOOK','UNKNOWN') RETURNING book_id", Long.class);
        long subject = jdbc.queryForObject("INSERT INTO recommendation_subjects(code,name,active) VALUES ('SHELF-SCOPE','Chủ đề kiểm thử',true) RETURNING subject_id", Long.class);
        jdbc.update("INSERT INTO book_subject_assignments(book_id,subject_id,confidence,classification_source,primary_subject) VALUES (?,?,1,'MANUAL',true)", book, subject);
        jdbc.update("INSERT INTO shelf_subject_rules(shelf_id,subject_id,active) VALUES (?,?,true)", target, subject);
        String barcode = "SCOPED-" + UUID.randomUUID();
        jdbc.update("INSERT INTO book_copies(barcode,book_id,status) VALUES (?,?,'AVAILABLE')", barcode, book);

        assertThat(ok("/locations/unplaced-copies?targetShelf=" + target).path("items").toString()).contains(barcode);
        assertThat(ok("/locations/unplaced-copies?targetShelf=" + other).path("items").toString()).doesNotContain(barcode);
    }

    @Test void onlyAdminConfiguresShelvesAndLibrariansCanPlaceEligibleCopies() throws Exception {
        long target = shelf(), unconfigured = shelf();
        long book = jdbc.queryForObject("INSERT INTO books(title,view_count,material_type,price_status) VALUES ('Chưa có chủ đề',0,'BOOK','UNKNOWN') RETURNING book_id", Long.class);
        long intake = jdbc.queryForObject("INSERT INTO recommendation_subjects(code,name,active) VALUES ('SHELF_UNCLASSIFIED','Chờ phân loại',true) RETURNING subject_id", Long.class);
        String barcode = "INTAKE-" + UUID.randomUUID();
        jdbc.update("INSERT INTO book_copies(barcode,book_id,status) VALUES (?,?,'AVAILABLE')", barcode, book);
        String placement = "/book-copies/" + barcode + "/location?shelfId=";
        String rule = "/locations/shelves/" + target + "/subjects";
        String selected = "{\"subjectIds\":[" + intake + "]}";

        assertThat(request(placement + unconfigured, "librarian", "PUT", "").statusCode()).isEqualTo(409);
        assertThat(ok("/locations/unplaced-copies?targetShelf=" + unconfigured).path("total").asLong()).isZero();
        assertThat(request(rule, "librarian", "PUT", selected).statusCode()).isEqualTo(403);
        assertThat(request("/locations/shelves/" + target, "librarian", "PUT", "{\"name\":\"Ngăn A\"}").statusCode()).isEqualTo(403);
        assertThat(request(rule, "admin", "PUT", "{\"subjectIds\":[]}").statusCode()).isEqualTo(400);
        assertThat(request(rule, "admin", "PUT", selected).statusCode()).isEqualTo(200);
        assertThat(ok("/locations/unplaced-copies?targetShelf=" + target + "&size=1").path("items").toString()).contains(barcode);

        long classified = jdbc.queryForObject("INSERT INTO books(title,view_count,material_type,price_status) VALUES ('Sách đã phân loại',0,'BOOK','UNKNOWN') RETURNING book_id", Long.class);
        long subject = jdbc.queryForObject("INSERT INTO recommendation_subjects(code,name,active) VALUES ('SHELF-KNOWN','Chủ đề đã biết',true) RETURNING subject_id", Long.class);
        jdbc.update("INSERT INTO book_subject_assignments(book_id,subject_id,confidence,classification_source,primary_subject) VALUES (?,?,1,'MANUAL',true)", classified, subject);
        String classifiedBarcode = "KNOWN-" + UUID.randomUUID();
        jdbc.update("INSERT INTO book_copies(barcode,book_id,status) VALUES (?,?,'AVAILABLE')", classifiedBarcode, classified);
        assertThat(request("/book-copies/" + classifiedBarcode + "/location?shelfId=" + target, "librarian", "PUT", "").statusCode()).isEqualTo(409);
        assertThat(ok("/locations/unplaced-copies?targetShelf=" + target).path("total").asLong()).isEqualTo(1);
        assertThat(request(placement + target, "librarian", "PUT", "").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT shelf_id FROM book_copies WHERE barcode=?", Long.class, barcode)).isEqualTo(target);
    }

    @Test void authorGroupsUseCatalogMetadataAndRequireTheRightLetter() throws Exception {
        long dShelf = shelf(), nShelf = shelf();
        jdbc.update("""
                INSERT INTO shelf_subject_rules(shelf_id,subject_id,active)
                SELECT CASE WHEN code='SHELF_AUTHOR_D' THEN ? ELSE ? END,subject_id,true
                FROM recommendation_subjects WHERE code IN ('SHELF_AUTHOR_D','SHELF_AUTHOR_N')
                """, dShelf, nShelf);
        long book = jdbc.queryForObject("""
                INSERT INTO books(title,author,view_count,material_type,price_status)
                VALUES ('Nhan đề', ' Đỗ Tác Giả', 0, 'BOOK', 'UNKNOWN') RETURNING book_id
                """, Long.class);
        String barcode = "AUTHOR-" + UUID.randomUUID();
        jdbc.update("INSERT INTO book_copies(barcode,book_id,status) VALUES (?,?,'AVAILABLE')", barcode, book);
        String placement = "/book-copies/" + barcode + "/location?shelfId=";

        assertThat(jdbc.queryForObject("SELECT shelf_author_group('  ', '“Nhan đề”')", String.class))
                .isEqualTo("SHELF_AUTHOR_N");
        assertThat(jdbc.queryForObject("SELECT shelf_author_group('佐藤', 'Novel')", String.class))
                .isEqualTo("SHELF_AUTHOR_OTHER");
        assertThat(ok("/locations/unplaced-copies?targetShelf=" + dShelf).path("items").toString()).contains(barcode);
        assertThat(ok("/locations/unplaced-copies?targetShelf=" + nShelf).path("total").asLong()).isZero();
        assertThat(request(placement + nShelf, "librarian", "PUT", "").statusCode()).isEqualTo(409);
        assertThat(request(placement + dShelf, "librarian", "PUT", "").statusCode()).isEqualTo(200);
    }

    @Test @EnabledIfSystemProperty(named="f09.live",matches="true")
    void liveReadOnlyProbe()throws Exception {
        assertThat(jdbc.queryForObject("SHOW default_transaction_read_only",String.class)).isEqualTo("on");
        Long shelf=jdbc.queryForObject("SELECT min(c.shelf_id) FROM book_copies c LEFT JOIN books b ON b.book_id=c.book_id WHERE b.book_id IS NULL AND c.shelf_id IS NOT NULL",Long.class);
        if (shelf == null) shelf = jdbc.queryForObject("SELECT min(shelf_id) FROM book_copies", Long.class);
        assertThat(shelf).isNotNull();
        var page=ok("/locations/shelves/"+shelf+"/books/page?page=0&size=50");
        long count=jdbc.queryForObject("SELECT count(*) FROM book_copies WHERE shelf_id=?",Long.class,shelf);
        assertThat(page.path("total").asLong()).isEqualTo(count);assertThat(page.path("items").size()).isEqualTo((int)Math.min(50,count));
        var stored=jdbc.query("SELECT barcode,book_id FROM book_copies WHERE shelf_id=?",rs->{
            Map<String,Long> result=new HashMap<>();while(rs.next())result.put(rs.getString(1),rs.getObject(2,Long.class));return result;
        },shelf);
        for(var item:page.path("items")) {
            String barcode=item.path("barcode").asText();assertThat(stored).as(item.toString()).containsKey(barcode);
            var id=stored.get(barcode);
            if(id==null)assertThat(item.path("bookId").isNull()).isTrue();else assertThat(item.path("bookId").asLong()).isEqualTo(id);
            boolean matched=id!=null&&jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM books WHERE book_id=?)",Boolean.class,id);
            assertThat(item.path("catalogMatched").asBoolean()).isEqualTo(matched);
        }
        var next=ok("/locations/shelves/"+shelf+"/books/page?page=1&size=50");assertThat(next.path("total").asLong()).isEqualTo(count);
        assertThat(ok("/locations/shelves/"+shelf+"/books").size()).isEqualTo(count);
        var unplaced=ok("/locations/unplaced-copies?page=0&size=50");
        long free=jdbc.queryForObject("SELECT count(*) FROM book_copies WHERE shelf_id IS NULL",Long.class);
        assertThat(unplaced.path("total").asLong()).isEqualTo(free);
        var empty=jdbc.queryForObject("SELECT min(s.shelf_id) FROM shelves s WHERE NOT EXISTS(SELECT 1 FROM book_copies c WHERE c.shelf_id=s.shelf_id)",Long.class);
        if(empty!=null)assertThat(ok("/locations/shelves/"+empty+"/books/page").path("items").isEmpty()).isTrue();
        if(System.getProperty("f09.ui-script")!=null) {
            var log=java.nio.file.Path.of(System.getProperty("f09.ui-script")).toAbsolutePath().getParent().resolve("f09-real-ui.log");
            var process=new ProcessBuilder("node",System.getProperty("f09.ui-script"),Integer.toString(port),shelf.toString(),Long.toString(count))
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {assertThat(process.waitFor(60,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).as(java.nio.file.Files.readString(log)).isZero();}
            finally {if(process.isAlive())process.destroyForcibly();}
        }
        System.out.println("F09_LIVE_PROBE shelf="+shelf+" shelfTotal="+count+" unplacedTotal="+free+" emptyShelf="+empty+" readonly=true");
    }
}
