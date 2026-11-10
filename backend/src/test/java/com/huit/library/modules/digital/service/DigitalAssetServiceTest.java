package com.huit.library.modules.digital.service;

import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.digital.dto.DigitalAssetResponseDTO;
import com.huit.library.modules.digital.entity.DigitalDocumentEntity;
import com.huit.library.modules.digital.repository.DigitalDocumentRepository;
import com.huit.library.modules.digital.repository.DigitalUsageLogRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdf);
        }
        MockMultipartFile file = new MockMultipartFile("file", "lecture.pdf", "application/pdf", pdf.toByteArray());

        DigitalAssetResponseDTO response = service.upload(
                file, 5L, "AUTHENTICATED", UUID.randomUUID());

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.name()).isEqualTo("lecture.pdf");
        assertThat(response.type()).isEqualTo("PDF");
        assertThat(Files.list(tempDirectory)).hasSize(1);
        verify(documents, times(2)).save(any(DigitalDocumentEntity.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "epub", "pptx"})
    void uploadRejectsFakeDocumentsWithoutKeepingFileOrMetadata(String extension) throws Exception {
        DigitalDocumentRepository documents = mock(DigitalDocumentRepository.class);
        DigitalAssetService service = new DigitalAssetService(
                documents, mock(DigitalUsageLogRepository.class),
                mock(BookRepository.class), tempDirectory.toString(), 1024);
        service.initializeStorage();
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(archive)) {
            zip.putNextEntry(new ZipEntry("notes.txt"));
            zip.write("Not a document".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        byte[] bytes = extension.equals("pdf")
                ? "%PDF-1.7\nTruncated file with no document structure".getBytes(StandardCharsets.US_ASCII)
                : archive.toByteArray();
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake." + extension, "application/octet-stream", bytes);

        assertThatThrownBy(() -> service.upload(file, null, "AUTHENTICATED", UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Nội dung tệp không khớp")
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        assertThat(tempDirectory).isEmptyDirectory();
        verifyNoInteractions(documents);
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
    void listByBookReturnsOnlyLinkedReadableDocuments() {
        DigitalDocumentRepository documents = mock(DigitalDocumentRepository.class);
        DigitalDocumentEntity publicAsset = asset(1L, "AUTHENTICATED");
        publicAsset.setBookId(7L);
        DigitalDocumentEntity staffAsset = asset(2L, "STAFF_ONLY");
        staffAsset.setBookId(7L);
        when(documents.findByBookIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(publicAsset, staffAsset));
        DigitalAssetService service = new DigitalAssetService(
                documents, mock(DigitalUsageLogRepository.class), mock(BookRepository.class),
                tempDirectory.toString(), 1024);

        assertThat(service.listByBook(7L, false))
                .extracting(DigitalAssetResponseDTO::id)
                .containsExactly(1L);
        assertThat(service.listByBook(7L, true)).hasSize(2);
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
