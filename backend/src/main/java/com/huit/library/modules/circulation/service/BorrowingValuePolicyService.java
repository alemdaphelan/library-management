package com.huit.library.modules.circulation.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.catalog.service.BookValuationService;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.policy.service.LibraryPolicyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class BorrowingValuePolicyService {

    private final LoanDetailRepository loanDetailRepository;
    private final WalletRepository walletRepository;
    private final BookRepository bookRepository;
    private final LibraryPolicyService policyService;
    private final BookValuationService bookValuationService;

    public record BorrowingValueSnapshot(
            BigDecimal securityDeposit,
            BigDecimal currentBorrowedValue,
            BigDecimal requestedBookValue,
            BigDecimal maximumBorrowingValue,
            BigDecimal remainingBorrowingValue,
            boolean priceKnown,
            boolean depositEligible,
            boolean eligible,
            String message) {
    }

    public BorrowingValuePolicyService(
            LoanDetailRepository loanDetailRepository,
            WalletRepository walletRepository,
            BookRepository bookRepository,
            LibraryPolicyService policyService,
            BookValuationService bookValuationService) {
        this.loanDetailRepository = loanDetailRepository;
        this.walletRepository = walletRepository;
        this.bookRepository = bookRepository;
        this.policyService = policyService;
        this.bookValuationService = bookValuationService;
    }

    public void ensureCanAddBooks(UUID userId, Collection<Long> requestedBookIds) {
        var uniqueBookIds = new LinkedHashSet<>(requestedBookIds == null ? List.of() : requestedBookIds);
        if (uniqueBookIds.isEmpty()) {
            return;
        }

        WalletEntity wallet = requireWalletForUpdate(userId);
        BorrowingValueSnapshot snapshot = calculateSnapshot(userId, uniqueBookIds, wallet);
        requireEligible(snapshot);
    }

    public void ensureCurrentWithinLimit(UUID userId) {
        WalletEntity wallet = requireWalletForUpdate(userId);
        requireEligible(calculateSnapshot(userId, List.of(), wallet));
    }

    public BorrowingValueSnapshot getSnapshot(UUID userId, Collection<Long> requestedBookIds) {
        WalletEntity wallet = walletRepository.findByUserId(userId).orElse(null);
        return calculateSnapshot(userId, requestedBookIds, wallet);
    }

    private BorrowingValueSnapshot calculateSnapshot(
            UUID userId, Collection<Long> requestedBookIds, WalletEntity wallet) {
        BigDecimal deposit = wallet == null ? BigDecimal.ZERO : zeroIfNull(wallet.getDepositBalance());
        BigDecimal minimum = zeroIfNull(policyService.minimumSecurityDeposit());
        BigDecimal margin = zeroIfNull(policyService.borrowingValueMargin());
        BigDecimal maximumValue = deposit.add(margin);

        ValueTotal current = totalValue(loanDetailRepository.findOpenBookIdsByUserId(userId));
        var requestedIds = new LinkedHashSet<>(requestedBookIds == null ? List.of() : requestedBookIds);
        ValueTotal requested = totalValue(requestedIds);
        BigDecimal projectedValue = current.amount().add(requested.amount());
        BigDecimal remaining = maximumValue.subtract(current.amount()).max(BigDecimal.ZERO);
        boolean priceKnown = current.known() && requested.known();
        boolean depositEligible = deposit.compareTo(minimum) >= 0;
        boolean withinLimit = projectedValue.compareTo(maximumValue) <= 0;
        boolean eligible = priceKnown && depositEligible && withinLimit;

        String message;
        if (!priceKnown) {
            message = current.message() != null ? current.message() : requested.message();
        } else if (!depositEligible) {
            message = "Tiền thế chân hiện tại là " + formatMoney(deposit)
                    + ", thấp hơn mức tối thiểu " + formatMoney(minimum)
                    + ". Vui lòng bổ sung tiền thế chân trước khi mượn sách.";
        } else if (!withinLimit) {
            message = "Tổng giá trị tài liệu đang mượn và đăng ký dự kiến là " + formatMoney(projectedValue)
                    + ", vượt giới hạn " + formatMoney(maximumValue)
                    + " (tiền thế chân " + formatMoney(deposit)
                    + " + biên cho phép " + formatMoney(margin) + ").";
        } else {
            message = "Hạn mức giá trị còn lại là " + formatMoney(remaining) + ".";
        }

        return new BorrowingValueSnapshot(
                deposit, current.amount(), requested.amount(), maximumValue, remaining,
                priceKnown, depositEligible, eligible, message);
    }

    private ValueTotal totalValue(Collection<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return new ValueTotal(BigDecimal.ZERO, true, null);
        }
        var uniqueIds = new LinkedHashSet<>(bookIds);
        List<BookEntity> books = bookRepository.findAllById(uniqueIds);
        if (books.size() != uniqueIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đầy đủ tài liệu cần kiểm tra giá trị");
        }
        var byId = books.stream().collect(java.util.stream.Collectors.toMap(BookEntity::getBookId, book -> book));
        BigDecimal amount = BigDecimal.ZERO;
        for (Long bookId : bookIds) {
            BookValuationService.BookValue value = bookValuationService.resolve(byId.get(bookId));
            if (!value.known()) {
                return new ValueTotal(amount, false, value.message());
            }
            amount = amount.add(zeroIfNull(value.amount()));
        }
        return new ValueTotal(amount, true, null);
    }

    private WalletEntity requireWalletForUpdate(UUID userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Tài khoản chưa có ví tiền thế chân. Vui lòng liên hệ thủ thư để kích hoạt quyền mượn."));
    }

    private void requireEligible(BorrowingValueSnapshot snapshot) {
        if (!snapshot.eligible()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, snapshot.message());
        }
    }

    private record ValueTotal(BigDecimal amount, boolean known, String message) {
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String formatMoney(BigDecimal value) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"))
                .format(value == null ? BigDecimal.ZERO : value) + " đồng";
    }
}
