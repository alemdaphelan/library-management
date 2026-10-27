package com.huit.library.modules.finance.service;

import com.huit.library.modules.finance.dto.OnlinePaymentCreateRequestDTO;
import com.huit.library.modules.finance.dto.OnlinePaymentSessionDTO;
import com.huit.library.modules.finance.dto.PaymentReceiptDTO;
import com.huit.library.modules.finance.entity.OnlinePaymentSessionEntity;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.OnlinePaymentSessionRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OnlinePaymentServiceTest {
    private static final String SECRET = "sandbox-secret-for-tests";

    @Test
    void createsSignedDebtPaymentUrlWithoutExposingSecret() {
        OnlinePaymentSessionRepository sessions = mock(OnlinePaymentSessionRepository.class);
        WalletRepository wallets = mock(WalletRepository.class);
        DebtLedgerService ledger = mock(DebtLedgerService.class);
        UUID userId = UUID.randomUUID();
        WalletEntity wallet = new WalletEntity();
        wallet.setUserId(userId);
        wallet.setTotalDebt(new BigDecimal("75000"));
        when(wallets.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        OnlinePaymentService service = service(sessions, wallets, ledger, true);
        OnlinePaymentCreateRequestDTO request = new OnlinePaymentCreateRequestDTO();
        request.setPurpose("debt");
        request.setAmount(new BigDecimal("50000"));

        OnlinePaymentSessionDTO result = service.create(userId, request, "127.0.0.1");

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getPaymentUrl()).contains("vnp_TmnCode=TESTCODE", "vnp_SecureHash=");
        assertThat(result.getPaymentUrl()).doesNotContain(SECRET);
    }

    @Test
    void rejectsCreationWhenGatewayCredentialsAreDisabled() {
        OnlinePaymentCreateRequestDTO request = new OnlinePaymentCreateRequestDTO();
        request.setPurpose("DEPOSIT");
        request.setAmount(new BigDecimal("50000"));

        assertThatThrownBy(() -> service(
                mock(OnlinePaymentSessionRepository.class), mock(WalletRepository.class),
                mock(DebtLedgerService.class), false).create(UUID.randomUUID(), request, "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("chưa được cấu hình");
    }

    @Test
    void signedDepositCallbackIsIdempotentlyRecordedAsRealTransaction() throws Exception {
        OnlinePaymentSessionRepository sessions = mock(OnlinePaymentSessionRepository.class);
        WalletRepository wallets = mock(WalletRepository.class);
        DebtLedgerService ledger = mock(DebtLedgerService.class);
        UUID userId = UUID.randomUUID();
        OnlinePaymentSessionEntity session = new OnlinePaymentSessionEntity();
        session.setReference("UL-TEST-1");
        session.setUserId(userId);
        session.setProvider("VNPAY");
        session.setPurpose("DEPOSIT");
        session.setAmount(new BigDecimal("50000"));
        session.setStatus("PENDING");
        session.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(sessions.findByReferenceForUpdate("UL-TEST-1")).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledger.recordDepositTopUp(userId, new BigDecimal("50000"), "UL-TEST-1"))
                .thenReturn(PaymentReceiptDTO.builder().paymentTransactionId(901L).build());
        SortedMap<String, String> params = new TreeMap<>();
        params.put("vnp_TmnCode", "TESTCODE");
        params.put("vnp_TxnRef", "UL-TEST-1");
        params.put("vnp_Amount", "5000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "VNP123");
        params.put("vnp_SecureHash", hmac(query(params)));

        OnlinePaymentSessionDTO result = service(sessions, wallets, ledger, true).processCallback(params);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getPaymentTransactionId()).isEqualTo(901L);
        verify(ledger).recordDepositTopUp(userId, new BigDecimal("50000"), "UL-TEST-1");
    }

    @Test
    void rejectsCallbackWhenSignatureWasTampered() {
        OnlinePaymentSessionRepository sessions = mock(OnlinePaymentSessionRepository.class);
        DebtLedgerService ledger = mock(DebtLedgerService.class);
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", "TESTCODE");
        params.put("vnp_TxnRef", "UL-TAMPERED");
        params.put("vnp_Amount", "5000000");
        params.put("vnp_SecureHash", "invalid-signature");

        assertThatThrownBy(() -> service(
                sessions, mock(WalletRepository.class), ledger, true).processCallback(params))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Chữ ký phản hồi VNPAY không hợp lệ");
        verifyNoInteractions(sessions, ledger);
    }

    @Test
    void completedCallbackDoesNotRecordPaymentTwice() throws Exception {
        OnlinePaymentSessionRepository sessions = mock(OnlinePaymentSessionRepository.class);
        DebtLedgerService ledger = mock(DebtLedgerService.class);
        OnlinePaymentSessionEntity session = new OnlinePaymentSessionEntity();
        session.setReference("UL-COMPLETED");
        session.setUserId(UUID.randomUUID());
        session.setProvider("VNPAY");
        session.setPurpose("DEBT");
        session.setAmount(new BigDecimal("25000"));
        session.setStatus("COMPLETED");
        session.setPaymentTransactionId(902L);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(sessions.findByReferenceForUpdate("UL-COMPLETED")).thenReturn(Optional.of(session));
        SortedMap<String, String> params = new TreeMap<>();
        params.put("vnp_TmnCode", "TESTCODE");
        params.put("vnp_TxnRef", "UL-COMPLETED");
        params.put("vnp_Amount", "2500000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", hmac(query(params)));

        OnlinePaymentSessionDTO result = service(
                sessions, mock(WalletRepository.class), ledger, true).processCallback(params);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getPaymentTransactionId()).isEqualTo(902L);
        verifyNoInteractions(ledger);
        verify(sessions, never()).save(any());
    }

    private OnlinePaymentService service(
            OnlinePaymentSessionRepository sessions,
            WalletRepository wallets,
            DebtLedgerService ledger,
            boolean enabled) {
        return new OnlinePaymentService(
                sessions, wallets, ledger, enabled,
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "TESTCODE", SECRET,
                "http://localhost:8080/api/v1/online-payments/vnpay-return",
                "http://localhost:4200/my-penalties", 15);
    }

    private String query(SortedMap<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> !"vnp_SecureHash".equals(entry.getKey()))
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right).orElse("");
    }

    private String hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
