package com.huit.library.modules.rag.service;

import com.huit.library.modules.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50)
public class RagKnowledgeBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RagKnowledgeBootstrap.class);

    private final RagProperties properties;
    private final RagKnowledgeIndexService indexService;

    public RagKnowledgeBootstrap(RagProperties properties, RagKnowledgeIndexService indexService) {
        this.properties = properties;
        this.indexService = indexService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            indexService.rebuild(false);
        } catch (Exception exception) {
            log.error("RAG index bootstrap failed; the rest of the library application remains available", exception);
        }
    }
}
