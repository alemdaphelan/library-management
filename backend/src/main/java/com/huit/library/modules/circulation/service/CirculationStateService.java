package com.huit.library.modules.circulation.service;

import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.circulation.dto.CheckoutRequestDTO;
import com.huit.library.modules.circulation.dto.BorrowEligibilityDTO;
import com.huit.library.modules.circulation.dto.CirculationOperationResponseDTO;
import com.huit.library.modules.circulation.dto.LoanStateResponseDTO;
import com.huit.library.modules.circulation.dto.ReturnRequestDTO;
import com.huit.library.modules.circulation.dto.ManualCheckoutRequestDTO;
import com.huit.library.modules.circulation.entity.CirculationOperationEntity;
import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanStatusHistoryEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.model.LoanDetailStatus;
import com.huit.library.modules.circulation.model.LoanTicketStatus;
import com.huit.library.modules.circulation.model.LoanTicketType;
import com.huit.library.modules.circulation.repository.CirculationOperationRepository;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanStatusHistoryRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.penalty.service.LateFineService;
import com.huit.library.modules.notification.service.NotificationService;
import com.huit.library.modules.policy.service.LibraryPolicyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CirculationStateService {
    private final LoanTicketRepository loanTicketRepository;
    private final LoanDetailRepository loanDetailRepository;
    private final LoanStatusHistoryRepository historyRepository;
    private final BookCopyRepository bookCopyRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final CirculationOperationRepository operationRepository;
    private final CirculationStateMachine stateMachine;
    private final LateFineService lateFineService;
    private final WaitlistService waitlistService;
    private final NotificationService notificationService;
    private final LibraryHoursService libraryHoursService;
    private final LibraryPolicyService policyService;

    public CirculationStateService(
            LoanTicketRepository loanTicketRepository,
            LoanDetailRepository loanDetailRepository,
            LoanStatusHistoryRepository historyRepository,
            BookCopyRepository bookCopyRepository,
            UserRepository userRepository,
            WalletRepository walletRepository,
            CirculationOperationRepository operationRepository,
            CirculationStateMachine stateMachine,
            LateFineService lateFineService,
            WaitlistService waitlistService,
            NotificationService notificationService,
            LibraryHoursService libraryHoursService,
            LibraryPolicyService policyService) {
        this.loanTicketRepository = loanTicketRepository;
        this.loanDetailRepository = loanDetailRepository;
        this.historyRepository = historyRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.operationRepository = operationRepository;
        this.stateMachine = stateMachine;
        this.lateFineService = lateFineService;
        this.waitlistService = waitlistService;
        this.notificationService = notificationService;
        this.libraryHoursService = libraryHoursService;
        this.policyService = policyService;
    }

    @Transactional
    public LoanStateResponseDTO approveReservation(Long ticketId, UUID operatorId) {
        // Compatibility endpoint for old clients and tickets. Approval is now automatic;
        // the authenticated caller triggers validation, not a discretionary staff decision.
        LoanTicketEntity ticket = findTicket(ticketId);
        LoanTicketStatus currentStatus = LoanTicketStatus.fromDatabaseValue(ticket.getStatus());
        if (currentStatus != LoanTicketStatus.PENDING && currentStatus != LoanTicketStatus.RESERVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể chuyển phiếu mượn từ " + currentStatus + " sang " + LoanTicketStatus.RESERVED);
        }
        ensureNoDebtOrOverdue(ticket.getUserId());
        ensureCurrentOpenItemsWithinLimit(ticket.getUserId());
        List<LoanDetailEntity> details = loanDetailRepository.findByTicketIdForUpdate(ticketId);
        if (details.isEmpty() || details.size() > policyService.maximumOpenItems()
                || details.stream().anyMatch(detail -> detail.getBarcode() == null
                    || detail.getReturnDate() != null
                    || LoanDetailStatus.fromDatabaseValue(detail.getReturnStatus()) != LoanDetailStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phiếu cũ không còn đủ tài liệu hợp lệ để giữ chỗ");
        }
        List<String> barcodes = details.stream().map(LoanDetailEntity::getBarcode).sorted().toList();
        var copies = bookCopyRepository.findAllByBarcodeInForUpdate(barcodes);
        LocalDateTime now = LocalDateTime.now();
        if (new LinkedHashSet<>(barcodes).size() != details.size() || copies.size() != details.size()
                || copies.stream().anyMatch(copy -> !"RESERVED".equalsIgnoreCase(copy.getStatus())
                    || !Objects.equals(copy.getLockedByUser(), ticket.getUserId())
                    || copy.getLockedUntil() == null || !copy.getLockedUntil().isAfter(now))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Giữ chỗ đã hết hạn hoặc bản sao không còn được giữ cho phiếu");
        }
        if (currentStatus == LoanTicketStatus.RESERVED) {
            return LoanStateResponseDTO.builder()
                    .ticketId(ticketId).previousStatus(currentStatus).status(currentStatus)
                    .message("Phiếu đã được hệ thống giữ chỗ; có thể giao sách tại quầy.")
                    .changedAt(now).build();
        }
        return autoApproveReservation(ticket);
    }

    /** Called inside the reservation transaction after eligibility and copy locks are established. */
    @Transactional
    public LoanStateResponseDTO autoApproveReservation(LoanTicketEntity ticket) {
        LoanStateResponseDTO response = transition(
                ticket, LoanTicketStatus.RESERVED, null, "Hệ thống tự động duyệt và giữ chỗ");
        notificationService.createOnce(
                ticket.getUserId(),
                "LOAN_APPROVED",
                "Yêu cầu mượn #" + ticket.getTicketId()
                        + " đã được hệ thống tự động giữ chỗ. Vui lòng đến quầy nhận tài liệu trong thời hạn giữ chỗ.",
                "LOAN_TICKET",
                ticket.getTicketId().toString(),
                "LOAN_APPROVED:" + ticket.getTicketId(),
                LocalDateTime.now());
        return response;
    }

    @Transactional
    public LoanStateResponseDTO rejectReservation(Long ticketId, UUID operatorId, String reason) {
        LoanTicketEntity ticket = findTicket(ticketId);
        LoanStateResponseDTO response = transition(ticket, LoanTicketStatus.REJECTED, operatorId, reason.trim());
        releaseReservedCopies(ticket, LoanDetailStatus.CANCELLED);
        notificationService.createOnce(
                ticket.getUserId(),
                "LOAN_REJECTED",
                "Yêu cầu mượn #" + ticket.getTicketId() + " bị từ chối. Lý do: " + reason.trim(),
                "LOAN_TICKET",
                ticket.getTicketId().toString(),
                "LOAN_REJECTED:" + ticket.getTicketId(),
                LocalDateTime.now());
        return response;
    }

    @Transactional
    public LoanStateResponseDTO cancelReservation(LoanTicketEntity ticket, UUID actorId) {
        LoanStateResponseDTO response = transition(
                ticket, LoanTicketStatus.CANCELLED, actorId, "Độc giả hủy yêu cầu đặt giữ");
        releaseReservedCopies(ticket, LoanDetailStatus.CANCELLED);
        return response;
    }

    public void ensureEligibleForReservation(UUID userId) {
        ensureEligibleForReservation(userId, 1);
    }

    public void ensureEligibleForReservation(UUID userId, int requestedItems) {
        int maximumOpenItems = policyService.maximumOpenItems();
        if (requestedItems < 1 || requestedItems > maximumOpenItems) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mỗi phiếu phải có từ 1 đến " + maximumOpenItems + " tài liệu");
        }
        ensureNoDebtOrOverdue(userId);
        long openItems = loanDetailRepository.countOpenItemsByUserId(
                userId,
                List.of("PENDING", "RESERVED", "BORROWING", "OVERDUE"));
        if (openItems + requestedItems > maximumOpenItems) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Yêu cầu có " + requestedItems + " tài liệu nhưng bạn đọc hiện đã có "
                            + openItems + "; mỗi bạn đọc được đặt giữ hoặc mượn tối đa "
                            + maximumOpenItems + " tài liệu");
        }
    }

    @Transactional(readOnly = true)
    public BorrowEligibilityDTO getBorrowEligibility(UUID userId) {
        int maximumOpenItems = policyService.maximumOpenItems();
        long openItems = countOpenItems(userId);
        long remainingSlots = Math.max(0, maximumOpenItems - openItems);
        String message = remainingSlots > 0
                ? "Bạn còn có thể đăng ký " + remainingSlots + " tài liệu."
                : "Bạn đã đủ " + maximumOpenItems
                        + " tài liệu đang chờ duyệt, chờ nhận hoặc đang mượn. "
                        + "Hãy trả hoặc hủy một tài liệu trước khi đăng ký tiếp.";
        return BorrowEligibilityDTO.builder()
                .maximumItems(maximumOpenItems)
                .openItems(openItems)
                .remainingSlots(remainingSlots)
                .eligible(remainingSlots > 0)
                .message(message)
                .build();
    }

    private void ensureCurrentOpenItemsWithinLimit(UUID userId) {
        int maximumOpenItems = policyService.maximumOpenItems();
        long openItems = countOpenItems(userId);
        if (openItems > maximumOpenItems) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bạn đọc hiện có " + openItems
                            + " tài liệu trong các phiếu đang hoạt động, vượt giới hạn "
                            + maximumOpenItems + " tài liệu; không thể duyệt thêm");
        }
    }

    private long countOpenItems(UUID userId) {
        return loanDetailRepository.countOpenItemsByUserId(
                userId,
                List.of("PENDING", "RESERVED", "BORROWING", "OVERDUE"));
    }

    public void ensureEligibleForRenewal(UUID userId) {
        ensureNoDebtOrOverdue(userId);
    }

    @Transactional
    public CirculationOperationResponseDTO checkout(CheckoutRequestDTO request, UUID operatorId) {
        libraryHoursService.requireCounterOpen("giao sách");
        List<String> barcodes = normalizeBarcodes(request.getBarcodes());
        String requestHash = requestHash(
                "CHECKOUT", request.getTicketId().toString(), String.join(",", barcodes));

        LoanTicketEntity ticket = loanTicketRepository.findByIdForUpdate(request.getTicketId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy phiếu mượn #" + request.getTicketId()));

        CirculationOperationResponseDTO replay = replayIfPresent(request.getIdempotencyKey(), requestHash);
        if (replay != null) {
            return replay;
        }

        LoanTicketStatus status = LoanTicketStatus.fromDatabaseValue(ticket.getStatus());
        if (status != LoanTicketStatus.RESERVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ phiếu RESERVED đã được duyệt mới có thể giao sách");
        }

        List<LoanDetailEntity> details = loanDetailRepository.findByTicketIdForUpdate(ticket.getTicketId());
        Map<String, LoanDetailEntity> detailsByBarcode = details.stream()
                .filter(detail -> detail.getBarcode() != null)
                .filter(detail -> LoanDetailStatus.fromDatabaseValue(detail.getReturnStatus()) == LoanDetailStatus.PENDING)
                .collect(Collectors.toMap(LoanDetailEntity::getBarcode, Function.identity()));
        if (!detailsByBarcode.keySet().equals(new LinkedHashSet<>(barcodes))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Danh sách barcode phải khớp toàn bộ tài liệu đang chờ giao của phiếu");
        }

        ensureNoDebtOrOverdue(ticket.getUserId());
        long borrowedItems = loanDetailRepository.countOpenItemsByUserId(
                ticket.getUserId(), List.of("BORROWING", "OVERDUE"));
        int maximumOpenItems = policyService.maximumOpenItems();
        if (borrowedItems + barcodes.size() > maximumOpenItems) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Checkout vượt giới hạn tối đa " + maximumOpenItems + " tài liệu đang mượn");
        }

        List<com.huit.library.modules.catalog.entity.BookCopyEntity> copies =
                bookCopyRepository.findAllByBarcodeInForUpdate(barcodes);
        if (copies.size() != barcodes.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đầy đủ bản sao cần checkout");
        }
        for (var copy : copies) {
            if (!"RESERVED".equalsIgnoreCase(copy.getStatus())
                    || !Objects.equals(copy.getLockedByUser(), ticket.getUserId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Bản sao " + copy.getBarcode() + " không còn được giữ cho phiếu này");
            }
        }

        LocalDateTime checkoutAt = LocalDateTime.now();
        LocalDateTime dueAt = LoanTicketType.fromDatabaseValue(ticket.getTicketType()) == LoanTicketType.ONSITE
                ? checkoutAt.toLocalDate().atTime(libraryHoursService.closingTime(checkoutAt.toLocalDate()))
                : checkoutAt.plusDays(policyService.homeLoanDays());
        if (dueAt.isBefore(checkoutAt)) {
            dueAt = checkoutAt.plusHours(1);
        }

        transition(ticket, LoanTicketStatus.BORROWING, operatorId, "Đã giao sách tại quầy");
        for (LoanDetailEntity detail : detailsByBarcode.values()) {
            detail.setCheckoutAt(checkoutAt);
            detail.setCheckoutTimeSource("LIVE_CHECKOUT");
            detail.setCirculationBookId(copies.stream().filter(c -> c.getBarcode().equals(detail.getBarcode())).findFirst().orElseThrow().getBookId());
            detail.setReturnStatus(LoanDetailStatus.BORROWING.name());
            detail.setDueDate(dueAt);
            loanDetailRepository.save(detail);
        }
        for (var copy : copies) {
            copy.setStatus("BORROWING");
            copy.setLockedByUser(null);
            copy.setLockedUntil(null);
            bookCopyRepository.save(copy);
        }

        return recordOperation(
                request.getIdempotencyKey(), requestHash, "CHECKOUT",
                List.of(ticket.getTicketId()), barcodes, "BORROWING",
                "Checkout thành công " + barcodes.size() + " tài liệu; hạn trả " + dueAt.toLocalDate());
    }

    @Transactional
    public CirculationOperationResponseDTO manualCheckout(ManualCheckoutRequestDTO request, UUID operatorId) {
        libraryHoursService.requireCounterOpen("lập phiếu mượn tại quầy");
        List<String> barcodes = normalizeBarcodes(request.getBarcodes());
        LoanTicketType ticketType;
        try {
            ticketType = LoanTicketType.fromDatabaseValue(request.getTicketType());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loại phiếu không hợp lệ");
        }
        String mssv = request.getMssv().trim();
        String requestHash = requestHash(
                "MANUAL_CHECKOUT", mssv, ticketType.name(), String.join(",", barcodes));
        CirculationOperationResponseDTO replay = replayIfPresent(request.getIdempotencyKey(), requestHash);
        if (replay != null) {
            return replay;
        }

        var reader = userRepository.findByMssv(mssv)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy bạn đọc có MSSV " + mssv));
        ensureEligibleForReservation(reader.getUserId(), barcodes.size());

        List<com.huit.library.modules.catalog.entity.BookCopyEntity> copies =
                bookCopyRepository.findAllByBarcodeInForUpdate(barcodes);
        if (copies.size() != barcodes.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đầy đủ bản sao cần mượn");
        }
        for (var copy : copies) {
            if (!"AVAILABLE".equalsIgnoreCase(copy.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Bản sao " + copy.getBarcode() + " hiện không sẵn sàng để mượn");
            }
            if (loanDetailRepository.countOpenItemsByUserIdAndBookId(reader.getUserId(), copy.getBookId()) > 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Bạn đọc đã có đầu sách của bản sao " + copy.getBarcode() + " trong phiếu đang hoạt động");
            }
        }
        if (copies.stream().map(com.huit.library.modules.catalog.entity.BookCopyEntity::getBookId).distinct().count()
                != copies.size()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Một phiếu không được mượn nhiều bản sao của cùng một đầu sách");
        }

        LoanTicketEntity ticket = new LoanTicketEntity();
        ticket.setUserId(reader.getUserId());
        ticket.setTicketType(ticketType.name());
        ticket.setStatus(LoanTicketStatus.RESERVED.name());
        ticket.setProcessedBy(operatorId);
        ticket.setDecisionNote("Lập phiếu mượn trực tiếp tại quầy");
        ticket = loanTicketRepository.save(ticket);

        LocalDateTime checkoutAt = LocalDateTime.now();
        LocalDateTime dueAt = ticketType == LoanTicketType.ONSITE
                ? checkoutAt.toLocalDate().atTime(libraryHoursService.closingTime(checkoutAt.toLocalDate()))
                : checkoutAt.plusDays(policyService.homeLoanDays());
        if (dueAt.isBefore(checkoutAt)) {
            dueAt = checkoutAt.plusHours(1);
        }
        String conditionNote = request.getNote() == null || request.getNote().isBlank()
                ? "Mượn trực tiếp tại quầy"
                : request.getNote().trim();
        for (var copy : copies) {
            LoanDetailEntity detail = new LoanDetailEntity();
            detail.setTicketId(ticket.getTicketId());
            detail.setBarcode(copy.getBarcode());
            detail.setCheckoutConditionNote(conditionNote);
            detail.setCheckoutAt(checkoutAt);
            detail.setCheckoutTimeSource("LIVE_CHECKOUT");
            detail.setCirculationBookId(copy.getBookId());
            detail.setReturnStatus(LoanDetailStatus.BORROWING.name());
            detail.setDueDate(dueAt);
            loanDetailRepository.save(detail);

            copy.setStatus("BORROWING");
            copy.setLockedByUser(null);
            copy.setLockedUntil(null);
            bookCopyRepository.save(copy);
        }
        transition(ticket, LoanTicketStatus.BORROWING, operatorId, "Đã lập phiếu và giao sách tại quầy");

        return recordOperation(
                request.getIdempotencyKey(), requestHash, "CHECKOUT",
                List.of(ticket.getTicketId()), barcodes, "BORROWING",
                "Đã lập phiếu #" + ticket.getTicketId() + " và cho mượn " + barcodes.size()
                        + " tài liệu; hạn trả " + dueAt);
    }

    @Transactional
    public CirculationOperationResponseDTO returnBooks(ReturnRequestDTO request, UUID operatorId) {
        libraryHoursService.requireCounterOpen("nhận trả sách");
        List<String> barcodes = normalizeBarcodes(request.getBarcodes());
        String note = request.getReturnConditionNote() == null ? "" : request.getReturnConditionNote().trim();
        String requestHash = requestHash("RETURN", String.join(",", barcodes), note);

        CirculationOperationResponseDTO replay = replayIfPresent(request.getIdempotencyKey(), requestHash);
        if (replay != null) {
            return replay;
        }

        // Discover identifiers without loading stale mutable entities before waiting for locks.
        var targets = loanDetailRepository.findActiveReturnTargets(barcodes);
        if (targets.size() != barcodes.size()) {
            replay = replayIfPresent(request.getIdempotencyKey(), requestHash);
            if (replay != null) return replay;
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Một hoặc nhiều barcode không có lượt mượn đang hoạt động");
        }
        List<Long> ticketIds = targets.stream().map(com.huit.library.modules.circulation.dto.ReturnTarget::ticketId)
                .distinct().sorted().toList();
        Map<Long, LoanTicketEntity> tickets = new java.util.LinkedHashMap<>();
        for (Long ticketId : ticketIds) tickets.put(ticketId, findTicket(ticketId));
        replay = replayIfPresent(request.getIdempotencyKey(), requestHash);
        if (replay != null) return replay;

        List<LoanDetailEntity> activeDetails = new java.util.ArrayList<>();
        for (Long ticketId : ticketIds) {
            if (!List.of("BORROWING", "OVERDUE").contains(tickets.get(ticketId).getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Phiếu không ở trạng thái đang mượn");
            }
            loanDetailRepository.findByTicketIdForUpdate(ticketId).stream()
                    .filter(detail -> barcodes.contains(detail.getBarcode()) && detail.getReturnDate() == null
                            && List.of("BORROWING", "OVERDUE").contains(detail.getReturnStatus()))
                    .forEach(activeDetails::add);
        }
        if (activeDetails.size() != barcodes.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tài liệu đã được xử lý bởi giao dịch khác");
        }
        Map<String, LoanDetailEntity> detailsByBarcode = activeDetails.stream()
                .collect(Collectors.toMap(LoanDetailEntity::getBarcode, Function.identity()));
        List<BookCopyEntity> copies = bookCopyRepository.findAllByBarcodeInForUpdate(barcodes);
        if (copies.size() != barcodes.size() || copies.stream().anyMatch(copy ->
                !detailsByBarcode.containsKey(copy.getBarcode())
                || !List.of("BORROWING", "OVERDUE").contains(copy.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bản sao không ở trạng thái đang mượn");
        }

        LocalDateTime returnedAt = LocalDateTime.now();
        BigDecimal totalAdditionalFine = BigDecimal.ZERO;
        for (BookCopyEntity copy : copies) {
            LoanDetailEntity detail = detailsByBarcode.get(copy.getBarcode());
            totalAdditionalFine = totalAdditionalFine.add(receiveNormalReturn(
                    tickets.get(detail.getTicketId()), detail, copy, note, returnedAt));
        }
        completeReturnedTickets(tickets.values().stream().toList(), operatorId);

        return recordOperation(
                request.getIdempotencyKey(), requestHash, "RETURN",
                ticketIds, barcodes, "RETURNED",
                "Trả thành công " + barcodes.size() + " tài liệu"
                        + (totalAdditionalFine.signum() > 0
                        ? "; phát sinh phí trễ " + totalAdditionalFine.toPlainString() + " đồng"
                        : ""));
    }

    /** Caller has locked the ticket, details and copy in that order inside a transaction. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public BigDecimal receiveNormalReturn(LoanTicketEntity ticket, LoanDetailEntity detail,
            BookCopyEntity copy, String note, LocalDateTime returnedAt) {
        BigDecimal additionalFine = lateFineService.accrueLateFine(ticket, detail, returnedAt);
        detail.setReturnStatus(LoanDetailStatus.RETURNED.name());
        detail.setReturnDate(returnedAt);
        detail.setPhysicalReturnAt(returnedAt);
        detail.setPhysicalReturnTimeSource("LIVE_COUNTER_RETURN");
        detail.setReturnConditionNote(note == null || note.isBlank() ? null : note.trim());
        loanDetailRepository.save(detail);
        copy.setStatus("AVAILABLE");
        copy.setLockedByUser(null);
        copy.setLockedUntil(null);
        if (note != null && !note.isBlank()) copy.setConditionNote(note.trim());
        bookCopyRepository.save(copy);
        waitlistService.promoteNext(copy, returnedAt);
        return additionalFine;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void completeReturnedTickets(List<LoanTicketEntity> tickets, UUID operatorId) {
        for (LoanTicketEntity ticket : tickets) {
            boolean completed = loanDetailRepository.findByTicketIdForUpdate(ticket.getTicketId()).stream()
                    .allMatch(detail -> List.of("RETURNED", "CANCELLED", "SETTLED").contains(detail.getReturnStatus()));
            if (completed && !"RETURNED".equals(ticket.getStatus())) {
                transition(ticket, LoanTicketStatus.RETURNED, operatorId, "Đã nhận đủ sách trả tại quầy");
            }
        }
    }

    private LoanStateResponseDTO transition(
            LoanTicketEntity ticket,
            LoanTicketStatus target,
            UUID actorId,
            String reason) {
        LoanTicketStatus previous = stateMachine.transition(ticket, target);
        ticket.setProcessedBy(actorId);
        ticket.setDecisionNote(reason);
        loanTicketRepository.save(ticket);

        LoanStatusHistoryEntity history = new LoanStatusHistoryEntity();
        history.setTicketId(ticket.getTicketId());
        history.setFromStatus(previous);
        history.setToStatus(target);
        history.setChangedBy(actorId);
        history.setReason(reason);
        historyRepository.save(history);

        return LoanStateResponseDTO.builder()
                .ticketId(ticket.getTicketId())
                .previousStatus(previous)
                .status(target)
                .message(reason)
                .changedAt(LocalDateTime.now())
                .build();
    }

    private LoanTicketEntity findTicket(Long ticketId) {
        return loanTicketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy phiếu mượn #" + ticketId));
    }

    private void ensureNoDebtOrOverdue(UUID userId) {
        var user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy bạn đọc"));
        if (Boolean.TRUE.equals(user.getDisciplineFlag())
                || "LOCKED".equalsIgnoreCase(user.getCardStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Thẻ thư viện đang bị khóa");
        }
        LocalDateTime now = LocalDateTime.now();
        if (user.getCardStatus() != null && !"ACTIVE".equalsIgnoreCase(user.getCardStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Thẻ thư viện không ở trạng thái hoạt động");
        }
        if (user.getCardExpiryDate() != null && user.getCardExpiryDate().isBefore(now)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Thẻ thư viện đã hết hạn");
        }
        boolean hasDebt = walletRepository.findByUserId(userId)
                .map(wallet -> wallet.getTotalDebt() != null && wallet.getTotalDebt().signum() > 0)
                .orElse(false);
        long overdueItems = loanDetailRepository.countOverdueItemsByUserId(userId, now);
        if (user.getCirculationLockedUntil() != null) {
            if (user.getCirculationLockedUntil().isAfter(now)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Quyền mượn đang bị khóa đến " + user.getCirculationLockedUntil());
            }
            long openBorrowedItems = loanDetailRepository.countOpenItemsByUserId(
                    userId, List.of("BORROWING", "OVERDUE"));
            if (hasDebt || openBorrowedItems > 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Đã hết thời hạn khóa nhưng chỉ mở quyền sau khi trả hết sách và công nợ");
            }
            user.setCirculationLockedUntil(null);
            user.setCirculationLockReason(null);
            userRepository.save(user);
            notificationService.createOnce(
                    userId, "CIRCULATION_UNLOCKED",
                    "Quyền mượn, đặt giữ và gia hạn đã được khôi phục.",
                    "USER", userId.toString(),
                    "CIRCULATION_UNLOCKED:" + now.toLocalDate(), now);
        }
        if (hasDebt) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Bạn đọc còn nợ chưa xử lý nên không thể mượn thêm");
        }
        if (overdueItems > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Bạn đọc đang có tài liệu quá hạn nên không thể mượn thêm");
        }
    }

    private List<String> normalizeBarcodes(List<String> rawBarcodes) {
        List<String> normalized = rawBarcodes.stream()
                .map(String::trim)
                .sorted(Comparator.naturalOrder())
                .toList();
        if (new LinkedHashSet<>(normalized).size() != normalized.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách barcode bị trùng");
        }
        return normalized;
    }

    private CirculationOperationResponseDTO replayIfPresent(String idempotencyKey, String requestHash) {
        return operationRepository.findByIdempotencyKey(idempotencyKey.trim())
                .map(operation -> {
                    if (!operation.getRequestHash().equals(requestHash)) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Idempotency key đã được dùng cho một yêu cầu khác");
                    }
                    return toOperationResponse(operation, true);
                })
                .orElse(null);
    }

    private CirculationOperationResponseDTO recordOperation(
            String idempotencyKey,
            String requestHash,
            String operationType,
            List<Long> ticketIds,
            List<String> barcodes,
            String status,
            String message) {
        CirculationOperationEntity operation = new CirculationOperationEntity();
        operation.setOperationId(UUID.randomUUID());
        operation.setIdempotencyKey(idempotencyKey.trim());
        operation.setOperationType(operationType);
        operation.setRequestHash(requestHash);
        operation.setTicketIds(ticketIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        operation.setBarcodes(String.join(",", barcodes));
        operation.setResultStatus(status);
        operation.setMessage(message);
        return toOperationResponse(operationRepository.saveAndFlush(operation), false);
    }

    private CirculationOperationResponseDTO toOperationResponse(
            CirculationOperationEntity operation,
            boolean replayed) {
        List<Long> ticketIds = operation.getTicketIds().isBlank()
                ? List.of()
                : java.util.Arrays.stream(operation.getTicketIds().split(","))
                        .map(Long::valueOf)
                        .toList();
        List<String> barcodes = operation.getBarcodes().isBlank()
                ? List.of()
                : List.of(operation.getBarcodes().split(","));
        return CirculationOperationResponseDTO.builder()
                .operationId(operation.getOperationId())
                .operationType(operation.getOperationType())
                .ticketIds(ticketIds)
                .barcodes(barcodes)
                .status(operation.getResultStatus())
                .message(operation.getMessage())
                .processedAt(operation.getProcessedAt())
                .replayed(replayed)
                .build();
    }

    private String requestHash(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                digest.update(value.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void releaseReservedCopies(LoanTicketEntity ticket, LoanDetailStatus detailStatus) {
        List<LoanDetailEntity> details = loanDetailRepository.findByTicketIdForUpdate(ticket.getTicketId());
        List<String> barcodes = details.stream()
                .map(LoanDetailEntity::getBarcode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, com.huit.library.modules.catalog.entity.BookCopyEntity> copies = barcodes.isEmpty()
                ? Map.of()
                : bookCopyRepository.findAllByBarcodeInForUpdate(barcodes).stream()
                        .collect(Collectors.toMap(
                                com.huit.library.modules.catalog.entity.BookCopyEntity::getBarcode,
                                Function.identity()));
        for (LoanDetailEntity detail : details) {
            detail.setReturnStatus(detailStatus.name());
            loanDetailRepository.save(detail);
            if (detail.getBarcode() == null) {
                continue;
            }
            var copy = copies.get(detail.getBarcode());
            if (copy == null
                    || !"RESERVED".equalsIgnoreCase(copy.getStatus())
                    || !Objects.equals(copy.getLockedByUser(), ticket.getUserId())) {
                continue;
            }
            copy.setStatus("AVAILABLE");
            copy.setLockedByUser(null);
            copy.setLockedUntil(null);
            bookCopyRepository.save(copy);
            waitlistService.promoteNext(copy, LocalDateTime.now());
        }
    }
}
