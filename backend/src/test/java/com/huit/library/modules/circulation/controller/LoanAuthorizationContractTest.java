package com.huit.library.modules.circulation.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.circulation.dto.CheckoutRequestDTO;
import com.huit.library.modules.circulation.dto.ManualCheckoutRequestDTO;
import com.huit.library.modules.circulation.dto.ReturnRequestDTO;
import com.huit.library.modules.circulation.dto.ReturnInspectionRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class LoanAuthorizationContractTest {

    @Test
    void counterMutationsRequireLibraryStaff() throws Exception {
        var checkout = LoanController.class.getMethod(
                "checkoutBooks", CheckoutRequestDTO.class, CustomUserDetails.class);
        var manualCheckout = LoanController.class.getMethod(
                "manualCheckout", ManualCheckoutRequestDTO.class, CustomUserDetails.class);
        var returns = LoanController.class.getMethod(
                "returnBooks", ReturnRequestDTO.class, CustomUserDetails.class);

        assertThat(checkout.getAnnotation(PreAuthorize.class).value()).contains("ADMIN", "LIBRARIAN");
        assertThat(manualCheckout.getAnnotation(PreAuthorize.class).value()).contains("ADMIN", "LIBRARIAN");
        assertThat(returns.getAnnotation(PreAuthorize.class).value()).contains("ADMIN", "LIBRARIAN");
        var inspection = ReturnInspectionController.class.getMethod(
                "inspect", ReturnInspectionRequestDTO.class, CustomUserDetails.class);
        assertThat(inspection.getAnnotation(PreAuthorize.class).value()).contains("ADMIN", "LIBRARIAN");
    }
}
