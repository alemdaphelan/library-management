package com.huit.library.modules.circulation.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CirculationDtoValidationTest {
    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void borrowRequestAcceptsOnlyOfficialTicketTypes() {
        BorrowRequestDTO request = new BorrowRequestDTO();
        request.setBookId(1L);
        request.setTicketType("NORMAL");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("ticketType");
    }

    @Test
    void checkoutRequiresTicketButLeavesItemLimitToActivePolicy() {
        CheckoutRequestDTO request = new CheckoutRequestDTO();
        request.setIdempotencyKey("checkout-test-1");
        request.setBarcodes(List.of("BC-1", "BC-2", "BC-3", "BC-4"));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("ticketId");
    }

    @Test
    void returnRejectsBlankBarcode() {
        ReturnRequestDTO request = new ReturnRequestDTO();
        request.setIdempotencyKey("return-test-1");
        request.setBarcodes(List.of(" "));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("barcodes[0].<list element>");
    }

    @Test
    void rejectionRequiresReason() {
        RejectLoanRequestDTO request = new RejectLoanRequestDTO();
        request.setReason(" ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("reason");
    }

    @Test
    void batchReservationLeavesItemLimitToActivePolicy() {
        BatchBorrowRequestDTO request = new BatchBorrowRequestDTO();
        request.setBookIds(List.of(1L, 2L, 3L, 4L));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void manualCheckoutRequiresMssvAndValidTicketType() {
        ManualCheckoutRequestDTO request = new ManualCheckoutRequestDTO();
        request.setIdempotencyKey("manual-1");
        request.setMssv(" ");
        request.setTicketType("NORMAL");
        request.setBarcodes(List.of("BC-1"));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("mssv", "ticketType");
    }
}
