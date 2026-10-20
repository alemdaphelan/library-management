package com.huit.library.modules.circulation.controller;

import com.huit.library.modules.circulation.dto.QrResolveRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class LibrarianQrAuthorizationContractTest {
    @Test
    void resolvingQrIsRestrictedToAdminAndLibrarian() throws Exception {
        Method method = LibrarianQrController.class.getMethod("resolve", QrResolveRequestDTO.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAnyRole('ADMIN', 'LIBRARIAN')");
    }
}
