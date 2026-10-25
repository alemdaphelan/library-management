package com.huit.library.modules.digital.service;

import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.digital.dto.DigitalAssetResponseDTO;
import com.huit.library.modules.digital.dto.DigitalAssetUpdateDTO;
import com.huit.library.modules.digital.entity.DigitalDocumentEntity;
import com.huit.library.modules.digital.entity.DigitalUsageLogEntity;
import com.huit.library.modules.digital.repository.DigitalDocumentRepository;
import com.huit.library.modules.digital.repository.DigitalUsageLogRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DigitalAssetService {
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "pdf", "application/pdf",
            "epub", "application/epub+zip",
            "mp3", "audio/mpeg",
            "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );
    private static final Set<String> SAFE_ACCESS_LEVELS = Set.of("AUTHENTICATED", "STAFF_ONLY");

    private final DigitalDocumentRepository documentRepository;
    private final DigitalUsageLogRepository usageLogRepository;
    private final BookRepository bookRepository;
    private final Path storageRoot;
    private final long maxFileSize;

    public DigitalAssetService(
            DigitalDocumentRepository documentRepository,
            DigitalUsageLogRepository usageLogRepository,
            BookRepository bookRepository,
            @Value("${library.digital.storage-path:./data/digital-assets}") String storagePath,
            @Value("${library.digital.max-file-size:52428800}") long maxFileSize) {
        this.documentRepository = documentRepository;
        this.usageLogRepository = usageLogRepository;
        this.bookRepository = bookRepository;
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
    }

    @PostConstruct
    void initializeStorage() {
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể khởi tạo thư mục tài liệu số", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<DigitalAssetResponseDTO> list(boolean staff) {
        return documentRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(document -> canRead(document, staff))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DigitalAssetResponseDTO> listByBook(Long bookId, boolean staff) {
        return documentRepository.findByBookIdOrderByCreatedAtDesc(bookId).stream()
                .filter(document -> canRead(document, staff))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DigitalAssetResponseDTO upload(
            MultipartFile file,
            Long bookId,
            String accessLevel,
            UUID uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn tệp cần tải lên");
        }
        if (file.getSize() > maxFileSize) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Tệp vượt quá giới hạn 50 MB");
        }

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null
                ? "digital-document"
                : file.getOriginalFilename());
        if (originalName.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên tệp không hợp lệ");
        }
        String extension = extensionOf(originalName);
        String expectedContentType = ALLOWED_TYPES.get(extension);
        if (expectedContentType == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Chỉ hỗ trợ PDF, EPUB, MP3 và PPTX");
        }
        validateBook(bookId);
        validateFileSignature(file, extension);

        String safeAccessLevel = normalizeAccess(accessLevel);
        String storageKey = UUID.randomUUID() + "." + extension;
        Path destination = resolveStorageKey(storageKey);
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể lưu tệp tài liệu số", exception);
        }

        DigitalDocumentEntity document = new DigitalDocumentEntity();
        document.setBookId(bookId);
        document.setOriginalFilename(originalName);
        document.setDisplayName(originalName);
        document.setFormat(extension.toUpperCase(Locale.ROOT));
        document.setContentType(expectedContentType);
        document.setFileSize(file.getSize());
        document.setStorageKey(storageKey);
        document.setFileUrl("/digital-assets/pending/download");
        document.setAccessLevel(safeAccessLevel);
        document.setUploadedBy(uploadedBy);
        try {
            DigitalDocumentEntity saved = documentRepository.save(document);
            saved.setFileUrl("/digital-assets/" + saved.getDocumentId() + "/download");
            return toResponse(documentRepository.save(saved));
        } catch (RuntimeException exception) {
            deleteQuietly(destination);
            throw exception;
        }
    }

    @Transactional
    public DigitalAssetResponseDTO update(Long id, DigitalAssetUpdateDTO request) {
        DigitalDocumentEntity document = getDocument(id);
        validateBook(request.bookId());
        document.setBookId(request.bookId());
        document.setDisplayName(request.displayName().trim());
        document.setAccessLevel(normalizeAccess(request.accessLevel()));
        return toResponse(documentRepository.save(document));
    }

    @Transactional
    public DigitalDownload download(Long id, UUID userId, boolean staff) {
        DigitalDocumentEntity document = getDocument(id);
        if ("STAFF_ONLY".equals(normalizeAccess(document.getAccessLevel())) && !staff) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài liệu này chỉ dành cho nhân viên thư viện");
        }
        if (!StringUtils.hasText(document.getStorageKey())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tệp vật lý chưa được lưu trên hệ thống");
        }
        Path file = resolveStorageKey(document.getStorageKey());
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tệp vật lý");
        }
        try {
            DigitalUsageLogEntity log = new DigitalUsageLogEntity();
            log.setDocumentId(document.getDocumentId());
            log.setUserId(userId);
            log.setAccessTime(LocalDateTime.now());
            log.setPagesRead(0);
            usageLogRepository.save(log);
            return new DigitalDownload(
                    new UrlResource(file.toUri()),
                    document.getDisplayName(),
                    document.getContentType(),
                    document.getFileSize());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể đọc tệp", exception);
        }
    }

    @Transactional
    public void delete(Long id) {
        DigitalDocumentEntity document = getDocument(id);
        Path path = StringUtils.hasText(document.getStorageKey())
                ? resolveStorageKey(document.getStorageKey())
                : null;
        documentRepository.delete(document);
        documentRepository.flush();
        if (path != null) {
            deleteQuietly(path);
        }
    }

    private DigitalDocumentEntity getDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy tài liệu số"));
    }

    private DigitalAssetResponseDTO toResponse(DigitalDocumentEntity document) {
        String bookTitle = document.getBookId() == null ? null : bookRepository.findById(document.getBookId())
                .map(book -> book.getTitle())
                .orElse("Sách không còn tồn tại");
        String name = StringUtils.hasText(document.getDisplayName())
                ? document.getDisplayName()
                : StringUtils.hasText(document.getOriginalFilename())
                    ? document.getOriginalFilename()
                    : "Tài liệu số #" + document.getDocumentId();
        return new DigitalAssetResponseDTO(
                document.getDocumentId(), document.getBookId(), bookTitle, name,
                document.getFormat(), document.getContentType(), document.getFileSize(),
                normalizeAccess(document.getAccessLevel()), document.getUploadedBy(),
                document.getCreatedAt(), document.getUpdatedAt());
    }

    private void validateBook(Long bookId) {
        if (bookId != null && !bookRepository.existsById(bookId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy sách để liên kết");
        }
    }

    private String normalizeAccess(String accessLevel) {
        String normalized = StringUtils.hasText(accessLevel)
                ? accessLevel.trim().toUpperCase(Locale.ROOT)
                : "AUTHENTICATED";
        if (!SAFE_ACCESS_LEVELS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mức truy cập không hợp lệ");
        }
        return normalized;
    }

    private boolean canRead(DigitalDocumentEntity document, boolean staff) {
        return StringUtils.hasText(document.getStorageKey())
                && (staff || !"STAFF_ONLY".equals(normalizeAccess(document.getAccessLevel())));
    }

    private Path resolveStorageKey(String storageKey) {
        Path resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đường dẫn lưu trữ không hợp lệ");
        }
        return resolved;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void validateFileSignature(MultipartFile file, String extension) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(4);
            boolean valid = switch (extension) {
                case "pdf" -> header.length == 4 && header[0] == '%' && header[1] == 'P'
                        && header[2] == 'D' && header[3] == 'F';
                case "epub", "pptx" -> header.length >= 2 && header[0] == 'P' && header[1] == 'K';
                case "mp3" -> header.length >= 2 && ((header[0] == 'I' && header[1] == 'D')
                        || ((header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0));
                default -> false;
            };
            if (!valid) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Nội dung tệp không khớp với định dạng " + extension.toUpperCase(Locale.ROOT));
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể kiểm tra tệp", exception);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Metadata remains the source of truth; orphan cleanup can safely run later.
        }
    }

    public record DigitalDownload(Resource resource, String filename, String contentType, Long fileSize) {
    }
}
