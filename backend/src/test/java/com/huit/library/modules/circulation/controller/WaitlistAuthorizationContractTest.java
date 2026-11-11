package com.huit.library.modules.circulation.controller;

import com.huit.library.core.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class WaitlistAuthorizationContractTest {

    @Test
    void personalWaitlistOperationsRequireStudentRole() throws Exception {
        var join =
                WaitlistController.class.getMethod(
                        "joinWaitlist", Long.class, CustomUserDetails.class);
        var mine = WaitlistController.class.getMethod("getMyWaitlist", CustomUserDetails.class);

        assertThat(join.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('STUDENT')");
        assertThat(mine.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('STUDENT')");
    }
}
