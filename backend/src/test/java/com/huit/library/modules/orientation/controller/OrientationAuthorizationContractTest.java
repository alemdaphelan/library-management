package com.huit.library.modules.orientation.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class OrientationAuthorizationContractTest {
    @Test
    void controllerRequiresLibraryStaffByDefault() {
        PreAuthorize authorization = OrientationController.class.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAnyRole('ADMIN', 'LIBRARIAN')");
    }

    @Test
    void administrationOperationsRemainAdminOnly() throws Exception {
        assertAdminOnly("update", Long.class,
                com.huit.library.modules.orientation.dto.OrientationDtos.ClassRequest.class);
        assertAdminOnly("delete", Long.class);
        assertAdminOnly("students", Long.class);
        assertAdminOnly("assign", Long.class,
                com.huit.library.modules.orientation.dto.OrientationDtos.AssignmentRequest.class);
    }

    private void assertAdminOnly(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = OrientationController.class.getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
    }
}
