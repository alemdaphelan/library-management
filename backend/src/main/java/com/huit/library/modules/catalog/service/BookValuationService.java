package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.penalty.repository.CompensationRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.StandardEnvironment;

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
    private final boolean demoPricingEnabled;

    public BookValuationService(CompensationRateRepository compensationRateRepository) {
        this(compensationRateRepository, false, new StandardEnvironment());
    }

    @Autowired
    public BookValuationService(CompensationRateRepository compensationRateRepository,
            @Value("${library.catalog.demo-pricing-enabled:false}") boolean demoPricingEnabled,
            Environment environment) {
        this.compensationRateRepository = compensationRateRepository;
        if (demoPricingEnabled && (environment.acceptsProfiles(Profiles.of("prod", "production"))
                || !environment.acceptsProfiles(Profiles.of("dev", "test")))) {
            throw new IllegalStateException("Demo catalog pricing requires dev/test and must not run in production");
        }
        this.demoPricingEnabled = demoPricingEnabled;
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
        String source = normalize(book.getPriceSource());
        boolean realCatalogPrice = manualPrice != null && manualPrice.signum() > 0
                && !"UNKNOWN".equals(status)
                && !"CONFIGURED_FALLBACK".equals(source) && !"DEMO_GENERATED".equals(source);
        if (demoPricingEnabled && !realCatalogPrice && book.getBookId() != null) {
            // Stable per-book value, matching migration 43 without rewriting catalog or money history.
            long bucket = Math.floorMod(Math.floorMod(book.getBookId(), 51L) * 7919L, 51L);
            return new BookValue(BigDecimal.valueOf(15000L + bucket * 1000L), true,
                    "DEMO_GENERATED", "VND", null);
        }
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
