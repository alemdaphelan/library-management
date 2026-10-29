package com.huit.library.modules.circulation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.circulation.dto.ReturnInspectionRequestDTO;
import com.huit.library.modules.circulation.dto.ReturnInspectionResponseDTO;
import com.huit.library.modules.circulation.dto.ReturnTarget;
import com.huit.library.modules.circulation.entity.CirculationOperationEntity;
import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.repository.CirculationOperationRepository;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.penalty.entity.PenaltyCaseEntity;
import com.huit.library.modules.penalty.service.PenaltyCaseService;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReturnInspectionService {
    private static final String TYPE = "RETURN_INSPECT";
    private final LoanTicketRepository tickets;
    private final LoanDetailRepository details;
    private final BookCopyRepository copies;
    private final UserRepository users;
    private final WalletRepository wallets;
    private final CirculationOperationRepository operations;
    private final CirculationStateService circulation;
    private final PenaltyCaseService penalties;
    private final LibraryHoursService hours;
    private final ObjectMapper json;
    private final Validator validator;

    public ReturnInspectionService(LoanTicketRepository tickets, LoanDetailRepository details,
            BookCopyRepository copies, UserRepository users, WalletRepository wallets,
            CirculationOperationRepository operations, CirculationStateService circulation,
            PenaltyCaseService penalties, LibraryHoursService hours, ObjectMapper json, Validator validator) {
        this.tickets = tickets;
        this.details = details;
        this.copies = copies;
        this.users = users;
        this.wallets = wallets;
        this.operations = operations;
        this.circulation = circulation;
        this.penalties = penalties;
        this.hours = hours;
        this.json = json;
        this.validator = validator;
    }

    @Transactional
    public ReturnInspectionResponseDTO inspect(ReturnInspectionRequestDTO request, UUID operatorId) {
        validate(request);
        String key = request.getIdempotencyKey().trim();
        List<ReturnInspectionRequestDTO.Item> items = request.getItems().stream()
                .sorted(Comparator.comparing(ReturnInspectionRequestDTO.Item::getLoanDetailId)).toList();
        String fingerprint = fingerprint(items);
        ReturnInspectionResponseDTO replay = replay(key, fingerprint);
        if (replay != null) return replay;
        hours.requireCounterOpen("kiểm tra và nhận trả tài liệu");

        List<Long> ids = items.stream().map(ReturnInspectionRequestDTO.Item::getLoanDetailId).toList();
        List<ReturnTarget> targets = details.findReturnTargets(ids);
        if (targets.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đầy đủ chi tiết mượn cần xử lý");
        }
        Map<Long, LoanTicketEntity> lockedTickets = new LinkedHashMap<>();
        for (Long id : targets.stream().map(ReturnTarget::ticketId).distinct().sorted().toList()) {
            lockedTickets.put(id, tickets.findByIdForUpdate(id).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu mượn")));
        }
        // A concurrent retry on the same items now sees the committed receipt, not stale item states.
        replay = replay(key, fingerprint);
        if (replay != null) return replay;

        List<UUID> readerIds = lockedTickets.values().stream().map(LoanTicketEntity::getUserId)
                .distinct().sorted().toList();
        for (UUID readerId : readerIds) {
            users.findByIdForUpdate(readerId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bạn đọc"));
        }
        Map<Long, LoanDetailEntity> lockedDetails = new LinkedHashMap<>();
        for (Long ticketId : lockedTickets.keySet()) {
            details.findByTicketIdForUpdate(ticketId).stream().filter(detail -> ids.contains(detail.getDetailId()))
                    .forEach(detail -> lockedDetails.put(detail.getDetailId(), detail));
        }
        List<String> barcodes = targets.stream().map(ReturnTarget::barcode).sorted().toList();
        if (new LinkedHashSet<>(barcodes).size() != barcodes.size()) {
            throw conflict("Một bản sao xuất hiện trong nhiều chi tiết mượn");
        }
        Map<String, BookCopyEntity> lockedCopies = copies.findAllByBarcodeInForUpdate(barcodes).stream()
                .collect(Collectors.toMap(BookCopyEntity::getBarcode, Function.identity()));
        for (UUID readerId : readerIds) wallets.findByUserIdForUpdate(readerId);

        // Validate the whole batch before any fine, penalty, inventory or waitlist mutation.
        for (ReturnTarget target : targets) {
            LoanDetailEntity detail = lockedDetails.get(target.detailId());
            BookCopyEntity copy = lockedCopies.get(target.barcode());
            LoanTicketEntity ticket = lockedTickets.get(target.ticketId());
            if (detail == null || copy == null || !target.barcode().equals(detail.getBarcode())
                    || detail.getReturnDate() != null
                    || !List.of("BORROWING", "OVERDUE").contains(detail.getReturnStatus())
                    || !List.of("BORROWING", "OVERDUE").contains(copy.getStatus())
                    || !List.of("BORROWING", "OVERDUE").contains(ticket.getStatus())) {
                throw conflict("Tài liệu không còn ở trạng thái đang mượn hoặc đã có hồ sơ xử lý");
            }
        }

        List<ReturnInspectionResponseDTO.ItemResult> results = new ArrayList<>();
        LocalDateTime receivedAt = LocalDateTime.now();
        for (var item : items) {
            LoanDetailEntity detail = lockedDetails.get(item.getLoanDetailId());
            LoanTicketEntity ticket = lockedTickets.get(detail.getTicketId());
            BookCopyEntity copy = lockedCopies.get(detail.getBarcode());
            var before = balance(ticket.getUserId());
            BigDecimal previousLateFine = zero(detail.getLateFineAccrued());
            PenaltyCaseEntity penalty = null;
            if (item.getCondition() == ReturnInspectionRequestDTO.Condition.NORMAL) {
                circulation.receiveNormalReturn(ticket, detail, copy, item.getReturnConditionNote(), receivedAt);
            } else {
                // createCase accrues/stops late fines before recording the incident and applies the deposit.
                penalty = penalties.createCase(item.getPenalty(), operatorId);
                if (item.getCondition() == ReturnInspectionRequestDTO.Condition.DAMAGED) {
                    detail.setPhysicalReturnAt(receivedAt);
                    detail.setPhysicalReturnTimeSource("LIVE_DAMAGED_RECEIPT");
                }
                detail.setReturnConditionNote(item.getReturnConditionNote());
                details.save(detail);
            }
            var after = balance(ticket.getUserId());
            results.add(new ReturnInspectionResponseDTO.ItemResult(
                    detail.getDetailId(), ticket.getTicketId(), ticket.getUserId(), detail.getBarcode(),
                    item.getCondition().name(), detail.getReturnStatus(), copy.getStatus(),
                    penalty == null ? null : penalty.getCaseId(), penalty == null ? null : penalty.getStatus(),
                    zero(detail.getLateFineAccrued()).subtract(previousLateFine), zero(detail.getLateFineAccrued()),
                    penalty == null ? BigDecimal.ZERO : penalty.getTotalAmount(),
                    before.depositBalance().subtract(after.depositBalance()),
                    after.totalDebt().subtract(before.totalDebt()),
                    penalty == null ? BigDecimal.ZERO : penalty.getTotalAmount().subtract(zero(penalty.getPaidAmount())),
                    detail.getReturnDate(), detail.getFineStoppedAt()));
        }
        circulation.completeReturnedTickets(new ArrayList<>(lockedTickets.values()), operatorId);

        CirculationOperationEntity operation = new CirculationOperationEntity();
        operation.setOperationId(UUID.randomUUID());
        operation.setIdempotencyKey(key);
        operation.setRequestHash(fingerprint);
        operation.setOperationType(TYPE);
        operation.setTicketIds(lockedTickets.keySet().stream().map(String::valueOf).collect(Collectors.joining(",")));
        operation.setBarcodes(String.join(",", barcodes));
        operation.setResultStatus("INSPECTED");
        operation.setMessage("Đã kiểm tra và ghi nhận " + items.size() + " tài liệu tại quầy");
        operation = operations.saveAndFlush(operation);
        var response = new ReturnInspectionResponseDTO(operation.getOperationId(), TYPE, operation.getProcessedAt(), false,
                results, lockedTickets.values().stream().map(ticket ->
                        new ReturnInspectionResponseDTO.TicketResult(ticket.getTicketId(), ticket.getStatus())).toList(),
                readerIds.stream().map(this::balance).toList());
        operation.setResultPayload(write(response));
        operations.saveAndFlush(operation);
        return response;
    }

    private void validate(ReturnInspectionRequestDTO request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dữ liệu kiểm tra trả sách không hợp lệ");
        }
        if (request.getItems().stream().map(ReturnInspectionRequestDTO.Item::getLoanDetailId).distinct().count()
                != request.getItems().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chi tiết mượn bị trùng trong yêu cầu");
        }
        for (var item : request.getItems()) {
            var penalty = item.getPenalty();
            if (item.getCondition() == ReturnInspectionRequestDTO.Condition.NORMAL) {
                if (penalty != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tài liệu trả bình thường không được kèm phiếu phạt mất/hỏng");
            } else if (penalty == null || !item.getLoanDetailId().equals(penalty.getLoanDetailId())
                    || !item.getCondition().name().equalsIgnoreCase(penalty.getIncidentType().trim())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phiếu phạt phải khớp tài liệu và loại sự cố");
            } else if ((item.getCondition() == ReturnInspectionRequestDTO.Condition.LOST
                    || "HEAVY".equalsIgnoreCase(penalty.getDamageSeverity())) && penalty.getStillInMarket() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cần xác định tài liệu còn lưu hành trước khi chọn cách đền bù");
            }
        }
    }

    private ReturnInspectionResponseDTO.ReaderBalance balance(UUID readerId) {
        return wallets.findByUserId(readerId).map(wallet -> new ReturnInspectionResponseDTO.ReaderBalance(
                readerId, zero(wallet.getDepositBalance()), zero(wallet.getTotalDebt())))
                .orElseGet(() -> new ReturnInspectionResponseDTO.ReaderBalance(readerId, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private ReturnInspectionResponseDTO replay(String key, String fingerprint) {
        return operations.findByIdempotencyKey(key).map(operation -> {
            if (!TYPE.equals(operation.getOperationType()) || !fingerprint.equals(operation.getRequestHash())) {
                throw conflict("Idempotency key đã được dùng cho một yêu cầu khác");
            }
            try {
                return json.readValue(operation.getResultPayload(), ReturnInspectionResponseDTO.class).asReplay();
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                throw new IllegalStateException("Biên nhận kiểm tra trả sách đã lưu không hợp lệ", exception);
            }
        }).orElse(null);
    }

    private String fingerprint(List<ReturnInspectionRequestDTO.Item> items) {
        try {
            byte[] content = (TYPE + ":" + canonical(json.valueToTree(items))).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private JsonNode canonical(JsonNode value) {
        if (value.isNumber()) return DecimalNode.valueOf(value.decimalValue().stripTrailingZeros());
        if (value.isArray()) {
            var array = json.createArrayNode();
            value.forEach(item -> array.add(canonical(item)));
            return array;
        }
        if (value.isObject()) {
            ObjectNode object = json.createObjectNode();
            List<String> keys = new ArrayList<>();
            value.fieldNames().forEachRemaining(keys::add);
            keys.stream().sorted().forEach(key -> object.set(key, canonical(value.get(key))));
            return object;
        }
        return value;
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể lưu biên nhận kiểm tra trả sách", exception);
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private BigDecimal zero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
