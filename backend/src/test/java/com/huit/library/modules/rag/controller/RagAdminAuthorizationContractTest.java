package com.huit.library.modules.rag.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class RagAdminAuthorizationContractTest {
    @Test
    void knowledgeIndexOperationsRequireAdminRole() {
        PreAuthorize authorization = RagAdminController.class.getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasRole('ADMIN')");
    }
}
