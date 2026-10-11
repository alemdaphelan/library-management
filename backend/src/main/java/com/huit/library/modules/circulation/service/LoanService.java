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
import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.entity.WaitlistEntity;
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

@Service
public class LoanService {

    private final LoanTicketRepository loanTicketRepository;
    private final LoanDetailRepository loanDetailRepository;
    private final WaitlistRepository waitlistRepository;
    private final BookCopyRepository bookCopyRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    public LoanService(
            LoanTicketRepository loanTicketRepository,
            LoanDetailRepository loanDetailRepository,
            WaitlistRepository waitlistRepository,
            BookCopyRepository bookCopyRepository,
            BookRepository bookRepository,
            UserRepository userRepository) {
        this.loanTicketRepository = loanTicketRepository;
        this.loanDetailRepository = loanDetailRepository;
        this.waitlistRepository = waitlistRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public BorrowResponseDTO reserveOrBorrowBook(BorrowRequestDTO request, UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin người dùng trong hệ thống"));

        if (Boolean.TRUE.equals(user.getDisciplineFlag()) || "LOCKED".equalsIgnoreCase(user.getCardStatus())) {
            throw new RuntimeException("Tài khoản của bạn đang bị khóa hoặc hạn chế mượn sách. Vui lòng liên hệ thủ thư!");
        }

        BookEntity book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cuốn sách này trong cơ sở dữ liệu"));

        // Check if physical copies exist; if none exist yet for this book, seed initial copies
        List<BookCopyEntity> allCopies = bookCopyRepository.findByBookId(book.getBookId());
        if (allCopies.isEmpty()) {
            for (int i = 1; i <= 3; i++) {
                BookCopyEntity newCopy = new BookCopyEntity();
                newCopy.setBarcode("BC-" + book.getBookId() + "-" + String.format("%03d", i));
                newCopy.setBookId(book.getBookId());
                newCopy.setStatus("AVAILABLE");
                newCopy.setConditionNote("Sách mới - Lưu kho Thư viện HUIT");
                bookCopyRepository.save(newCopy);
                allCopies.add(newCopy);
            }
        }

        // Find available copy
        List<BookCopyEntity> availableCopies = allCopies.stream()
                .filter(c -> "AVAILABLE".equalsIgnoreCase(c.getStatus()))
                .toList();

        // If no available copies, join waitlist
        if (availableCopies.isEmpty()) {
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
        BookCopyEntity copy = availableCopies.get(0);

        // Calculate pickup and due dates
        LocalDate pickupDate = request.getPickupDate() != null ? request.getPickupDate() : LocalDate.now().plusDays(1);
        LocalDateTime pickupDateTime = pickupDate.atTime(17, 0);
        LocalDateTime dueDateTime;
        if ("ONSITE".equalsIgnoreCase(request.getTicketType())) {
            dueDateTime = pickupDate.atTime(21, 0); // Đọc tại chỗ trong ngày
        } else {
            dueDateTime = pickupDateTime.plusDays(14); // Mượn về nhà 14 ngày
        }

        // Create LoanTicket
        LoanTicketEntity ticket = new LoanTicketEntity();
        ticket.setUserId(userId);
        ticket.setTicketType(request.getTicketType() != null ? request.getTicketType() : "BORROW");
        ticket.setStatus("PENDING");
        ticket = loanTicketRepository.save(ticket);

        // Create LoanDetail
        LoanDetailEntity detail = new LoanDetailEntity();
        detail.setTicketId(ticket.getTicketId());
        detail.setBarcode(copy.getBarcode());
        detail.setCheckoutConditionNote(request.getNote() != null && !request.getNote().isBlank()
                ? request.getNote()
                : "Đặt giữ trước qua cổng trực tuyến OPAC");
        detail.setReturnStatus("PENDING");
        detail.setDueDate(dueDateTime);
        loanDetailRepository.save(detail);

        // Lock / Reserve book copy
        copy.setStatus("RESERVED");
        copy.setLockedByUser(userId);
        copy.setLockedUntil(pickupDateTime.plusDays(2));
        bookCopyRepository.save(copy);

        return BorrowResponseDTO.builder()
                .success(true)
                .message("Đặt giữ trước thành công! Mã phiếu mượn #" + ticket.getTicketId() + ". Vui lòng mang thẻ sinh viên/bạn đọc đến quầy nhận sách trước 17:00 ngày " + pickupDate + ".")
                .ticketId(ticket.getTicketId())
                .ticketType(ticket.getTicketType())
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
                // Find book through copy
                BookEntity book = null;
                if (detail.getBarcode() != null) {
                    var copyOpt = bookCopyRepository.findById(detail.getBarcode());
                    if (copyOpt.isPresent() && copyOpt.get().getBookId() != null) {
                        book = bookRepository.findById(copyOpt.get().getBookId()).orElse(null);
                    }
                }

                Long daysRemaining = null;
                if (detail.getDueDate() != null) {
                    daysRemaining = ChronoUnit.DAYS.between(LocalDateTime.now(), detail.getDueDate());
                }

                results.add(LoanItemDTO.builder()
                        .ticketId(ticket.getTicketId())
                        .detailId(detail.getDetailId())
                        .bookId(book != null ? book.getBookId() : null)
                        .bookTitle(book != null ? book.getTitle() : "Tài liệu #" + detail.getBarcode())
                        .bookCover(book != null ? (book.getImageUrlL() != null ? book.getImageUrlL() : book.getImageUrlM()) : null)
                        .author(book != null ? book.getAuthor() : null)
                        .barcode(detail.getBarcode())
                        .ticketType(ticket.getTicketType())
                        .status(ticket.getStatus())
                        .createdAt(ticket.getCreatedAt())
                        .dueDate(detail.getDueDate())
                        .returnDate(detail.getReturnDate())
                        .daysRemaining(daysRemaining)
                        .renewalCount(detail.getRenewalCount() != null ? detail.getRenewalCount() : 0)
                        .build());
            }
        }

        return results;
    }

    @Transactional
    public LoanItemDTO renewLoan(Long ticketId, UUID userId) {
        LoanTicketEntity ticket = loanTicketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu mượn #" + ticketId));

        if (!ticket.getUserId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền thao tác trên phiếu mượn này");
        }

        if ("RETURNED".equalsIgnoreCase(ticket.getStatus()) || "CANCELLED".equalsIgnoreCase(ticket.getStatus())) {
            throw new RuntimeException("Phiếu mượn đã hoàn tất hoặc đã bị hủy, không thể gia hạn!");
        }

        List<LoanDetailEntity> details = loanDetailRepository.findByTicketId(ticketId);
        if (details.isEmpty()) {
            throw new RuntimeException("Không tìm thấy chi tiết mượn sách cho phiếu này");
        }

        LoanDetailEntity detail = details.get(0);

        if (Boolean.TRUE.equals(detail.getIsRecalled())) {
            throw new RuntimeException("Tài liệu này đã có lệnh thu hồi từ thư viện, không thể gia hạn!");
        }

        int currentRenewals = detail.getRenewalCount() != null ? detail.getRenewalCount() : 0;
        if (currentRenewals >= 2) {
            throw new RuntimeException("Bạn đã đạt giới hạn gia hạn tối đa (2/2 lần). Vui lòng hoàn trả tài liệu đúng hạn!");
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

        // Tăng số lần gia hạn và gia hạn thêm 7 ngày
        detail.setRenewalCount(currentRenewals + 1);
        LocalDateTime baseDate = detail.getDueDate() != null ? detail.getDueDate() : LocalDateTime.now();
        if (baseDate.isBefore(LocalDateTime.now())) {
            detail.setDueDate(LocalDateTime.now().plusDays(7));
        } else {
            detail.setDueDate(baseDate.plusDays(7));
        }
        loanDetailRepository.save(detail);

        BookEntity book = null;
        if (detail.getBarcode() != null) {
            var copyOpt = bookCopyRepository.findById(detail.getBarcode());
            if (copyOpt.isPresent() && copyOpt.get().getBookId() != null) {
                book = bookRepository.findById(copyOpt.get().getBookId()).orElse(null);
            }
        }

        Long daysRemaining = ChronoUnit.DAYS.between(LocalDateTime.now(), detail.getDueDate());

        return LoanItemDTO.builder()
                .ticketId(ticket.getTicketId())
                .detailId(detail.getDetailId())
                .bookId(book != null ? book.getBookId() : null)
                .bookTitle(book != null ? book.getTitle() : "Tài liệu #" + detail.getBarcode())
                .bookCover(book != null ? (book.getImageUrlL() != null ? book.getImageUrlL() : book.getImageUrlM()) : null)
                .author(book != null ? book.getAuthor() : null)
                .barcode(detail.getBarcode())
                .ticketType(ticket.getTicketType())
                .status(ticket.getStatus())
                .createdAt(ticket.getCreatedAt())
                .dueDate(detail.getDueDate())
                .returnDate(detail.getReturnDate())
                .daysRemaining(daysRemaining)
                .renewalCount(detail.getRenewalCount())
                .build();
    }

    @Transactional
    public void cancelReservation(Long ticketId, UUID userId) {
        LoanTicketEntity ticket = loanTicketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu mượn #" + ticketId));

        if (!ticket.getUserId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền hủy phiếu mượn này");
        }

        if (!"PENDING".equalsIgnoreCase(ticket.getStatus()) && !"RESERVED".equalsIgnoreCase(ticket.getStatus())) {
            throw new RuntimeException("Chỉ có thể hủy phiếu mượn khi đang ở trạng thái Chờ xử lý hoặc Đặt giữ");
        }

        ticket.setStatus("CANCELLED");
        loanTicketRepository.save(ticket);

        // Unlock copy
        List<LoanDetailEntity> details = loanDetailRepository.findByTicketId(ticketId);
        for (LoanDetailEntity detail : details) {
            detail.setReturnStatus("CANCELLED");
            loanDetailRepository.save(detail);

            if (detail.getBarcode() != null) {
                bookCopyRepository.findById(detail.getBarcode()).ifPresent(copy -> {
                    copy.setStatus("AVAILABLE");
                    copy.setLockedByUser(null);
                    copy.setLockedUntil(null);
                    bookCopyRepository.save(copy);
                });
            }
        }
    }
}
