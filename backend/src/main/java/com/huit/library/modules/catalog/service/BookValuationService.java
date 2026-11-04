package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.penalty.repository.CompensationRateRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

@Service
public class BookValuationService {

    public record BookValue(
            BigDecimal amount,
            boolean known,
            String source,
            String currency,
            String message) {
    }

    private final CompensationRateRepository compensationRateRepository;

    public BookValuationService(CompensationRateRepository compensationRateRepository) {
        this.compensationRateRepository = compensationRateRepository;
    }

    public BookValue resolve(BookEntity book) {
        if (book == null) {
            return unknown("Không tìm thấy tài liệu để xác định giá");
        }
        if (Boolean.TRUE.equals(book.getIsDigital())) {
            return new BookValue(BigDecimal.ZERO, true, "DIGITAL", "VND", null);
        }

        BigDecimal manualPrice = book.getDefaultPrice();
        String status = normalize(book.getPriceStatus());
        if (manualPrice != null && manualPrice.signum() > 0 && !"UNKNOWN".equals(status)) {
            return new BookValue(
                    manualPrice,
                    true,
                    valueOrDefault(book.getPriceSource(), "CATALOG"),
                    valueOrDefault(book.getPriceCurrency(), "VND"),
                    null);
        }

        String materialType = valueOrDefault(book.getMaterialType(), "BOOK");
        String languageCategory = isVietnamese(book.getLanguage()) ? "VIETNAMESE" : "FOREIGN";
        return compensationRateRepository.findCurrent(materialType, languageCategory, LocalDate.now())
                .filter(rate -> rate.getAmount() != null && rate.getAmount().signum() > 0)
                .map(rate -> new BookValue(
                        rate.getAmount(),
                        true,
                        "CONFIGURED_FALLBACK",
                        "VND",
                        null))
                .orElseGet(() -> unknown(
                        "Tài liệu \"" + valueOrDefault(book.getTitle(), "không rõ tên")
                                + "\" chưa được xác định giá. Vui lòng liên hệ thủ thư để cập nhật giá trước khi mượn."));
    }

    private BookValue unknown(String message) {
        return new BookValue(null, false, "UNKNOWN", "VND", message);
    }

    private boolean isVietnamese(String language) {
        String normalized = normalize(language);
        return normalized.equals("VI")
                || normalized.equals("VIE")
                || normalized.contains("VIET")
                || normalized.contains("TIENG VIET");
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
