package com.huit.library.modules.circulation.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.circulation.dto.BatchBorrowRequestDTO;
import com.huit.library.modules.circulation.dto.BorrowRequestDTO;
import com.huit.library.modules.circulation.dto.CheckoutRequestDTO;
import com.huit.library.modules.circulation.dto.ManualCheckoutRequestDTO;
import com.huit.library.modules.circulation.dto.ReturnRequestDTO;
import com.huit.library.modules.circulation.dto.ReturnInspectionRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class LoanAuthorizationContractTest {

    @Test
    void readerLoanOperationsRequireStudentRole() throws Exception {
        assertStudentOnly(
                LoanController.class.getMethod(
                        "reserveBook", BorrowRequestDTO.class, CustomUserDetails.class));
        assertStudentOnly(
                LoanController.class.getMethod(
                        "reserveBooks", BatchBorrowRequestDTO.class, CustomUserDetails.class));
        assertStudentOnly(LoanController.class.getMethod("getMyLoans", CustomUserDetails.class));
        assertStudentOnly(
                LoanController.class.getMethod(
                        "getMyBorrowEligibility", Long.class, CustomUserDetails.class));
        assertStudentOnly(
                LoanController.class.getMethod(
                        "cancelReservation", Long.class, CustomUserDetails.class));
        assertStudentOnly(
                LoanController.class.getMethod("renewLoan", Long.class, CustomUserDetails.class));
        assertStudentOnly(
                LoanController.class.getMethod(
                        "renewLoanItem", Long.class, Long.class, CustomUserDetails.class));
    }

    @Test
    void counterMutationsRequireLibraryStaff() throws Exception {
        var checkout =
                LoanController.class.getMethod(
                        "checkoutBooks", CheckoutRequestDTO.class, CustomUserDetails.class);
        var manualCheckout =
                LoanController.class.getMethod(
                        "manualCheckout", ManualCheckoutRequestDTO.class, CustomUserDetails.class);
        var returns =
                LoanController.class.getMethod(
                        "returnBooks", ReturnRequestDTO.class, CustomUserDetails.class);

        assertThat(checkout.getAnnotation(PreAuthorize.class).value())
                .contains("ADMIN", "LIBRARIAN");
        assertThat(manualCheckout.getAnnotation(PreAuthorize.class).value())
                .contains("ADMIN", "LIBRARIAN");
        assertThat(returns.getAnnotation(PreAuthorize.class).value())
                .contains("ADMIN", "LIBRARIAN");
        var inspection =
                ReturnInspectionController.class.getMethod(
                        "inspect", ReturnInspectionRequestDTO.class, CustomUserDetails.class);
        assertThat(inspection.getAnnotation(PreAuthorize.class).value())
                .contains("ADMIN", "LIBRARIAN");
    }

    private void assertStudentOnly(java.lang.reflect.Method method) {
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('STUDENT')");
    }
}
