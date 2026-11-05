package com.huit.library.modules.finance.service;

import com.huit.library.modules.finance.dto.*;
import com.huit.library.modules.finance.entity.OnlinePaymentSessionEntity;
import com.huit.library.modules.finance.repository.OnlinePaymentSessionRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Profile("(dev | test) & !prod & !production")
@ConditionalOnProperty(name = "library.payment.mock.enabled", havingValue = "true")
public class MockPaymentService {
    private final OnlinePaymentSessionRepository sessions;
    private final WalletRepository wallets;
    private final DebtLedgerService ledger;
    private final JdbcTemplate jdbc;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public MockPaymentService(OnlinePaymentSessionRepository sessions, WalletRepository wallets,
            DebtLedgerService ledger, JdbcTemplate jdbc) {
        this.sessions = sessions; this.wallets = wallets; this.ledger = ledger; this.jdbc = jdbc;
    }

    @Transactional
    public OnlinePaymentSessionDTO create(UUID userId, MockPaymentModels.Create request) {
        serialize(userId);
        String reference = reference(userId, request.idempotencyKey());
        var existing = sessions.findByReferenceForUpdate(reference).orElse(null);
        if (existing != null) {
            if (!userId.equals(existing.getUserId()) || !"MOCK".equals(existing.getProvider())
                    || !request.purpose().name().equals(existing.getPurpose()) || request.amount().compareTo(existing.getAmount()) != 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã yêu cầu đã dùng cho giao dịch khác");
            return dto(existing);
        }
        if (request.purpose() == MockPaymentModels.Purpose.DEBT) {
            BigDecimal debt = wallets.findByUserId(userId).map(w -> w.getTotalDebt() == null ? BigDecimal.ZERO : w.getTotalDebt()).orElse(BigDecimal.ZERO);
            if (request.amount().compareTo(debt) > 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Số tiền vượt công nợ hiện tại");
        }
        var session = new OnlinePaymentSessionEntity();
        session.setReference(reference); session.setUserId(userId); session.setProvider("MOCK");
        session.setPurpose(request.purpose().name()); session.setAmount(request.amount()); session.setStatus("PENDING");
        session.setExpiresAt(now().plusMinutes(15));
        return dto(sessions.saveAndFlush(session));
    }

    @Transactional(readOnly = true)
    public OnlinePaymentSessionDTO get(UUID userId, String reference) {
        var session = sessions.findByReferenceAndUserId(reference, userId).filter(s -> "MOCK".equals(s.getProvider()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên giả lập"));
        return dto(session);
    }

    @Transactional
    public OnlinePaymentSessionDTO finish(UUID userId, String reference, MockPaymentModels.Outcome outcome) {
        serialize(userId);
        var session = sessions.findByReferenceForUpdate(reference)
                .filter(s -> userId.equals(s.getUserId()) && "MOCK".equals(s.getProvider()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên giả lập"));
        // First terminal result wins, including retries with a different requested outcome.
        if (!"PENDING".equals(session.getStatus())) return dto(session);
        if (!session.getExpiresAt().isAfter(now())) session.setStatus("EXPIRED");
        else if (outcome != MockPaymentModels.Outcome.SUCCESS) {
            session.setStatus(outcome == MockPaymentModels.Outcome.CANCEL ? "CANCELLED" : "FAILED");
        } else {
            PaymentReceiptDTO receipt;
            if ("DEBT".equals(session.getPurpose())) {
                var payment = new DebtPaymentRequestDTO();
                payment.setUserId(userId); payment.setAmount(session.getAmount()); payment.setMethod("MOCK");
                payment.setIdempotencyKey(session.getReference()); payment.setNote("GIẢ LẬP: trả công nợ để kiểm thử " + reference);
                receipt = ledger.recordMockPayment(payment, userId);
            } else receipt = ledger.recordMockDepositTopUp(userId, session.getAmount(), reference);
            session.setPaymentTransactionId(receipt.getPaymentTransactionId());
            session.setStatus("COMPLETED"); session.setCompletedAt(now());
        }
        return dto(sessions.saveAndFlush(session));
    }

    private void serialize(UUID userId) {
        // Serializes mock sessions for a reader, including first top-ups with no wallet yet.
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?,0))")) {
                statement.setString(1, "MOCK_PAYMENT:" + userId); statement.execute();
            }
            return null;
        });
    }

    private String reference(UUID userId, UUID key) {
        try { return "MOCK-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((userId + ":" + key).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private LocalDateTime now() { return LocalDateTime.now(ZONE); }
    private OnlinePaymentSessionDTO dto(OnlinePaymentSessionEntity s) {
        String status = "PENDING".equals(s.getStatus()) && !s.getExpiresAt().isAfter(now()) ? "EXPIRED" : s.getStatus();
        return OnlinePaymentSessionDTO.builder().reference(s.getReference()).provider("MOCK").purpose(s.getPurpose())
                .amount(s.getAmount()).status(status).expiresAt(s.getExpiresAt()).completedAt(s.getCompletedAt())
                .paymentTransactionId(s.getPaymentTransactionId()).build();
    }
}
