package com.huit.library.modules.finance.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.penalty.controller.PenaltyCaseController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class ReaderFinanceAuthorizationContractTest {

    @Test
    void personalLedgerRequiresStudentRole() throws Exception {
        var method = PaymentController.class.getMethod("getMyLedger", CustomUserDetails.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('STUDENT')");
    }

    @Test
    void personalPenaltyCasesRequireStudentRole() throws Exception {
        var method = PenaltyCaseController.class.getMethod("listMyCases", CustomUserDetails.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('STUDENT')");
    }

    @Test
    void personalOnlinePaymentSessionRequiresStudentRole() throws Exception {
        var method = OnlinePaymentController.class.getMethod(
                "get", String.class, CustomUserDetails.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('STUDENT')");
    }
}
