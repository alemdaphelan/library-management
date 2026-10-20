package com.huit.library.modules.auth.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthDtoValidationTest {
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
    void resetPasswordRejectsWeakPasswordAndMalformedOtp() {
        ResetPasswordRequestDTO request = new ResetPasswordRequestDTO();
        request.setEmail("student@huit.edu.vn");
        request.setOtp("12A");
        request.setNewPassword("short");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("otp", "newPassword");
    }

    @Test
    void resetPasswordAcceptsValidPayload() {
        ResetPasswordRequestDTO request = new ResetPasswordRequestDTO();
        request.setEmail("student@huit.edu.vn");
        request.setOtp("123456");
        request.setNewPassword("new-password");

        assertThat(validator.validate(request)).isEmpty();
    }
}
