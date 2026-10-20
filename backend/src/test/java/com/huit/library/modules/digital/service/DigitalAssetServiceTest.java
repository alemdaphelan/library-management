package com.huit.library.modules.digital.service;

import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.digital.dto.DigitalAssetResponseDTO;
import com.huit.library.modules.digital.entity.DigitalDocumentEntity;
import com.huit.library.modules.digital.repository.DigitalDocumentRepository;
import com.huit.library.modules.digital.repository.DigitalUsageLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DigitalAssetServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void uploadStoresFileAndMetadataWithGeneratedStorageKey() throws Exception {
        DigitalDocumentRepository documents = mock(DigitalDocumentRepository.class);
        DigitalUsageLogRepository usageLogs = mock(DigitalUsageLogRepository.class);
        BookRepository books = mock(BookRepository.class);
        when(books.existsById(5L)).thenReturn(true);
        when(documents.save(any())).thenAnswer(invocation -> {
            DigitalDocumentEntity entity = invocation.getArgument(0);
            entity.setDocumentId(11L);
            entity.setCreatedAt(LocalDateTime.now());
            return entity;
        });
        DigitalAssetService service = new DigitalAssetService(
                documents, usageLogs, books, tempDirectory.toString(), 1024);
        service.initializeStorage();
        MockMultipartFile file = new MockMultipartFile(
                "file", "lecture.pdf", "application/pdf", "%PDF-test".getBytes());

        DigitalAssetResponseDTO response = service.upload(
                file, 5L, "AUTHENTICATED", UUID.randomUUID());

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.name()).isEqualTo("lecture.pdf");
        assertThat(response.type()).isEqualTo("PDF");
        assertThat(Files.list(tempDirectory)).hasSize(1);
        verify(documents, times(2)).save(any(DigitalDocumentEntity.class));
    }

    @Test
    void uploadRejectsExtensionWhoseContentDoesNotMatch() {
        DigitalAssetService service = new DigitalAssetService(
                mock(DigitalDocumentRepository.class), mock(DigitalUsageLogRepository.class),
                mock(BookRepository.class), tempDirectory.toString(), 1024);
        service.initializeStorage();
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "not-a-pdf".getBytes());

        assertThatThrownBy(() -> service.upload(file, null, "AUTHENTICATED", UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Nội dung tệp không khớp");
    }

    @Test
    void listHidesStaffOnlyDocumentsFromStudents() {
        DigitalDocumentRepository documents = mock(DigitalDocumentRepository.class);
        DigitalDocumentEntity publicAsset = asset(1L, "AUTHENTICATED");
        DigitalDocumentEntity staffAsset = asset(2L, "STAFF_ONLY");
        when(documents.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(publicAsset, staffAsset));
        DigitalAssetService service = new DigitalAssetService(
                documents, mock(DigitalUsageLogRepository.class), mock(BookRepository.class),
                tempDirectory.toString(), 1024);

        assertThat(service.list(false)).extracting(DigitalAssetResponseDTO::id).containsExactly(1L);
        assertThat(service.list(true)).hasSize(2);
    }

    @Test
    void downloadCreatesUsageLog() throws Exception {
        DigitalDocumentRepository documents = mock(DigitalDocumentRepository.class);
        DigitalUsageLogRepository logs = mock(DigitalUsageLogRepository.class);
        DigitalDocumentEntity document = asset(3L, "AUTHENTICATED");
        document.setStorageKey("stored.pdf");
        document.setContentType("application/pdf");
        document.setFileSize(8L);
        Files.write(tempDirectory.resolve("stored.pdf"), "%PDF-ok".getBytes());
        when(documents.findById(3L)).thenReturn(Optional.of(document));
        DigitalAssetService service = new DigitalAssetService(
                documents, logs, mock(BookRepository.class), tempDirectory.toString(), 1024);

        DigitalAssetService.DigitalDownload download = service.download(3L, UUID.randomUUID(), false);

        assertThat(download.resource().exists()).isTrue();
        verify(logs).save(any());
    }

    private DigitalDocumentEntity asset(Long id, String accessLevel) {
        DigitalDocumentEntity entity = new DigitalDocumentEntity();
        entity.setDocumentId(id);
        entity.setDisplayName("Document " + id);
        entity.setFormat("PDF");
        entity.setFileSize(100L);
        entity.setStorageKey("stored-" + id + ".pdf");
        entity.setAccessLevel(accessLevel);
        return entity;
    }
}
