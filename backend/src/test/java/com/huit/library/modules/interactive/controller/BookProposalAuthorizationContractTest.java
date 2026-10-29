package com.huit.library.modules.interactive.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.interactive.dto.CreateProposalRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class BookProposalAuthorizationContractTest {

    @Test
    void readerProposalOperationsRequireAuthentication() throws Exception {
        var submit = BookProposalController.class.getMethod(
                "submitProposal", CustomUserDetails.class, CreateProposalRequestDTO.class);
        var mine = BookProposalController.class.getMethod(
                "getMyProposals", CustomUserDetails.class);

        assertThat(submit.getAnnotation(PreAuthorize.class).value()).isEqualTo("isAuthenticated()");
        assertThat(mine.getAnnotation(PreAuthorize.class).value()).isEqualTo("isAuthenticated()");
    }
}
