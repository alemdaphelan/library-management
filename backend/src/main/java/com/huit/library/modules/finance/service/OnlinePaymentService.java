package com.huit.library.modules.finance.service;

import com.huit.library.modules.finance.dto.DebtPaymentRequestDTO;
import com.huit.library.modules.finance.dto.OnlinePaymentCreateRequestDTO;
import com.huit.library.modules.finance.dto.OnlinePaymentSessionDTO;
import com.huit.library.modules.finance.dto.PaymentReceiptDTO;
import com.huit.library.modules.finance.entity.OnlinePaymentSessionEntity;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.OnlinePaymentSessionRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.circulation.service.LibraryHoursService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class OnlinePaymentService {
    private static final ZoneId VNPAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OnlinePaymentSessionRepository sessionRepository;
    private final WalletRepository walletRepository;
    private final DebtLedgerService debtLedgerService;
    private final boolean enabled;
    private final String paymentUrl;
    private final String tmnCode;
    private final String hashSecret;
    private final String returnUrl;
    private final String frontendResultUrl;
    private final int expireMinutes;
    private final LibraryHoursService libraryHoursService;

    public OnlinePaymentService(
            OnlinePaymentSessionRepository sessionRepository,
            WalletRepository walletRepository,
            DebtLedgerService debtLedgerService,
            @Value("${library.payment.vnpay.enabled:false}") boolean enabled,
            @Value("${library.payment.vnpay.payment-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String paymentUrl,
            @Value("${library.payment.vnpay.tmn-code:}") String tmnCode,
            @Value("${library.payment.vnpay.hash-secret:}") String hashSecret,
            @Value("${library.payment.vnpay.return-url:http://localhost:8080/api/v1/online-payments/vnpay-return}") String returnUrl,
            @Value("${library.payment.vnpay.frontend-result-url:http://localhost:4200/my-penalties}") String frontendResultUrl,
            @Value("${library.payment.vnpay.expire-minutes:15}") int expireMinutes,
            LibraryHoursService libraryHoursService) {
        this.sessionRepository = sessionRepository;
        this.walletRepository = walletRepository;
        this.debtLedgerService = debtLedgerService;
        this.enabled = enabled;
        this.paymentUrl = paymentUrl;
        this.tmnCode = tmnCode;
        this.hashSecret = hashSecret;
        this.returnUrl = returnUrl;
        this.frontendResultUrl = frontendResultUrl;
        this.expireMinutes = expireMinutes;
        this.libraryHoursService = libraryHoursService;
    }

    @Transactional
    public OnlinePaymentSessionDTO create(
            UUID userId, OnlinePaymentCreateRequestDTO request, String clientIp) {
        libraryHoursService.requireCounterOpen("tạo giao dịch thanh toán");
        requireConfigured();
        String purpose = request.getPurpose().trim().toUpperCase(Locale.ROOT);
        if (!List.of("DEBT", "DEPOSIT").contains(purpose)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mục đích thanh toán phải là DEBT hoặc DEPOSIT");
        }
        BigDecimal amount = request.getAmount().setScale(0, RoundingMode.UNNECESSARY);
        if ("DEBT".equals(purpose)) {
            WalletEntity wallet = walletRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đọc chưa có công nợ"));
            BigDecimal debt = zero(wallet.getTotalDebt());
            if (debt.signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn hiện không còn công nợ cần thanh toán");
            }
            if (amount.compareTo(debt) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Số tiền thanh toán vượt quá công nợ hiện tại");
            }
        }

        LocalDateTime now = LocalDateTime.now(VNPAY_ZONE);
        String reference = "UL" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        OnlinePaymentSessionEntity session = new OnlinePaymentSessionEntity();
        session.setReference(reference);
        session.setUserId(userId);
        session.setProvider("VNPAY");
        session.setPurpose(purpose);
        session.setAmount(amount);
        session.setStatus("PENDING");
        session.setExpiresAt(now.plusMinutes(expireMinutes));
        session = sessionRepository.save(session);

        SortedMap<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", amount.multiply(BigDecimal.valueOf(100)).toBigIntegerExact().toString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", reference);
        params.put("vnp_OrderInfo", ("DEBT".equals(purpose) ? "Thanh toan cong no " : "Nap tien the chan ") + reference);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", normalizeIp(clientIp));
        params.put("vnp_CreateDate", now.format(VNPAY_TIME));
        params.put("vnp_ExpireDate", session.getExpiresAt().format(VNPAY_TIME));
        String query = queryString(params);
        String gatewayUrl = paymentUrl + (paymentUrl.contains("?") ? "&" : "?")
                + query + "&vnp_SecureHash=" + hmacSha512(hashSecret, query);
        return toDto(session, gatewayUrl);
    }

    @Transactional(readOnly = true)
    public OnlinePaymentSessionDTO get(UUID userId, String reference) {
        OnlinePaymentSessionEntity session = sessionRepository.findByReferenceAndUserId(reference, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên thanh toán"));
        return toDto(session, null);
    }

    @Transactional
    public OnlinePaymentSessionDTO processCallback(Map<String, String> callbackParams) {
        requireConfigured();
        if (!isValidSignature(callbackParams)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chữ ký phản hồi VNPAY không hợp lệ");
        }
        String reference = callbackParams.get("vnp_TxnRef");
        OnlinePaymentSessionEntity session = sessionRepository.findByReferenceForUpdate(reference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên thanh toán"));
        if (!tmnCode.equals(callbackParams.get("vnp_TmnCode"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã website thanh toán không hợp lệ");
        }
        BigDecimal callbackAmount;
        try {
            callbackAmount = new BigDecimal(callbackParams.getOrDefault("vnp_Amount", "0"))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền phản hồi không hợp lệ");
        }
        if (callbackAmount.compareTo(session.getAmount()) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền phản hồi không khớp phiên thanh toán");
        }
        if ("COMPLETED".equals(session.getStatus())) {
            return toDto(session, null);
        }
        if (!"PENDING".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phiên thanh toán không còn hiệu lực");
        }

        String responseCode = callbackParams.getOrDefault("vnp_ResponseCode", "99");
        String transactionStatus = callbackParams.getOrDefault("vnp_TransactionStatus", responseCode);
        session.setResponseCode(responseCode);
        session.setGatewayTransactionNo(callbackParams.get("vnp_TransactionNo"));
        if (!"00".equals(responseCode) || !"00".equals(transactionStatus)) {
            session.setStatus("FAILED");
            return toDto(sessionRepository.save(session), null);
        }

        PaymentReceiptDTO receipt;
        if ("DEBT".equals(session.getPurpose())) {
            DebtPaymentRequestDTO payment = new DebtPaymentRequestDTO();
            payment.setUserId(session.getUserId());
            payment.setAmount(session.getAmount());
            payment.setMethod("VNPAY");
            payment.setIdempotencyKey("VNPAY:" + session.getReference());
            payment.setNote("Thanh toán công nợ trực tuyến qua VNPAY - " + session.getReference());
            receipt = debtLedgerService.recordPayment(payment, session.getUserId());
        } else {
            receipt = debtLedgerService.recordDepositTopUp(
                    session.getUserId(), session.getAmount(), session.getReference());
        }
        session.setPaymentTransactionId(receipt.getPaymentTransactionId());
        session.setStatus("COMPLETED");
        session.setCompletedAt(LocalDateTime.now(VNPAY_ZONE));
        return toDto(sessionRepository.save(session), null);
    }

    public String frontendRedirect(OnlinePaymentSessionDTO session) {
        String separator = frontendResultUrl.contains("?") ? "&" : "?";
        return frontendResultUrl + separator + "payment="
                + ("COMPLETED".equals(session.getStatus()) ? "success" : "failed")
                + "&reference=" + encode(session.getReference());
    }

    boolean isValidSignature(Map<String, String> params) {
        String received = params.get("vnp_SecureHash");
        if (received == null || received.isBlank()) {
            return false;
        }
        SortedMap<String, String> signed = new TreeMap<>();
        params.forEach((key, value) -> {
            if (key.startsWith("vnp_") && !"vnp_SecureHash".equals(key)
                    && !"vnp_SecureHashType".equals(key) && value != null && !value.isBlank()) {
                signed.put(key, value);
            }
        });
        String expected = hmacSha512(hashSecret, queryString(signed));
        return MessageDigest.isEqual(
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                received.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private OnlinePaymentSessionDTO toDto(OnlinePaymentSessionEntity session, String gatewayUrl) {
        return OnlinePaymentSessionDTO.builder()
                .reference(session.getReference())
                .provider(session.getProvider())
                .purpose(session.getPurpose())
                .amount(session.getAmount())
                .status(session.getStatus())
                .paymentUrl(gatewayUrl)
                .paymentTransactionId(session.getPaymentTransactionId())
                .expiresAt(session.getExpiresAt())
                .completedAt(session.getCompletedAt())
                .build();
    }

    private void requireConfigured() {
        if (!enabled || tmnCode.isBlank() || hashSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Thanh toán trực tuyến chưa được cấu hình. Vui lòng liên hệ quản trị viên.");
        }
    }

    private String queryString(SortedMap<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String hmacSha512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể ký yêu cầu thanh toán", ex);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String normalizeIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "127.0.0.1";
        }
        String first = clientIp.split(",")[0].trim();
        return "0:0:0:0:0:0:0:1".equals(first) || "::1".equals(first) ? "127.0.0.1" : first;
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
