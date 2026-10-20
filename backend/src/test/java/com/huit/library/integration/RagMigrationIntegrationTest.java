package com.huit.library.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@JdbcTest(properties = {
        "spring.liquibase.enabled=true",
        "spring.liquibase.change-log=classpath:db/changelog/rag-test-master.yaml"
})
class RagMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg15").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("rag_migration_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsVectorIndexAndRanksNearestKnowledgeChunk() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'", Integer.class)).isEqualTo(1);
        UUID documentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO rag_knowledge_documents
                    (document_id, source_key, title, source_uri, version, content_sha256, active, created_at, updated_at)
                VALUES (?, 'policy', 'Policy', 'classpath:policy.md', '1', ?, TRUE, NOW(), NOW())
                """, documentId, "a".repeat(64));
        jdbcTemplate.update("""
                INSERT INTO rag_knowledge_chunks
                    (chunk_id, document_id, chunk_index, content, embedding, embedding_provider, token_estimate, created_at, updated_at)
                VALUES (?, ?, 0, 'gia hạn một lần', CAST(? AS vector), 'test', 4, NOW(), NOW()),
                       (?, ?, 1, 'đền bù tài liệu', CAST(? AS vector), 'test', 4, NOW(), NOW())
                """, UUID.randomUUID(), documentId, vector(1.0f, 0.0f),
                UUID.randomUUID(), documentId, vector(0.0f, 1.0f));

        String closest = jdbcTemplate.queryForObject("""
                SELECT content FROM rag_knowledge_chunks
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT 1
                """, String.class, vector(0.9f, 0.1f));

        assertThat(closest).isEqualTo("gia hạn một lần");
    }

    private String vector(float first, float second) {
        StringBuilder value = new StringBuilder("[");
        for (int index = 0; index < 768; index++) {
            if (index > 0) {
                value.append(',');
            }
            value.append(index == 0 ? first : index == 1 ? second : 0.0f);
        }
        return value.append(']').toString();
    }
}
