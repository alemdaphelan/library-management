package com.huit.library.modules.rag.service;

import com.huit.library.modules.rag.dto.RagIndexStatusDTO;
import com.huit.library.modules.rag.model.RagKnowledgeSource;
import com.huit.library.modules.rag.model.RagRetrievedContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class RagKnowledgeStore {
    private final JdbcTemplate jdbcTemplate;

    public RagKnowledgeStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isCurrent(String sourceKey, String hash, String provider) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM rag_knowledge_documents d
                JOIN rag_knowledge_chunks c ON c.document_id = d.document_id
                WHERE d.source_key = ? AND d.content_sha256 = ? AND d.active = TRUE
                  AND c.embedding_provider = ?
                """, Integer.class, sourceKey, hash, provider);
        return count != null && count > 0;
    }

    @Transactional
    public void replace(RagKnowledgeSource source, String hash, String provider, List<String> chunks, List<float[]> vectors) {
        UUID documentId = jdbcTemplate.query("SELECT document_id FROM rag_knowledge_documents WHERE source_key = ?",
                resultSet -> resultSet.next() ? resultSet.getObject(1, UUID.class) : UUID.randomUUID(), source.sourceKey());
        jdbcTemplate.update("DELETE FROM rag_knowledge_chunks WHERE document_id = ?", documentId);
        int updated = jdbcTemplate.update("""
                UPDATE rag_knowledge_documents
                SET title = ?, source_uri = ?, version = ?, effective_date = ?, content_sha256 = ?, active = TRUE, updated_at = ?
                WHERE document_id = ?
                """, source.title(), source.sourceUri(), source.version(), Date.valueOf(source.effectiveDate()), hash,
                Timestamp.valueOf(LocalDateTime.now()), documentId);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO rag_knowledge_documents
                        (document_id, source_key, title, source_uri, version, effective_date, content_sha256, active, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                    """, documentId, source.sourceKey(), source.title(), source.sourceUri(), source.version(),
                    Date.valueOf(source.effectiveDate()), hash, Timestamp.valueOf(LocalDateTime.now()),
                    Timestamp.valueOf(LocalDateTime.now()));
        }
        for (int index = 0; index < chunks.size(); index++) {
            String chunk = chunks.get(index);
            jdbcTemplate.update("""
                    INSERT INTO rag_knowledge_chunks
                        (chunk_id, document_id, chunk_index, content, embedding, embedding_provider,
                         token_estimate, created_at, updated_at)
                    VALUES (?, ?, ?, ?, CAST(? AS vector), ?, ?, ?, ?)
                    """, UUID.randomUUID(), documentId, index, chunk, toVectorLiteral(vectors.get(index)), provider,
                    Math.max(1, chunk.length() / 4), Timestamp.valueOf(LocalDateTime.now()),
                    Timestamp.valueOf(LocalDateTime.now()));
        }
    }

    @Transactional
    public void clear() {
        jdbcTemplate.update("DELETE FROM rag_knowledge_documents");
    }

    public List<RagRetrievedContext> retrieve(float[] queryVector, String provider, int limit, double minimumScore) {
        String vector = toVectorLiteral(queryVector);
        return jdbcTemplate.query("""
                SELECT d.source_key, d.title, d.source_uri, c.content,
                       1 - (c.embedding <=> CAST(? AS vector)) AS score
                FROM rag_knowledge_chunks c
                JOIN rag_knowledge_documents d ON d.document_id = c.document_id
                WHERE d.active = TRUE AND c.embedding_provider = ?
                  AND 1 - (c.embedding <=> CAST(? AS vector)) >= ?
                ORDER BY c.embedding <=> CAST(? AS vector)
                LIMIT ?
                """, (resultSet, rowNumber) -> new RagRetrievedContext(
                        resultSet.getString("source_key"),
                        resultSet.getString("title"),
                        resultSet.getString("source_uri"),
                        "POLICY",
                        resultSet.getString("content"),
                        resultSet.getDouble("score")),
                vector, provider, vector, minimumScore, vector, limit);
    }

    public RagIndexStatusDTO status(String activeProvider, boolean geminiConfigured) {
        Long documents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rag_knowledge_documents WHERE active = TRUE", Long.class);
        Long chunks = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM rag_knowledge_chunks c
                JOIN rag_knowledge_documents d ON d.document_id = c.document_id
                WHERE d.active = TRUE AND c.embedding_provider = ?
                """, Long.class, activeProvider);
        return new RagIndexStatusDTO(
                documents == null ? 0 : documents,
                chunks == null ? 0 : chunks,
                activeProvider,
                geminiConfigured);
    }

    public void audit(
            UUID requestId,
            String actorHash,
            String queryHash,
            int retrievalCount,
            boolean grounded,
            boolean fallbackUsed,
            String model,
            String status,
            long latencyMs) {
        jdbcTemplate.update("""
                INSERT INTO rag_query_audits
                    (audit_id, request_id, actor_hash, query_hash, retrieval_count, grounded,
                     fallback_used, model, status, latency_ms, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), requestId, actorHash, queryHash, retrievalCount, grounded,
                fallbackUsed, model, status, latencyMs, Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now()));
    }

    private String toVectorLiteral(float[] vector) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(Float.isFinite(vector[index]) ? vector[index] : 0.0f);
        }
        return literal.append(']').toString();
    }
}
