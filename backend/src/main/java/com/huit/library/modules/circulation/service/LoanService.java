package com.huit.library.modules.circulation.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.circulation.dto.BorrowRequestDTO;
import com.huit.library.modules.circulation.dto.BorrowResponseDTO;
import com.huit.library.modules.circulation.dto.LoanItemDTO;
import com.huit.library.modules.circulation.dto.LoanTicketDetailDTO;
import com.huit.library.modules.circulation.dto.LoanTicketSummaryDTO;
import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.entity.WaitlistEntity;
import com.huit.library.modules.circulation.model.LoanDetailStatus;
import com.huit.library.modules.circulation.model.LoanTicketStatus;
import com.huit.library.modules.circulation.model.LoanTicketType;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.circulation.repository.WaitlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

@Service
public class LoanService {

    private final LoanTicketRepository loanTicketRepository;
    private final LoanDetailRepository loanDetailRepository;
    private final WaitlistRepository waitlistRepository;
    private final BookCopyRepository bookCopyRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final CirculationStateService circulationStateService;

    public LoanService(
            LoanTicketRepository loanTicketRepository,
            LoanDetailRepository loanDetailRepository,
            WaitlistRepository waitlistRepository,
            BookCopyRepository bookCopyRepository,
            BookRepository bookRepository,
            UserRepository userRepository,
            CirculationStateService circulationStateService) {
        this.loanTicketRepository = loanTicketRepository;
        this.loanDetailRepository = loanDetailRepository;
        this.waitlistRepository = waitlistRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.circulationStateService = circulationStateService;
    }

    @Transactional
    public BorrowResponseDTO reserveOrBorrowBook(BorrowRequestDTO request, UUID userId) {
        UserEntity user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin người dùng trong hệ thống"));

        if (Boolean.TRUE.equals(user.getDisciplineFlag()) || "LOCKED".equalsIgnoreCase(user.getCardStatus())) {
            throw new RuntimeException("Tài khoản của bạn đang bị khóa hoặc hạn chế mượn sách. Vui lòng liên hệ thủ thư!");
        }
        circulationStateService.ensureEligibleForReservation(userId);

        BookEntity book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cuốn sách này trong cơ sở dữ liệu"));

        // Người đứng đầu hàng đợi có thể nhận đúng bản sao đã được khóa cho mình.
        WaitlistEntity notifiedEntry = waitlistRepository
                .findFirstByBookIdAndUserIdAndStatus(book.getBookId(), userId, "NOTIFIED")
                .filter(entry -> entry.getReservedBarcode() != null)
                .filter(entry -> entry.getExpiresAt() != null && entry.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElse(null);
        java.util.Optional<BookCopyEntity> availableCopy = notifiedEntry == null
                ? bookCopyRepository.findFirstByBookIdAndStatusOrderByBarcodeAsc(book.getBookId(), "AVAILABLE")
                : bookCopyRepository.findById(notifiedEntry.getReservedBarcode())
                        .filter(copy -> "RESERVED".equalsIgnoreCase(copy.getStatus()))
                        .filter(copy -> userId.equals(copy.getLockedByUser()));

        // If no available copies, join waitlist
        if (availableCopy.isEmpty()) {
            if (waitlistRepository.findFirstByBookIdAndUserIdAndStatus(
                    book.getBookId(), userId, "NOTIFIED").isPresent()) {
                return BorrowResponseDTO.builder()
                        .success(false)
                        .message("Lượt giữ từ hàng đợi đã hết hiệu lực hoặc đang được hệ thống xử lý.")
                        .status("WAITLIST")
                        .bookTitle(book.getTitle())
                        .build();
            }
            if (waitlistRepository.existsByBookIdAndUserIdAndStatus(book.getBookId(), userId, "WAITING")) {
                return BorrowResponseDTO.builder()
                        .success(false)
                        .message("Tài liệu hiện đã hết bản in sẵn sàng. Bạn đã có tên trong hàng đợi đặt trước rồi!")
                        .status("WAITLIST")
                        .bookTitle(book.getTitle())
                        .build();
            }

            WaitlistEntity waitlist = new WaitlistEntity();
            waitlist.setBookId(book.getBookId());
            waitlist.setUserId(userId);
            waitlist.setStatus("WAITING");
            waitlistRepository.save(waitlist);

            return BorrowResponseDTO.builder()
                    .success(true)
                    .message("Tài liệu hiện đang được mượn hết. Hệ thống đã thêm bạn vào Hàng đợi đặt trước (Waitlist). Thư viện sẽ gửi thông báo ngay khi có sách hoàn trả!")
                    .status("WAITLIST")
                    .bookTitle(book.getTitle())
                    .build();
        }

        // Pick the first available copy
        BookCopyEntity copy = availableCopy.get();

        // Calculate pickup and due dates
        LocalDate pickupDate = request.getPickupDate() != null ? request.getPickupDate() : LocalDate.now().plusDays(1);
        LocalDateTime pickupDateTime = pickupDate.atTime(17, 0);
        LocalDateTime dueDateTime;
        LoanTicketType ticketType = LoanTicketType.fromDatabaseValue(request.getTicketType());
        if (ticketType == LoanTicketType.ONSITE) {
            dueDateTime = pickupDate.atTime(21, 0); // Đọc tại chỗ trong ngày
        } else {
            dueDateTime = pickupDateTime.plusDays(10); // Quy định mượn về nhà 10 ngày
        }

        // Create LoanTicket
        LoanTicketEntity ticket = new LoanTicketEntity();
        ticket.setUserId(userId);
        ticket.setTicketType(ticketType.name());
        ticket.setStatus(LoanTicketStatus.PENDING.name());
        ticket = loanTicketRepository.save(ticket);

        // Create LoanDetail
        LoanDetailEntity detail = new LoanDetailEntity();
        detail.setTicketId(ticket.getTicketId());
        detail.setBarcode(copy.getBarcode());
        detail.setCheckoutConditionNote(request.getNote() != null && !request.getNote().isBlank()
                ? request.getNote()
                : "Đặt giữ trước qua cổng trực tuyến OPAC");
        detail.setReturnStatus(LoanDetailStatus.PENDING.name());
        detail.setDueDate(dueDateTime);
        loanDetailRepository.save(detail);

        // Lock / Reserve book copy
        copy.setStatus("RESERVED");
        copy.setLockedByUser(userId);
        copy.setLockedUntil(notifiedEntry != null ? notifiedEntry.getExpiresAt() : pickupDateTime.plusHours(48));
        bookCopyRepository.save(copy);
        if (notifiedEntry != null) {
            notifiedEntry.setStatus("FULFILLED");
            waitlistRepository.save(notifiedEntry);
        }

        return BorrowResponseDTO.builder()
                .success(true)
                .message("Đặt giữ trước thành công! Mã phiếu mượn #" + ticket.getTicketId() + ". Vui lòng mang thẻ sinh viên/bạn đọc đến quầy nhận sách trước 17:00 ngày " + pickupDate + ".")
                .ticketId(ticket.getTicketId())
                .ticketType(LoanTicketType.fromDatabaseValue(ticket.getTicketType()).name())
                .status(ticket.getStatus())
                .barcode(copy.getBarcode())
                .pickupDate(pickupDateTime)
                .dueDate(dueDateTime)
                .bookTitle(book.getTitle())
                .build();
    }

    @Transactional(readOnly = true)
    public List<LoanItemDTO> getMyLoans(UUID userId) {
        List<LoanTicketEntity> tickets = loanTicketRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<LoanItemDTO> results = new ArrayList<>();

        for (LoanTicketEntity ticket : tickets) {
            List<LoanDetailEntity> details = loanDetailRepository.findByTicketId(ticket.getTicketId());
            for (LoanDetailEntity detail : details) {
                results.add(toLoanItem(ticket, detail));
            }
        }

        return results;
    }

    @Transactional(readOnly = true)
    public LoanTicketDetailDTO getLoan(Long ticketId) {
        LoanTicketEntity ticket = loanTicketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu mượn #" + ticketId));
        UserEntity reader = ticket.getUserId() == null
                ? null
                : userRepository.findById(ticket.getUserId()).orElse(null);
        List<LoanItemDTO> items = loanDetailRepository.findByTicketId(ticketId).stream()
                .map(detail -> toLoanItem(ticket, detail))
                .toList();

        return LoanTicketDetailDTO.builder()
                .ticketId(ticket.getTicketId())
                .userId(ticket.getUserId())
                .mssv(reader != null ? reader.getMssv() : null)
                .readerName(reader != null ? reader.getFullName() : null)
                .ticketType(LoanTicketType.fromDatabaseValue(ticket.getTicketType()).name())
                .status(LoanTicketStatus.fromDatabaseValue(ticket.getStatus()).name())
                .decisionNote(ticket.getDecisionNote())
                .createdAt(ticket.getCreatedAt())
                .items(items)
                .build();
    }

    @Transactional(readOnly = true)
    public List<LoanTicketSummaryDTO> getStaffLoans() {
        return loanTicketRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toLoanTicketSummary)
                .toList();
    }

    @Transactional
    public LoanItemDTO renewLoan(Long ticketId, UUID userId) {
        return renewLoan(ticketId, null, userId);
    }

    @Transactional
    public LoanItemDTO renewLoan(Long ticketId, Long detailId, UUID userId) {
        LoanTicketEntity ticket = loanTicketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu mượn #" + ticketId));

        if (!ticket.getUserId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền thao tác trên phiếu mượn này");
        }

        LoanTicketStatus ticketStatus = LoanTicketStatus.fromDatabaseValue(ticket.getStatus());
        if (ticketStatus != LoanTicketStatus.BORROWING) {
            throw new RuntimeException("Chỉ phiếu đang mượn mới có thể gia hạn");
        }

        List<LoanDetailEntity> details = loanDetailRepository.findByTicketId(ticketId);
        if (details.isEmpty()) {
            throw new RuntimeException("Không tìm thấy chi tiết mượn sách cho phiếu này");
        }

        List<LoanDetailEntity> activeDetails = details.stream()
                .filter(item -> item.getReturnDate() == null)
                .filter(item -> "BORROWING".equalsIgnoreCase(item.getReturnStatus()))
                .toList();
        LoanDetailEntity detail;
        if (detailId == null) {
            if (activeDetails.size() != 1) {
                throw new RuntimeException("Phiếu có nhiều tài liệu; phải chọn tài liệu cần gia hạn");
            }
            detail = activeDetails.get(0);
        } else {
            detail = activeDetails.stream()
                    .filter(item -> detailId.equals(item.getDetailId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy tài liệu đang mượn trong phiếu"));
        }

        circulationStateService.ensureEligibleForRenewal(userId);

        if (detail.getDueDate() != null && detail.getDueDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Tài liệu đã quá hạn nên không thể gia hạn");
        }

        if (Boolean.TRUE.equals(detail.getIsRecalled())) {
            throw new RuntimeException("Tài liệu này đã có lệnh thu hồi từ thư viện, không thể gia hạn!");
        }

        int currentRenewals = detail.getRenewalCount() != null ? detail.getRenewalCount() : 0;
        if (currentRenewals >= 1) {
            throw new RuntimeException("Tài liệu chỉ được gia hạn tối đa 1 lần. Vui lòng hoàn trả đúng hạn!");
        }

        // Kiểm tra xem cuốn sách này có bạn đọc nào đang chờ trong hàng đợi Waitlist không
        if (detail.getBarcode() != null) {
            var copyOpt = bookCopyRepository.findById(detail.getBarcode());
            if (copyOpt.isPresent() && copyOpt.get().getBookId() != null) {
                Long bookId = copyOpt.get().getBookId();
                List<WaitlistEntity> waitingList = waitlistRepository.findByBookIdAndStatus(bookId, "WAITING");
                if (!waitingList.isEmpty()) {
                    throw new RuntimeException("Tài liệu này hiện đang có bạn đọc khác xếp hàng chờ mượn (Waitlist). Theo quy định thư viện, bạn không thể gia hạn thêm!");
                }
            }
        }

        // Tăng số lần gia hạn và gia hạn thêm 10 ngày
        detail.setRenewalCount(currentRenewals + 1);
        LocalDateTime baseDate = detail.getDueDate() != null ? detail.getDueDate() : LocalDateTime.now();
        detail.setDueDate(baseDate.plusDays(10));
        loanDetailRepository.save(detail);

        return toLoanItem(ticket, detail);
    }

    @Transactional
    public void cancelReservation(Long ticketId, UUID userId) {
        LoanTicketEntity ticket = loanTicketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu mượn #" + ticketId));

        if (!ticket.getUserId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền hủy phiếu mượn này");
        }

        circulationStateService.cancelReservation(ticket, userId);
    }

    private LoanItemDTO toLoanItem(LoanTicketEntity ticket, LoanDetailEntity detail) {
        BookEntity book = null;
        if (detail.getBarcode() != null) {
            var copyOpt = bookCopyRepository.findById(detail.getBarcode());
            if (copyOpt.isPresent() && copyOpt.get().getBookId() != null) {
                book = bookRepository.findById(copyOpt.get().getBookId()).orElse(null);
            }
        }

        Long daysRemaining = detail.getDueDate() == null
                ? null
                : ChronoUnit.DAYS.between(LocalDateTime.now(), detail.getDueDate());
        return LoanItemDTO.builder()
                .ticketId(ticket.getTicketId())
                .detailId(detail.getDetailId())
                .bookId(book != null ? book.getBookId() : null)
                .bookTitle(book != null ? book.getTitle() : "Tài liệu #" + detail.getBarcode())
                .bookCover(book != null
                        ? (book.getImageUrlL() != null ? book.getImageUrlL() : book.getImageUrlM())
                        : null)
                .author(book != null ? book.getAuthor() : null)
                .barcode(detail.getBarcode())
                .ticketType(LoanTicketType.fromDatabaseValue(ticket.getTicketType()).name())
                .status(LoanTicketStatus.fromDatabaseValue(ticket.getStatus()).name())
                .createdAt(ticket.getCreatedAt())
                .dueDate(detail.getDueDate())
                .returnDate(detail.getReturnDate())
                .daysRemaining(daysRemaining)
                .renewalCount(detail.getRenewalCount() != null ? detail.getRenewalCount() : 0)
                .lateFineAccrued(detail.getLateFineAccrued() != null
                        ? detail.getLateFineAccrued()
                        : BigDecimal.ZERO)
                .build();
    }

    private LoanTicketSummaryDTO toLoanTicketSummary(LoanTicketEntity ticket) {
        UserEntity reader = ticket.getUserId() == null
                ? null
                : userRepository.findById(ticket.getUserId()).orElse(null);
        List<LoanDetailEntity> details = loanDetailRepository.findByTicketId(ticket.getTicketId());
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime nearestDueDate = details.stream()
                .map(LoanDetailEntity::getDueDate)
                .filter(java.util.Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        int overdueItemCount = (int) details.stream()
                .filter(detail -> detail.getReturnDate() == null)
                .filter(detail -> detail.getDueDate() != null && detail.getDueDate().isBefore(now))
                .count();
        BigDecimal totalLateFine = details.stream()
                .map(LoanDetailEntity::getLateFineAccrued)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return LoanTicketSummaryDTO.builder()
                .ticketId(ticket.getTicketId())
                .userId(ticket.getUserId())
                .mssv(reader != null ? reader.getMssv() : null)
                .readerName(reader != null ? reader.getFullName() : null)
                .department(reader != null ? reader.getDepartment() : null)
                .cardStatus(reader != null ? reader.getCardStatus() : null)
                .ticketType(LoanTicketType.fromDatabaseValue(ticket.getTicketType()).name())
                .status(LoanTicketStatus.fromDatabaseValue(ticket.getStatus()).name())
                .createdAt(ticket.getCreatedAt())
                .nearestDueDate(nearestDueDate)
                .itemCount(details.size())
                .overdueItemCount(overdueItemCount)
                .totalLateFine(totalLateFine)
                .build();
    }
}
