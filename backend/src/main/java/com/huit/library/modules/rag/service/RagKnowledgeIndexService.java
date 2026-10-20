package com.huit.library.modules.rag.service;

import com.huit.library.modules.rag.config.RagProperties;
import com.huit.library.modules.rag.dto.RagIndexStatusDTO;
import com.huit.library.modules.rag.model.RagKnowledgeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class RagKnowledgeIndexService {
    private static final Logger log = LoggerFactory.getLogger(RagKnowledgeIndexService.class);
    private static final LocalDate POLICY_EFFECTIVE_DATE = LocalDate.of(2026, 10, 18);

    private final RagProperties properties;
    private final RagKnowledgeStore store;
    private final RagEmbeddingGateway embeddingGateway;
    private final RagTextChunker chunker;

    public RagKnowledgeIndexService(
            RagProperties properties,
            RagKnowledgeStore store,
            RagEmbeddingGateway embeddingGateway,
            RagTextChunker chunker) {
        this.properties = properties;
        this.store = store;
        this.embeddingGateway = embeddingGateway;
        this.chunker = chunker;
    }

    public synchronized RagIndexStatusDTO rebuild(boolean force) {
        if (!properties.isEnabled()) {
            return status();
        }
        if (force) {
            store.clear();
        }
        for (RagKnowledgeSource source : loadSources()) {
            String hash = sha256(source.content());
            String provider = embeddingGateway.providerKey();
            if (!force && store.isCurrent(source.sourceKey(), hash, provider)) {
                continue;
            }
            List<String> chunks = chunker.chunk(source.content());
            List<float[]> vectors = new ArrayList<>(chunks.size());
            for (String chunk : chunks) {
                vectors.add(embeddingGateway.embedDocument(chunk, source.title()));
            }
            store.replace(source, hash, provider, chunks, vectors);
            log.info("Indexed RAG source {} with {} chunks using {}", source.sourceKey(), chunks.size(), provider);
        }
        return status();
    }

    public RagIndexStatusDTO status() {
        return store.status(embeddingGateway.providerKey(), embeddingGateway.isRemote());
    }

    private List<RagKnowledgeSource> loadSources() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:rag/*.md");
            List<RagKnowledgeSource> sources = new ArrayList<>();
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                String content = resource.getContentAsString(StandardCharsets.UTF_8).trim();
                String title = content.lines()
                        .filter(line -> line.startsWith("# "))
                        .map(line -> line.substring(2).trim())
                        .findFirst()
                        .orElse(filename);
                String sourceKey = filename == null ? sha256(content).substring(0, 16) : filename.replace(".md", "");
                sources.add(new RagKnowledgeSource(
                        sourceKey,
                        title,
                        "classpath:rag/" + filename,
                        "2026.10",
                        POLICY_EFFECTIVE_DATE,
                        content));
            }
            return sources.stream().sorted(java.util.Comparator.comparing(RagKnowledgeSource::sourceKey)).toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể đọc nguồn tri thức RAG", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể tạo SHA-256", exception);
        }
    }
}
