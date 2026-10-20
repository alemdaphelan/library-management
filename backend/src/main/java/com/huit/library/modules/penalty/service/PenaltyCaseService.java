package com.huit.library.modules.penalty.service;

import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.finance.entity.TransactionEntity;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.TransactionRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.notification.service.NotificationService;
import com.huit.library.modules.penalty.dto.CompensationRateRequestDTO;
import com.huit.library.modules.penalty.dto.PenaltyCaseRequestDTO;
import com.huit.library.modules.penalty.dto.ReplacementAcceptanceRequestDTO;
import com.huit.library.modules.penalty.dto.WaiverReviewRequestDTO;
import com.huit.library.modules.penalty.entity.CompensationRateEntity;
import com.huit.library.modules.penalty.entity.PenaltyCaseEntity;
import com.huit.library.modules.penalty.repository.CompensationRateRepository;
import com.huit.library.modules.penalty.repository.PenaltyCaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PenaltyCaseService {
    private final PenaltyCaseRepository caseRepository;
    private final CompensationRateRepository rateRepository;
    private final LoanDetailRepository detailRepository;
    private final LoanTicketRepository ticketRepository;
    private final BookCopyRepository copyRepository;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final LateFineService lateFineService;
    private final NotificationService notificationService;
    private final BigDecimal technicalFee;

    public PenaltyCaseService(
            PenaltyCaseRepository caseRepository,
            CompensationRateRepository rateRepository,
            LoanDetailRepository detailRepository,
            LoanTicketRepository ticketRepository,
            BookCopyRepository copyRepository,
            TransactionRepository transactionRepository,
            WalletRepository walletRepository,
            LateFineService lateFineService,
            NotificationService notificationService,
            @Value("${library.circulation.technical-processing-fee:50000}") BigDecimal technicalFee) {
        this.caseRepository = caseRepository;
        this.rateRepository = rateRepository;
        this.detailRepository = detailRepository;
        this.ticketRepository = ticketRepository;
        this.copyRepository = copyRepository;
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.lateFineService = lateFineService;
        this.notificationService = notificationService;
        this.technicalFee = technicalFee;
    }

    @Transactional
    public PenaltyCaseEntity createCase(PenaltyCaseRequestDTO request, UUID operatorId) {
        LoanDetailEntity detail = detailRepository.findByIdForUpdate(request.getLoanDetailId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chi tiết mượn"));
        if (caseRepository.existsByLoanDetailIdAndStatusNotIn(
                detail.getDetailId(), List.of("CANCELLED", "REJECTED"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tài liệu đã có hồ sơ mất/hỏng đang xử lý");
        }
        LoanTicketEntity ticket = ticketRepository.findById(detail.getTicketId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu mượn"));
        if (detail.getReturnDate() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tài liệu đã hoàn tất trả");
        }
        BookCopyEntity copy = copyRepository.findById(detail.getBarcode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản sao"));

        LocalDateTime reportedAt = LocalDateTime.now();
        lateFineService.accrueLateFine(ticket, detail, reportedAt);
        String incident = normalize(request.getIncidentType());
        String severity = request.getDamageSeverity() == null ? null : normalize(request.getDamageSeverity());
        validateDamage(incident, severity, request.getPagesAffected());
        boolean treatedAsLoss = "LOST".equals(incident) || "HEAVY".equals(severity);
        String settlement = normalize(request.getSettlementMethod());

        BigDecimal repair = BigDecimal.ZERO;
        BigDecimal compensation = BigDecimal.ZERO;
        BigDecimal processing = BigDecimal.ZERO;
        BigDecimal multiplier = BigDecimal.ONE;
        if (!treatedAsLoss) {
            if (!"REPAIR".equals(settlement)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hư hỏng nhẹ phải xử lý theo phương thức REPAIR");
            }
            repair = positiveRequired(request.getRepairFee(), "Phải nhập chi phí sửa chữa hư hỏng nhẹ");
        } else {
            processing = technicalFee;
            if (Boolean.TRUE.equals(request.getStillInMarket()) && !"REPLACEMENT".equals(settlement)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài liệu còn lưu hành phải được thay thế đúng quy định");
            }
            if (!List.of("REPLACEMENT", "CASH").contains(settlement)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phương thức đền bù phải là REPLACEMENT hoặc CASH");
            }
            if ("CASH".equals(settlement)) {
                if (request.getCoverPrice() != null && request.getCoverPrice().signum() > 0) {
                    multiplier = "FOREIGN".equals(normalize(request.getLanguageCategory()))
                            ? BigDecimal.valueOf(3) : BigDecimal.valueOf(5);
                    BigDecimal convertedPrice = request.getCoverPrice();
                    if ("FOREIGN".equals(normalize(request.getLanguageCategory()))) {
                        convertedPrice = convertedPrice.multiply(positiveRequired(
                                request.getFxRate(), "Tài liệu ngoại văn cần tỷ giá tại thời điểm nộp"));
                        if (request.getFxSource() == null || request.getFxSource().isBlank()) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phải ghi nguồn tỷ giá ngoại tệ");
                        }
                    }
                    compensation = convertedPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
                } else {
                    compensation = rateRepository.findCurrent(
                                    normalize(request.getMaterialType()),
                                    normalize(request.getLanguageCategory()), reportedAt.toLocalDate())
                            .map(CompensationRateEntity::getAmount)
                            .orElseThrow(() -> new ResponseStatusException(
                                    HttpStatus.CONFLICT, "Chưa có biểu phí hiệu lực cho tài liệu không có giá bìa"));
                }
            }
        }

        detail.setFineStoppedAt(reportedAt);
        detail.setReturnStatus(treatedAsLoss ? "LOST" : "DAMAGED");
        detailRepository.save(detail);
        copy.setStatus(treatedAsLoss ? "LOST" : "MAINTENANCE");
        copy.setConditionNote(request.getDescription().trim());
        copyRepository.save(copy);

        PenaltyCaseEntity penaltyCase = new PenaltyCaseEntity();
        penaltyCase.setUserId(ticket.getUserId());
        penaltyCase.setTicketId(ticket.getTicketId());
        penaltyCase.setLoanDetailId(detail.getDetailId());
        penaltyCase.setBarcode(detail.getBarcode());
        penaltyCase.setIncidentType(incident);
        penaltyCase.setDamageSeverity(severity);
        penaltyCase.setPagesAffected(request.getPagesAffected());
        penaltyCase.setDescription(request.getDescription().trim());
        penaltyCase.setEvidenceUrl(request.getEvidenceUrl());
        penaltyCase.setMaterialType(normalize(request.getMaterialType()));
        penaltyCase.setLanguageCategory(normalize(request.getLanguageCategory()));
        penaltyCase.setStillInMarket(request.getStillInMarket());
        penaltyCase.setSettlementMethod(settlement);
        penaltyCase.setCoverPrice(request.getCoverPrice());
        penaltyCase.setOriginalCurrency(request.getOriginalCurrency());
        penaltyCase.setFxRate(request.getFxRate());
        penaltyCase.setFxSource(request.getFxSource());
        penaltyCase.setMultiplier(multiplier);
        penaltyCase.setRepairFee(repair);
        penaltyCase.setCompensationAmount(compensation);
        penaltyCase.setTechnicalFee(processing);
        penaltyCase.setOverdueFineSnapshot(zero(detail.getLateFineAccrued()));
        penaltyCase.setTotalAmount(repair.add(compensation).add(processing));
        penaltyCase.setPaidAmount(BigDecimal.ZERO);
        penaltyCase.setStatus("OUTSTANDING");
        penaltyCase.setReportedAt(reportedAt);
        penaltyCase.setSettlementDueAt(reportedAt.plusDays(30));
        penaltyCase.setCreatedBy(operatorId);
        penaltyCase = caseRepository.save(penaltyCase);

        createDebtLine(penaltyCase, "REPAIR_FEE", repair, "Chi phí sửa chữa: " + penaltyCase.getDescription());
        createDebtLine(penaltyCase, "COMPENSATION", compensation, "Đền bù tài liệu " + detail.getBarcode());
        createDebtLine(penaltyCase, "TECHNICAL_FEE", processing, "Phí xử lý kỹ thuật tài liệu " + detail.getBarcode());
        notificationService.createOnce(
                ticket.getUserId(), "PENALTY_CASE_CREATED",
                "Hồ sơ #" + penaltyCase.getCaseId() + " đã được lập; hạn xử lý "
                        + penaltyCase.getSettlementDueAt().toLocalDate() + ".",
                "PENALTY_CASE", penaltyCase.getCaseId().toString(),
                "PENALTY_CASE_CREATED:" + penaltyCase.getCaseId(), reportedAt);
        return penaltyCase;
    }

    @Transactional
    public CompensationRateEntity createRate(CompensationRateRequestDTO request, UUID adminId) {
        if (request.getValidUntil() != null && request.getValidUntil().isBefore(request.getValidFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày hết hiệu lực không được trước ngày bắt đầu");
        }
        CompensationRateEntity rate = new CompensationRateEntity();
        rate.setMaterialType(normalize(request.getMaterialType()));
        rate.setLanguageCategory(normalize(request.getLanguageCategory()));
        rate.setAmount(request.getAmount());
        rate.setValidFrom(request.getValidFrom());
        rate.setValidUntil(request.getValidUntil());
        rate.setActive(true);
        rate.setConfiguredBy(adminId);
        rate.setNote(request.getNote());
        return rateRepository.save(rate);
    }

    @Transactional
    public PenaltyCaseEntity submitForReview(Long caseId, UUID operatorId) {
        PenaltyCaseEntity penaltyCase = caseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ"));
        if (!"OUTSTANDING".equals(penaltyCase.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ hồ sơ còn nợ mới có thể xin miễn giảm");
        }
        penaltyCase.setStatus("PENDING_REVIEW");
        penaltyCase.setDecisionReason("Thủ thư tiếp nhận hồ sơ miễn giảm: " + operatorId);
        return caseRepository.save(penaltyCase);
    }

    @Transactional
    public PenaltyCaseEntity reviewWaiver(Long caseId, WaiverReviewRequestDTO request, UUID adminId) {
        PenaltyCaseEntity penaltyCase = caseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ"));
        if (!"PENDING_REVIEW".equals(penaltyCase.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hồ sơ không ở trạng thái chờ xét miễn giảm");
        }
        String action = normalize(request.getAction());
        penaltyCase.setDecisionBy(adminId);
        penaltyCase.setExternalDecisionMaker(request.getExternalDecisionMaker().trim());
        penaltyCase.setDecisionDocumentNo(request.getDecisionDocumentNo().trim());
        penaltyCase.setDecisionDate(request.getDecisionDate());
        penaltyCase.setDecisionReason(request.getReason().trim());
        if ("REJECT".equals(action)) {
            penaltyCase.setStatus("OUTSTANDING");
            return caseRepository.save(penaltyCase);
        }
        if (!"APPROVE".equals(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action phải là APPROVE hoặc REJECT");
        }
        BigDecimal remaining = penaltyCase.getTotalAmount().subtract(zero(penaltyCase.getPaidAmount()));
        BigDecimal waiver = request.getWaiverAmount() == null ? remaining : request.getWaiverAmount();
        if (waiver.signum() <= 0 || waiver.compareTo(remaining) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số tiền miễn giảm không hợp lệ");
        }
        applyWaiverToDebtLines(penaltyCase, waiver, adminId);
        penaltyCase.setWaiverAmount(zero(penaltyCase.getWaiverAmount()).add(waiver));
        penaltyCase.setPaidAmount(zero(penaltyCase.getPaidAmount()).add(waiver));
        if (penaltyCase.getPaidAmount().compareTo(penaltyCase.getTotalAmount()) >= 0) {
            if (requiresUnacceptedReplacement(penaltyCase)) {
                penaltyCase.setStatus("WAITING_REPLACEMENT");
            } else {
                penaltyCase.setStatus("WAIVED");
                closeResolvedLoanDetail(penaltyCase);
            }
        } else {
            penaltyCase.setStatus("OUTSTANDING");
        }
        notificationService.createOnce(
                penaltyCase.getUserId(), "WAIVER_DECISION",
                "Hồ sơ #" + caseId + " được miễn/giảm " + waiver.toPlainString() + " đồng.",
                "PENALTY_CASE", caseId.toString(), "WAIVER_DECISION:" + caseId, LocalDateTime.now());
        return caseRepository.save(penaltyCase);
    }

    @Transactional
    public PenaltyCaseEntity acceptReplacement(
            Long caseId, ReplacementAcceptanceRequestDTO request, UUID operatorId) {
        PenaltyCaseEntity penaltyCase = caseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ"));
        if (!"REPLACEMENT".equals(penaltyCase.getSettlementMethod())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hồ sơ này không xử lý bằng sách thay thế");
        }
        if (penaltyCase.getReplacementAcceptedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sách thay thế đã được xác nhận trước đó");
        }
        String replacementBarcode = request.getReplacementBarcode().trim();
        if (copyRepository.existsById(replacementBarcode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Barcode sách thay thế đã tồn tại");
        }
        BookCopyEntity originalCopy = copyRepository.findById(penaltyCase.getBarcode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản sao gốc"));
        BookCopyEntity replacementCopy = new BookCopyEntity();
        replacementCopy.setBarcode(replacementBarcode);
        replacementCopy.setBookId(originalCopy.getBookId());
        replacementCopy.setStatus("AVAILABLE");
        replacementCopy.setConditionNote("Sách đền bù đã được thủ thư kiểm tra: "
                + request.getVerificationNote().trim());
        copyRepository.save(replacementCopy);

        penaltyCase.setReplacementBarcode(replacementBarcode);
        penaltyCase.setReplacementAcceptedAt(LocalDateTime.now());
        penaltyCase.setReplacementAcceptedBy(operatorId);
        penaltyCase.setReplacementVerificationNote(request.getVerificationNote().trim());
        if (zero(penaltyCase.getPaidAmount()).compareTo(penaltyCase.getTotalAmount()) >= 0) {
            penaltyCase.setStatus("PAID");
            closeResolvedLoanDetail(penaltyCase);
        } else {
            penaltyCase.setStatus("OUTSTANDING");
        }
        return caseRepository.save(penaltyCase);
    }

    @Transactional(readOnly = true)
    public List<PenaltyCaseEntity> listCases() {
        return caseRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<PenaltyCaseEntity> listMyCases(UUID userId) {
        return caseRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private void createDebtLine(PenaltyCaseEntity penaltyCase, String type, BigDecimal amount, String reason) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        TransactionEntity transaction = new TransactionEntity();
        transaction.setUserId(penaltyCase.getUserId());
        transaction.setTicketId(penaltyCase.getTicketId());
        transaction.setLoanDetailId(penaltyCase.getLoanDetailId());
        transaction.setPenaltyCaseId(penaltyCase.getCaseId());
        transaction.setAmount(amount);
        transaction.setPaidAmount(BigDecimal.ZERO);
        transaction.setType(type);
        transaction.setReason(reason);
        transaction.setStatus("OUTSTANDING");
        transaction.setDedupKey("PENALTY_CASE:" + penaltyCase.getCaseId() + ":" + type);
        transactionRepository.save(transaction);

        WalletEntity wallet = walletRepository.findByUserIdForUpdate(penaltyCase.getUserId())
                .orElseGet(() -> newWallet(penaltyCase.getUserId()));
        wallet.setTotalDebt(zero(wallet.getTotalDebt()).add(amount));
        walletRepository.save(wallet);
    }

    private void applyWaiverToDebtLines(PenaltyCaseEntity penaltyCase, BigDecimal waiver, UUID adminId) {
        BigDecimal remaining = waiver;
        for (TransactionEntity entry : transactionRepository.findByPenaltyCaseIdOrderByCreatedAtAsc(penaltyCase.getCaseId())) {
            if (remaining.signum() == 0 || entry.getAmount() == null || entry.getAmount().signum() <= 0) {
                continue;
            }
            BigDecimal outstanding = entry.getAmount().subtract(zero(entry.getPaidAmount()));
            BigDecimal applied = outstanding.min(remaining);
            entry.setPaidAmount(zero(entry.getPaidAmount()).add(applied));
            entry.setStatus(entry.getPaidAmount().compareTo(entry.getAmount()) >= 0 ? "WAIVED" : "PARTIALLY_WAIVED");
            transactionRepository.save(entry);
            remaining = remaining.subtract(applied);
        }
        WalletEntity wallet = walletRepository.findByUserIdForUpdate(penaltyCase.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Không tìm thấy công nợ"));
        wallet.setTotalDebt(zero(wallet.getTotalDebt()).subtract(waiver).max(BigDecimal.ZERO));
        walletRepository.save(wallet);

        TransactionEntity adjustment = new TransactionEntity();
        adjustment.setUserId(penaltyCase.getUserId());
        adjustment.setTicketId(penaltyCase.getTicketId());
        adjustment.setLoanDetailId(penaltyCase.getLoanDetailId());
        adjustment.setPenaltyCaseId(penaltyCase.getCaseId());
        adjustment.setAmount(waiver.negate());
        adjustment.setType("WAIVER_ADJUSTMENT");
        adjustment.setReason(penaltyCase.getDecisionReason());
        adjustment.setStatus("COMPLETED");
        adjustment.setConfirmedBy(adminId);
        adjustment.setDedupKey("WAIVER:" + penaltyCase.getCaseId());
        transactionRepository.save(adjustment);
    }

    private boolean requiresUnacceptedReplacement(PenaltyCaseEntity penaltyCase) {
        return "REPLACEMENT".equals(penaltyCase.getSettlementMethod())
                && penaltyCase.getReplacementAcceptedAt() == null;
    }

    private void closeResolvedLoanDetail(PenaltyCaseEntity penaltyCase) {
        LoanDetailEntity detail = detailRepository.findById(penaltyCase.getLoanDetailId()).orElse(null);
        if (detail == null) {
            return;
        }
        detail.setReturnDate(LocalDateTime.now());
        detail.setReturnStatus("SETTLED");
        detailRepository.save(detail);
        LoanTicketEntity ticket = ticketRepository.findById(penaltyCase.getTicketId()).orElse(null);
        if (ticket != null && detailRepository.findByTicketId(ticket.getTicketId()).stream()
                .allMatch(item -> item.getReturnDate() != null || "CANCELLED".equals(item.getReturnStatus()))) {
            ticket.setStatus("RETURNED");
            ticketRepository.save(ticket);
        }
    }

    private void validateDamage(String incident, String severity, Integer pagesAffected) {
        if (!List.of("LOST", "DAMAGED").contains(incident)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incident type phải là LOST hoặc DAMAGED");
        }
        if ("LOST".equals(incident)) {
            return;
        }
        if (!List.of("LIGHT", "HEAVY").contains(severity)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hư hỏng phải được phân loại LIGHT hoặc HEAVY");
        }
        if (pagesAffected != null && pagesAffected < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số trang ảnh hưởng không hợp lệ");
        }
        if (pagesAffected != null && pagesAffected > 10 && "LIGHT".equals(severity)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trên 10 trang phải được phân loại hư hỏng nặng");
        }
    }

    private BigDecimal positiveRequired(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private WalletEntity newWallet(UUID userId) {
        WalletEntity wallet = new WalletEntity();
        wallet.setUserId(userId);
        wallet.setDepositBalance(BigDecimal.ZERO);
        wallet.setTotalDebt(BigDecimal.ZERO);
        return wallet;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
