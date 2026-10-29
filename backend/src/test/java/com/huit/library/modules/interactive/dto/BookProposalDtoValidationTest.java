package com.huit.library.modules.interactive.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BookProposalDtoValidationTest {
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
    void proposalRequiresTitleAndReason() {
        CreateProposalRequestDTO request = new CreateProposalRequestDTO();
        request.setTitle(" ");
        request.setReason(" ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("title", "reason");
    }

    @Test
    void proposalRejectsInvalidYearPriceAndUrgency() {
        CreateProposalRequestDTO request = new CreateProposalRequestDTO();
        request.setTitle("Sách hợp lệ");
        request.setReason("Phục vụ học tập");
        request.setPublicationYear(999);
        request.setEstimatedPrice(new BigDecimal("-1"));
        request.setUrgencyLevel("URGENT");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("publicationYear", "estimatedPrice", "urgencyLevel");
    }

    @Test
    void bookReviewRatingMustBeBetweenOneAndFive() {
        BookReviewRequestDTO request = new BookReviewRequestDTO();
        request.setRating(6);
        request.setComment("x".repeat(1001));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("rating", "comment");
    }
}
