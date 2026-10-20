package com.huit.library.modules.dashboard.service;

import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.dashboard.dto.DashboardStatsDTO;
import com.huit.library.modules.dashboard.dto.RecentActivityDTO;
import com.huit.library.modules.dashboard.dto.TrendingBookDTO;
import com.huit.library.modules.dashboard.dto.WeeklyStatDTO;
import com.huit.library.modules.finance.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private final BookCopyRepository bookCopyRepository;
    private final BookRepository bookRepository;
    private final LoanTicketRepository loanTicketRepository;
    private final LoanDetailRepository loanDetailRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public DashboardService(
            BookCopyRepository bookCopyRepository,
            BookRepository bookRepository,
            LoanTicketRepository loanTicketRepository,
            LoanDetailRepository loanDetailRepository,
            UserRepository userRepository,
            TransactionRepository transactionRepository) {
        this.bookCopyRepository = bookCopyRepository;
        this.bookRepository = bookRepository;
        this.loanTicketRepository = loanTicketRepository;
        this.loanDetailRepository = loanDetailRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    public DashboardStatsDTO getStats() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal fineCollected = transactionRepository.sumAmountByType("FINE");
        return new DashboardStatsDTO(
                bookCopyRepository.count(),
                bookCopyRepository.countByStatus("AVAILABLE"),
                bookCopyRepository.countByStatus("BORROWED"),
                bookCopyRepository.countByStatus("RESERVED"),
                loanDetailRepository.countByReturnDateIsNullAndDueDateBefore(now),
                fineCollected == null ? BigDecimal.ZERO : fineCollected,
                now);
    }

    public List<WeeklyStatDTO> getWeeklyStats() {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(6);
        List<LoanTicketEntity> tickets = loanTicketRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        firstDay.atStartOfDay(), today.plusDays(1).atStartOfDay());

        Map<LocalDate, Long> countByDate = tickets.stream()
                .filter(ticket -> ticket.getCreatedAt() != null)
                .collect(Collectors.groupingBy(ticket -> ticket.getCreatedAt().toLocalDate(), Collectors.counting()));
        long maximum = Math.max(1, countByDate.values().stream().mapToLong(Long::longValue).max().orElse(0));

        List<WeeklyStatDTO> result = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            LocalDate date = firstDay.plusDays(index);
            long count = countByDate.getOrDefault(date, 0L);
            result.add(new WeeklyStatDTO(date, vietnameseDay(date), count,
                    (int) Math.round(count * 100.0 / maximum)));
        }
        return result;
    }

    public List<RecentActivityDTO> getRecentActivities() {
        return loanTicketRepository.findTop10ByOrderByUpdatedAtDesc().stream()
                .map(this::toActivity)
                .toList();
    }

    public List<TrendingBookDTO> getTrendingBooks() {
        return loanDetailRepository.findTrendingBooks(PageRequest.of(0, 5)).stream()
                .map(book -> new TrendingBookDTO(
                        book.getBookId(), book.getTitle(), book.getAuthor(), book.getBorrows()))
                .toList();
    }

    private RecentActivityDTO toActivity(LoanTicketEntity ticket) {
        LoanDetailEntity detail = loanDetailRepository.findFirstByTicketId(ticket.getTicketId()).orElse(null);
        String item = resolveBookTitle(detail);
        String user = ticket.getUserId() == null ? "Bạn đọc" : userRepository.findById(ticket.getUserId())
                .map(person -> person.getFullName() != null ? person.getFullName() : person.getMssv())
                .orElse("Bạn đọc");

        String status = ticket.getStatus() == null ? "" : ticket.getStatus().toUpperCase(Locale.ROOT);
        String type;
        String action;
        if ("RETURNED".equals(status) || detail != null && detail.getReturnDate() != null) {
            type = "return";
            action = "đã trả";
        } else if ("CANCELLED".equals(status)) {
            type = "cancel";
            action = "đã hủy phiếu cho";
        } else if (detail != null && detail.getRenewalCount() != null && detail.getRenewalCount() > 0) {
            type = "renew";
            action = "đã gia hạn";
        } else {
            type = "borrow";
            action = "đã đặt/mượn";
        }

        LocalDateTime time = latest(ticket.getUpdatedAt(), ticket.getCreatedAt());
        if (detail != null) time = latest(time, latest(detail.getUpdatedAt(), detail.getCreatedAt()));
        return new RecentActivityDTO(type, user, action, item, time);
    }

    private String resolveBookTitle(LoanDetailEntity detail) {
        if (detail == null || detail.getBarcode() == null) return "phiếu thư viện";
        return bookCopyRepository.findById(detail.getBarcode())
                .map(BookCopyEntity::getBookId)
                .flatMap(bookRepository::findById)
                .map(BookEntity::getTitle)
                .orElse("tài liệu " + detail.getBarcode());
    }

    private LocalDateTime latest(LocalDateTime first, LocalDateTime second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isAfter(second) ? first : second;
    }

    private String vietnameseDay(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "T2";
            case TUESDAY -> "T3";
            case WEDNESDAY -> "T4";
            case THURSDAY -> "T5";
            case FRIDAY -> "T6";
            case SATURDAY -> "T7";
            case SUNDAY -> "CN";
        };
    }
}
