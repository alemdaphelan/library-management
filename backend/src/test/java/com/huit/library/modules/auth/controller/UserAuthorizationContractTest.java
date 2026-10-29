package com.huit.library.modules.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class UserAuthorizationContractTest {
    @Test
    void writeOperationsAreAdminOnly() throws Exception {
        assertAdminOnly("createStudent", com.huit.library.modules.auth.dto.StudentUpsertRequestDTO.class);
        assertAdminOnly("updateStudent", String.class, com.huit.library.modules.auth.dto.StudentUpsertRequestDTO.class);
        assertAdminOnly("deleteStudent", String.class);
        assertAdminOnly("syncWithUniversity", java.util.List.class);
    }

    @Test
    void readOperationsAllowAdminAndLibrarian() throws Exception {
        Method method = UserController.class.getMethod("getAllStudents");
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAnyRole('ADMIN', 'LIBRARIAN')");
        Method profile = UserController.class.getMethod("getReaderProfile", String.class);
        assertThat(profile.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAnyRole('ADMIN', 'LIBRARIAN')");
    }

    private void assertAdminOnly(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = UserController.class.getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
    }
}
