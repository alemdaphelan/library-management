package com.huit.library.modules.circulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.circulation.dto.LoanTicketDetailDTO;
import com.huit.library.modules.circulation.dto.QrPayloadResponseDTO;
import com.huit.library.modules.circulation.dto.QrResolveResponseDTO;
import com.huit.library.modules.circulation.model.LoanTicketStatus;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class LibrarianQrService {
    static final String TOKEN_PREFIX = "HUITQR1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final LoanService loanService;
    private final LoanTicketRepository loanTicketRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final byte[] secret;
    private final long ttlMinutes;
    private final Clock clock;

    @Autowired
    public LibrarianQrService(
            ObjectMapper objectMapper,
            LoanService loanService,
            LoanTicketRepository loanTicketRepository,
            UserRepository userRepository,
            WalletRepository walletRepository,
            @Value("${library.qr.secret}") String secret,
            @Value("${library.qr.ttl-minutes:10}") long ttlMinutes) {
        this(objectMapper, loanService, loanTicketRepository, userRepository, walletRepository,
                secret, ttlMinutes, Clock.systemUTC());
    }

    LibrarianQrService(
            ObjectMapper objectMapper,
            LoanService loanService,
            LoanTicketRepository loanTicketRepository,
            UserRepository userRepository,
            WalletRepository walletRepository,
            String secret,
            long ttlMinutes,
            Clock clock) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("library.qr.secret must contain at least 32 characters");
        }
        if (ttlMinutes < 1 || ttlMinutes > 60) {
            throw new IllegalArgumentException("library.qr.ttl-minutes must be between 1 and 60");
        }
        this.objectMapper = objectMapper;
        this.loanService = loanService;
        this.loanTicketRepository = loanTicketRepository;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlMinutes = ttlMinutes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public QrPayloadResponseDTO issueTicketPayload(Long ticketId, UserEntity requester) {
        var ticket = loanTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy phiếu mượn #" + ticketId));
        boolean staff = hasStaffRole(requester);
        if (!staff && !Objects.equals(ticket.getUserId(), requester.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền tạo QR cho phiếu này");
        }
        return issue("LOAN_TICKET", ticketId.toString(), ticket.getUserId());
    }

    public QrPayloadResponseDTO issueCardPayload(UserEntity requester) {
        return issue("USER_CARD", requester.getUserId().toString(), requester.getUserId());
    }

    @Transactional(readOnly = true)
    public QrResolveResponseDTO resolve(String payload) {
        QrClaims claims = verify(payload);
        return switch (claims.type()) {
            case "LOAN_TICKET" -> resolveTicket(claims);
            case "USER_CARD" -> resolveCard(claims);
            default -> throw invalidPayload("Loại QR không được hỗ trợ");
        };
    }

    private QrResolveResponseDTO resolveTicket(QrClaims claims) {
        long ticketId;
        try {
            ticketId = Long.parseLong(claims.id());
        } catch (NumberFormatException exception) {
            throw invalidPayload("Mã phiếu trong QR không hợp lệ");
        }
        LoanTicketDetailDTO ticket = loanService.getLoan(ticketId);
        if (ticket.getUserId() == null || !ticket.getUserId().equals(claims.userId())) {
            throw invalidPayload("Chủ sở hữu QR không khớp với phiếu mượn");
        }

        LoanTicketStatus status = LoanTicketStatus.fromDatabaseValue(ticket.getStatus());
        return QrResolveResponseDTO.builder()
                .type(claims.type())
                .subjectId(claims.id())
                .userId(ticket.getUserId())
                .readerName(ticket.getReaderName())
                .mssv(ticket.getMssv())
                .ticketId(ticket.getTicketId())
                .ticketStatus(status.name())
                .allowedActions(actionsFor(status))
                .items(ticket.getItems())
                .expiresAt(Instant.ofEpochSecond(claims.expiresAt()))
                .build();
    }

    private QrResolveResponseDTO resolveCard(QrClaims claims) {
        UUID userId;
        try {
            userId = UUID.fromString(claims.id());
        } catch (IllegalArgumentException exception) {
            throw invalidPayload("Mã bạn đọc trong QR không hợp lệ");
        }
        if (!userId.equals(claims.userId())) {
            throw invalidPayload("Chủ sở hữu QR không hợp lệ");
        }
        UserEntity reader = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bạn đọc"));
        int activeLoanCount = (int) loanTicketRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ticket -> LoanTicketStatus.fromDatabaseValue(ticket.getStatus()))
                .filter(status -> status == LoanTicketStatus.RESERVED
                        || status == LoanTicketStatus.BORROWING
                        || status == LoanTicketStatus.OVERDUE)
                .count();
        BigDecimal totalDebt = walletRepository.findByUserId(userId)
                .map(wallet -> wallet.getTotalDebt() == null ? BigDecimal.ZERO : wallet.getTotalDebt())
                .orElse(BigDecimal.ZERO);

        return QrResolveResponseDTO.builder()
                .type(claims.type())
                .subjectId(claims.id())
                .userId(userId)
                .readerName(reader.getFullName())
                .mssv(reader.getMssv())
                .cardStatus(reader.getCardStatus())
                .allowedActions(List.of("VIEW_READER"))
                .items(List.of())
                .activeLoanCount(activeLoanCount)
                .totalDebt(totalDebt)
                .expiresAt(Instant.ofEpochSecond(claims.expiresAt()))
                .build();
    }

    private List<String> actionsFor(LoanTicketStatus status) {
        return switch (status) {
            case PENDING -> List.of("REVIEW");
            case RESERVED -> List.of("CHECKOUT");
            case BORROWING, OVERDUE -> List.of("RETURN");
            default -> List.of("VIEW");
        };
    }

    private QrPayloadResponseDTO issue(String type, String id, UUID userId) {
        Instant expiresAt = clock.instant().plus(ttlMinutes, ChronoUnit.MINUTES);
        QrClaims claims = new QrClaims(1, type, id, userId, expiresAt.getEpochSecond());
        try {
            String encodedClaims = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(claims));
            String signingInput = TOKEN_PREFIX + "." + encodedClaims;
            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(sign(signingInput));
            return QrPayloadResponseDTO.builder()
                    .payload(signingInput + "." + signature)
                    .expiresAt(expiresAt)
                    .build();
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể tạo QR", exception);
        }
    }

    private QrClaims verify(String payload) {
        if (payload == null) {
            throw invalidPayload("QR trống");
        }
        String[] parts = payload.trim().split("\\.", -1);
        if (parts.length != 3 || !TOKEN_PREFIX.equals(parts[0])) {
            throw invalidPayload("QR không đúng định dạng HUIT");
        }
        try {
            byte[] suppliedSignature = Base64.getUrlDecoder().decode(parts[2]);
            String canonicalSignature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(suppliedSignature);
            if (!canonicalSignature.equals(parts[2])) {
                throw invalidPayload("Chữ ký QR không ở dạng mã hóa chuẩn");
            }
            byte[] expectedSignature = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
                throw invalidPayload("Chữ ký QR không hợp lệ");
            }
            QrClaims claims = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]), QrClaims.class);
            if (claims.version() != 1 || claims.type() == null || claims.id() == null
                    || claims.userId() == null || claims.expiresAt() <= 0) {
                throw invalidPayload("Dữ liệu QR không đầy đủ");
            }
            String normalizedType = claims.type().trim().toUpperCase(Locale.ROOT);
            if (!List.of("LOAN_TICKET", "USER_CARD").contains(normalizedType)) {
                throw invalidPayload("Loại QR không được hỗ trợ");
            }
            if (!clock.instant().isBefore(Instant.ofEpochSecond(claims.expiresAt()))) {
                throw new ResponseStatusException(HttpStatus.GONE, "QR đã hết hạn, vui lòng tạo mã mới");
            }
            return new QrClaims(claims.version(), normalizedType, claims.id(), claims.userId(), claims.expiresAt());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidPayload("Không thể xác thực QR");
        }
    }

    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private boolean hasStaffRole(UserEntity user) {
        String role = user.getRoleId() == null ? "" : user.getRoleId().toUpperCase(Locale.ROOT);
        return role.equals("ADMIN") || role.equals("LIBRARIAN");
    }

    private ResponseStatusException invalidPayload(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    record QrClaims(int version, String type, String id, UUID userId, long expiresAt) {
    }
}
