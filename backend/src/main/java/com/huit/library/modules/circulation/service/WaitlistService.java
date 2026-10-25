package com.huit.library.modules.circulation.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.circulation.entity.HolidayCalendarEntity;
import com.huit.library.modules.circulation.entity.WaitlistEntity;
import com.huit.library.modules.circulation.repository.HolidayCalendarRepository;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.WaitlistRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Service
public class WaitlistService {
    private final WaitlistRepository waitlistRepository;
    private final BookCopyRepository copyRepository;
    private final HolidayCalendarRepository holidayRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final LoanDetailRepository detailRepository;
    private final NotificationService notificationService;
    private final ReservationNoShowService noShowService;
    private final LocalTime defaultOpenTime;
    private final LocalTime defaultCloseTime;

    public WaitlistService(
            WaitlistRepository waitlistRepository,
            BookCopyRepository copyRepository,
            HolidayCalendarRepository holidayRepository,
            UserRepository userRepository,
            WalletRepository walletRepository,
            LoanDetailRepository detailRepository,
            NotificationService notificationService,
            ReservationNoShowService noShowService,
            @Value("${library.circulation.default-open-time:07:30}") String defaultOpenTime,
            @Value("${library.circulation.default-close-time:17:00}") String defaultCloseTime) {
        this.waitlistRepository = waitlistRepository;
        this.copyRepository = copyRepository;
        this.holidayRepository = holidayRepository;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.detailRepository = detailRepository;
        this.notificationService = notificationService;
        this.noShowService = noShowService;
        this.defaultOpenTime = LocalTime.parse(defaultOpenTime);
        this.defaultCloseTime = LocalTime.parse(defaultCloseTime);
    }

    @Transactional
    public WaitlistEntity join(Long bookId, UUID userId) {
        if (copyRepository.countByBookIdAndStatus(bookId, "AVAILABLE") > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tài liệu đang có bản sẵn sàng, không cần vào hàng đợi");
        }
        if (waitlistRepository.existsByBookIdAndUserIdAndStatus(bookId, userId, "WAITING")
                || waitlistRepository.findFirstByBookIdAndUserIdAndStatus(bookId, userId, "NOTIFIED").isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã có lượt hoạt động cho đầu sách này");
        }
        if (!isEligible(userId, LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đọc hiện không đủ điều kiện tham gia hàng đợi");
        }
        WaitlistEntity entry = new WaitlistEntity();
        entry.setBookId(bookId);
        entry.setUserId(userId);
        entry.setStatus("WAITING");
        return waitlistRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<WaitlistEntity> getMine(UUID userId) {
        return waitlistRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public LocalDateTime calculateDirectReservationDeadline(LocalDate pickupDate, LocalDateTime now) {
        LocalDate selectedDate = pickupDate != null ? pickupDate : now.toLocalDate();
        if (!selectedDate.equals(now.toLocalDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Phiếu đặt giữ trực tiếp chỉ có hiệu lực trong ngày hiện tại");
        }
        if (isClosed(selectedDate)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ngày nhận sách đã chọn là ngày thư viện đóng cửa");
        }
        LocalDateTime deadline = selectedDate.atTime(closingTime(selectedDate));
        if (!deadline.isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thư viện đã hết giờ nhận sách trong ngày đã chọn");
        }
        return deadline;
    }

    @Transactional
    public Optional<WaitlistEntity> promoteNext(BookCopyEntity copy, LocalDateTime now) {
        while (true) {
            Optional<WaitlistEntity> next = waitlistRepository
                    .findFirstByBookIdAndStatusOrderByCreatedAtAsc(copy.getBookId(), "WAITING");
            if (next.isEmpty()) {
                return Optional.empty();
            }
            WaitlistEntity entry = next.get();
            if (!isEligible(entry.getUserId(), now)) {
                entry.setStatus("SKIPPED");
                waitlistRepository.save(entry);
                continue;
            }
            LocalDateTime expiresAt = adjustToOpening(now.plusHours(48));
            entry.setStatus("NOTIFIED");
            entry.setNotifiedAt(now);
            entry.setExpiresAt(expiresAt);
            entry.setReservedBarcode(copy.getBarcode());
            waitlistRepository.save(entry);

            copy.setStatus("RESERVED");
            copy.setLockedByUser(entry.getUserId());
            copy.setLockedUntil(expiresAt);
            copyRepository.save(copy);
            notificationService.createOnce(
                    entry.getUserId(), "WAITLIST_AVAILABLE",
                    "Tài liệu trong hàng đợi đã sẵn sàng. Vui lòng nhận trước " + expiresAt + ".",
                    "WAITLIST", entry.getWaitlistId().toString(),
                    "WAITLIST_AVAILABLE:" + entry.getWaitlistId(), now);
            return Optional.of(entry);
        }
    }

    @Transactional
    public int expireAndPromote(LocalDateTime now) {
        int expired = 0;
        for (WaitlistEntity entry : waitlistRepository.findByStatusAndExpiresAtBefore("NOTIFIED", now)) {
            entry.setStatus("EXPIRED");
            waitlistRepository.save(entry);
            expired++;
            noShowService.record(entry.getUserId(), now,
                    "WAITLIST", entry.getWaitlistId().toString());
            if (entry.getReservedBarcode() == null) {
                continue;
            }
            BookCopyEntity copy = copyRepository.findById(entry.getReservedBarcode()).orElse(null);
            if (copy == null || !entry.getUserId().equals(copy.getLockedByUser())) {
                continue;
            }
            copy.setStatus("AVAILABLE");
            copy.setLockedByUser(null);
            copy.setLockedUntil(null);
            copyRepository.save(copy);
            notificationService.createOnce(
                    entry.getUserId(), "WAITLIST_EXPIRED",
                    "Lượt giữ sách trong hàng đợi đã hết hạn sau 48 giờ.",
                    "WAITLIST", entry.getWaitlistId().toString(),
                    "WAITLIST_EXPIRED:" + entry.getWaitlistId(), now);
            promoteNext(copy, now);
        }
        return expired;
    }

    private boolean isEligible(java.util.UUID userId, LocalDateTime now) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null || Boolean.TRUE.equals(user.getDisciplineFlag())
                || !"ACTIVE".equalsIgnoreCase(user.getCardStatus())) {
            return false;
        }
        boolean hasDebt = walletRepository.findByUserId(userId)
                .map(wallet -> wallet.getTotalDebt() != null && wallet.getTotalDebt().signum() > 0)
                .orElse(false);
        if (hasDebt || detailRepository.countOverdueItemsByUserId(userId, now) > 0) {
            return false;
        }
        if (user.getCirculationLockedUntil() != null) {
            if (user.getCirculationLockedUntil().isAfter(now)) {
                return false;
            }
            return detailRepository.countOpenItemsByUserId(
                    userId, List.of("BORROWING", "OVERDUE")) == 0;
        }
        return true;
    }

    private LocalDateTime adjustToOpening(LocalDateTime candidate) {
        LocalDate date = candidate.toLocalDate();
        while (isClosed(date)) {
            date = date.plusDays(1);
        }
        LocalTime open = openingTime(date);
        LocalTime close = closingTime(date);
        if (candidate.toLocalDate().isBefore(date) || candidate.toLocalTime().isBefore(open)) {
            return date.atTime(open);
        }
        if (!candidate.toLocalTime().isBefore(close)) {
            do {
                date = date.plusDays(1);
            } while (isClosed(date));
            return date.atTime(openingTime(date));
        }
        return candidate;
    }

    private boolean isClosed(LocalDate date) {
        return holidayRepository.findById(date.toString())
                .map(day -> Boolean.TRUE.equals(day.getIsClosed()))
                .orElse(false);
    }

    private LocalTime openingTime(LocalDate date) {
        return holidayRepository.findById(date.toString())
                .map(HolidayCalendarEntity::getOverrideOpenTime)
                .filter(value -> value != null && !value.isBlank())
                .map(LocalTime::parse)
                .orElse(defaultOpenTime);
    }

    private LocalTime closingTime(LocalDate date) {
        return holidayRepository.findById(date.toString())
                .map(HolidayCalendarEntity::getOverrideCloseTime)
                .filter(value -> value != null && !value.isBlank())
                .map(LocalTime::parse)
                .orElse(defaultCloseTime);
    }
}
