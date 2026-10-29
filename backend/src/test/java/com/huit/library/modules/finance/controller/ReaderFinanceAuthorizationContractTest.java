package com.huit.library.modules.finance.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.penalty.controller.PenaltyCaseController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class ReaderFinanceAuthorizationContractTest {

    @Test
    void personalLedgerRequiresAuthentication() throws Exception {
        var method = PaymentController.class.getMethod("getMyLedger", CustomUserDetails.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("isAuthenticated()");
    }

    @Test
    void personalPenaltyCasesRequireAuthentication() throws Exception {
        var method = PenaltyCaseController.class.getMethod("listMyCases", CustomUserDetails.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("isAuthenticated()");
    }
}
