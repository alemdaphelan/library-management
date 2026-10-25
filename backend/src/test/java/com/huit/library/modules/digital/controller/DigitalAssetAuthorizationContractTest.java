package com.huit.library.modules.digital.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class DigitalAssetAuthorizationContractTest {
    @Test
    void writeOperationsRequireLibraryStaff() throws Exception {
        Method upload = DigitalAssetController.class.getMethod(
                "upload", MultipartFile.class, Long.class, String.class, Authentication.class);
        Method update = DigitalAssetController.class.getMethod(
                "update", Long.class, com.huit.library.modules.digital.dto.DigitalAssetUpdateDTO.class);
        Method delete = DigitalAssetController.class.getMethod("delete", Long.class);

        assertThat(upload.getAnnotation(PreAuthorize.class).value()).contains("ADMIN", "LIBRARIAN");
        assertThat(update.getAnnotation(PreAuthorize.class).value()).contains("ADMIN", "LIBRARIAN");
        assertThat(delete.getAnnotation(PreAuthorize.class).value()).contains("ADMIN", "LIBRARIAN");
    }

    @Test
    void downloadRequiresAuthentication() throws Exception {
        Method download = DigitalAssetController.class.getMethod("download", Long.class, Authentication.class);
        Method preview = DigitalAssetController.class.getMethod("preview", Long.class, Authentication.class);
        Method listByBook = DigitalAssetController.class.getMethod("listByBook", Long.class, Authentication.class);
        assertThat(download.getAnnotation(PreAuthorize.class).value()).isEqualTo("isAuthenticated()");
        assertThat(preview.getAnnotation(PreAuthorize.class).value()).isEqualTo("isAuthenticated()");
        assertThat(listByBook.getAnnotation(PreAuthorize.class).value()).isEqualTo("isAuthenticated()");
    }
}
