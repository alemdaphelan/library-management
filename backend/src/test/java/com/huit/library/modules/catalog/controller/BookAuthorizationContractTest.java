package com.huit.library.modules.catalog.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.interactive.dto.BookReviewRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class BookAuthorizationContractTest {

    @Test
    void personalizedReaderFeaturesRequireStudentRole() throws Exception {
        assertStudentOnly(BookController.class.getMethod(
                "getPersonalizedRecommendations", CustomUserDetails.class, int.class));
        assertStudentOnly(BookController.class.getMethod(
                "addReview", Long.class, BookReviewRequestDTO.class, CustomUserDetails.class));
        assertStudentOnly(BookController.class.getMethod(
                "deleteMyReview", Long.class, CustomUserDetails.class));
    }

    private void assertStudentOnly(java.lang.reflect.Method method) {
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('STUDENT')");
    }
}
