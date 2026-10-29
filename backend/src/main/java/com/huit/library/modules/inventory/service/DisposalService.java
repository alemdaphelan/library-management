package com.huit.library.modules.inventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.catalog.entity.*;
import com.huit.library.modules.catalog.repository.*;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.inventory.dto.DisposalModels.*;
import com.huit.library.modules.inventory.entity.*;
import com.huit.library.modules.inventory.repository.*;
import jakarta.validation.Validator;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DisposalService {
    private static final List<String> ACTIVE = List.of("DRAFT", "SUBMITTED");
    private static final Set<String> PROPOSABLE = Set.of("AVAILABLE", "DAMAGED", "MAINTENANCE");
    private final DisposalBatchRepository batches;
    private final DisposalProposalRepository proposals;
    private final DisposalHistoryRepository history;
    private final InventoryAuditRepository audits;
    private final InventoryDetailRepository scans;
    private final BookCopyRepository copies;
    private final BookRepository books;
    private final LoanDetailRepository loans;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final Validator validator;
    public DisposalService(DisposalBatchRepository batches, DisposalProposalRepository proposals, DisposalHistoryRepository history,
            InventoryAuditRepository audits, InventoryDetailRepository scans, BookCopyRepository copies, BookRepository books,
            LoanDetailRepository loans, ObjectMapper json, JdbcTemplate jdbc, Validator validator) {
        this.batches = batches; this.proposals = proposals; this.history = history; this.audits = audits; this.scans = scans;
        this.copies = copies; this.books = books; this.loans = loans; this.json = json; this.jdbc = jdbc; this.validator = validator;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Candidate> candidates(Long auditId) {
        return candidates(audit(auditId, false), scans.findByAuditIdOrderByCreatedAtAsc(auditId.toString()), false, null);
    }

    @Transactional
    public Batch create(Create raw, UserEntity actor) {
        actor(actor); valid(raw);
        var lines = raw.lines().stream().map(l -> new Line(l.barcode().trim(), l.sourceDetailId().trim(), l.expectedFingerprint(),
                l.observedCondition().trim(), l.reasonCode(), l.reason().trim(), l.proposedAction(), evidence(l.evidenceUrl())))
                .sorted(Comparator.comparing(Line::barcode)).toList();
        if (lines.stream().map(Line::barcode).distinct().count() != lines.size()) throw bad("Không chọn trùng barcode trong một lần lập");
        Create request = new Create(raw.requestId(), raw.auditId(), lines);
        String payload = write(request);
        // Same key is serialized before looking for the persisted result. No copy state is changed.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", (org.springframework.jdbc.core.RowCallbackHandler) rs -> {}, request.requestId().toString());
        var prior = batches.findById(request.requestId());
        if (prior.isPresent()) {
            if (!prior.get().getCreatedBy().equals(actor.getUserId()) || !prior.get().getRequestPayload().equals(payload))
                throw conflict("Mã gửi đã dùng với người lập hoặc nội dung khác");
            return new Batch(request.requestId(), true, proposals.findByRequestIdOrderByBarcode(request.requestId()).stream().map(this::item).toList());
        }
        var audit = audit(request.auditId(), true);
        var selectedIds = lines.stream().map(Line::sourceDetailId).collect(Collectors.toSet());
        var selected = scans.findForDisposal(audit.getAuditId().toString()).stream().filter(s -> selectedIds.contains(s.getDetailId())).toList();
        var candidateMap = candidates(audit, selected, true, null).stream().collect(Collectors.toMap(c -> c.source().detailId(), Function.identity()));
        for (var line : lines) requireCandidate(candidateMap.get(line.sourceDetailId()), line.barcode(), line.expectedFingerprint());
        var batch = new DisposalBatchEntity(); batch.setRequestId(request.requestId()); batch.setCreatedBy(actor.getUserId()); batch.setRequestPayload(payload); batches.saveAndFlush(batch);
        var saved = new ArrayList<Item>();
        for (var line : lines) {
            var c = candidateMap.get(line.sourceDetailId());
            var p = new DisposalProposalEntity(); p.setRequestId(batch.getRequestId()); p.setBarcode(line.barcode());
            p.setBookId(c.source().bookId()); p.setBookTitle(title(c.source())); p.setAuditId(request.auditId()); p.setSourceDetailId(line.sourceDetailId());
            p.setSourceFingerprint(c.fingerprint()); p.setSourceSnapshot(write(c.source()));
            p.setObservedCondition(line.observedCondition()); p.setReasonCode(line.reasonCode().name()); p.setReason(line.reason());
            p.setProposedAction(line.proposedAction().name()); p.setEvidenceUrl(line.evidenceUrl()); p.setStatus("DRAFT");
            p.setCreatedBy(actor.getUserId()); p.setCreatedByName(name(actor));
            proposals.saveAndFlush(p); event(p, "CREATED", actor, null); saved.add(item(p));
        }
        return new Batch(batch.getRequestId(), false, List.copyOf(saved));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Detail get(UUID id, UserEntity actor) { actor(actor); return detail(find(id, false), actor); }

    @Transactional(readOnly = true)
    public PageResult list(Status status, Long auditId, String barcode, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) throw bad("page không âm; size 1–100; trang quá lớn");
        String query = barcode == null ? "" : barcode.trim();
        if (query.length() > 255) throw bad("Barcode tối đa 255 ký tự");
        Specification<DisposalProposalEntity> spec = (r, q, b) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status != null) predicates.add(b.equal(r.get("status"), status.name()));
            if (auditId != null) predicates.add(b.equal(r.get("auditId"), auditId));
            if (!query.isEmpty()) predicates.add(b.equal(r.get("barcode"), query));
            return b.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result = proposals.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("proposalId"))));
        return new PageResult(result.getContent().stream().map(this::item).toList(), result.getTotalElements(), page, size);
    }

    @Transactional
    public Detail edit(UUID id, Edit request, UserEntity actor) {
        actor(actor); valid(request); var p = find(id, true); owner(p, actor); version(p, request.version());
        if (!p.getStatus().equals("DRAFT")) throw conflict("Chỉ sửa hồ sơ còn nháp");
        var c = current(p, true); requireCandidate(c, p.getBarcode(), request.expectedFingerprint());
        Snapshot before = snapshot(p);
        p.setObservedCondition(request.observedCondition().trim()); p.setReasonCode(request.reasonCode().name()); p.setReason(request.reason().trim());
        p.setProposedAction(request.proposedAction().name()); p.setEvidenceUrl(evidence(request.evidenceUrl()));
        p.setSourceFingerprint(c.fingerprint()); p.setSourceSnapshot(write(c.source())); p.setBookId(c.source().bookId()); p.setBookTitle(title(c.source()));
        if (!snapshot(p).equals(before)) { proposals.saveAndFlush(p); event(p, "UPDATED", actor, before); }
        return detail(p, actor);
    }

    @Transactional
    public Detail transition(UUID id, Transition request, boolean submit, UserEntity actor) {
        actor(actor); valid(request); var p = find(id, true); owner(p, actor);
        String target = submit ? "SUBMITTED" : "CANCELLED";
        if (p.getStatus().equals(target) && (p.getVersion().equals(request.version()) || p.getVersion() == request.version() + 1)) return detail(p, actor);
        version(p, request.version());
        if (!(p.getStatus().equals("DRAFT") || (!submit && p.getStatus().equals("SUBMITTED")))) throw conflict("Hồ sơ không còn ở bước được phép");
        if (submit) { var c = current(p, true); requireCandidate(c, p.getBarcode(), p.getSourceFingerprint()); }
        var before = snapshot(p); p.setStatus(target); proposals.saveAndFlush(p); event(p, target, actor, before);
        return detail(p, actor);
    }

    private Candidate current(DisposalProposalEntity p, boolean lock) {
        var audit = audit(p.getAuditId(), lock);
        var selected = (lock ? scans.findForDisposal(audit.getAuditId().toString()) : scans.findByAuditIdOrderByCreatedAtAsc(audit.getAuditId().toString()))
                .stream().filter(s -> s.getDetailId().equals(p.getSourceDetailId())).toList();
        if (selected.isEmpty()) throw conflict("Dòng kiểm kê nguồn không còn tồn tại");
        return candidates(audit, selected, lock, p.getProposalId()).get(0);
    }
    private List<Candidate> candidates(InventoryAuditEntity audit, List<InventoryDetailEntity> selected, boolean lock, UUID exclude) {
        if (selected.isEmpty()) return List.of();
        var barcodes = selected.stream().map(InventoryDetailEntity::getBarcode).filter(Objects::nonNull).distinct().sorted().toList();
        if (barcodes.isEmpty()) return List.of();
        var copyMap = (lock ? copies.findAllByBarcodeInForUpdate(barcodes) : copies.findAllById(barcodes)).stream().collect(Collectors.toMap(BookCopyEntity::getBarcode, Function.identity()));
        var bookMap = books.findAllById(copyMap.values().stream().map(BookCopyEntity::getBookId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(BookEntity::getBookId, Function.identity()));
        var active = proposals.findByBarcodeInAndStatusIn(barcodes, ACTIVE).stream().filter(p -> !p.getProposalId().equals(exclude))
                .collect(Collectors.toMap(DisposalProposalEntity::getBarcode, DisposalProposalEntity::getProposalId));
        var committed = loans.findCommittedBarcodes(barcodes);
        LocalDateTime now = LocalDateTime.now();
        return selected.stream().map(s -> {
            var c = copyMap.get(s.getBarcode()); var b = c == null ? null : bookMap.get(c.getBookId());
            var reasons = new ArrayList<String>();
            if (!"COMPLETED".equals(audit.getStatus())) reasons.add("Cần hoàn tất phiên kiểm kê trước khi lập đề xuất");
            if (!Set.of("FOUND", "MATCHED", "MISPLACED").contains(Objects.toString(s.getScanStatus(), ""))) reasons.add("Dòng quét chưa nhận diện bản sao hợp lệ");
            if (c == null || b == null) reasons.add("Bản sao hoặc đầu sách không còn tồn tại");
            if (c != null) {
                if (!PROPOSABLE.contains(Objects.toString(c.getStatus(), ""))) reasons.add("Trạng thái bản sao không cho phép lập/gửi đề xuất: " + c.getStatus());
                if (Boolean.TRUE.equals(b == null ? null : b.getIsDigital())) reasons.add("Không xử lý bản sao vật lý cho tài liệu số");
                if ((c.getLockedUntil() != null && c.getLockedUntil().isAfter(now)) || (c.getLockedByUser() != null && c.getLockedUntil() == null)) reasons.add("Bản sao đang được giữ");
            }
            if (committed.contains(s.getBarcode())) reasons.add("Bản sao đang gắn với phiếu mượn/giữ chưa kết thúc");
            if (active.containsKey(s.getBarcode())) reasons.add("Đã có hồ sơ nháp/đã gửi cho barcode này");
            var shelf = c == null ? null : c.getShelf();
            Source source = new Source(audit.getAuditId(), audit.getStatus(), audit.getUpdatedAt(), s.getDetailId(), s.getScanStatus(), s.getScannedLocation(), s.getCreatedAt(), s.getUpdatedAt(),
                    s.getBarcode(), c == null ? null : c.getBookId(), b == null ? null : b.getTitle(), b == null ? null : b.getIsbn(), c == null ? null : c.getStatus(), c == null ? null : c.getConditionNote(),
                    shelf == null ? null : shelf.getShelfId(), location(shelf), c == null ? null : c.getUpdatedAt(), c == null ? null : c.getLockedByUser(), c == null ? null : c.getLockedUntil());
            return new Candidate(source, hash(write(source)), reasons.isEmpty(), List.copyOf(reasons), active.get(s.getBarcode()));
        }).toList();
    }
    private Detail detail(DisposalProposalEntity p, UserEntity actor) {
        Candidate current = current(p, false); boolean own = p.getCreatedBy().equals(actor.getUserId()), draft = p.getStatus().equals("DRAFT");
        return new Detail(item(p), snapshot(p), current, own && draft, own && draft && current.eligible() && p.getSourceFingerprint().equals(current.fingerprint()),
                own && ACTIVE.contains(p.getStatus()), "WAITING_ADMIN_WORKFLOW",
                history.findByProposalIdOrderByProposalVersion(p.getProposalId()).stream().map(h -> new History(h.getEventId(), h.getProposalVersion(), h.getEventType(), h.getActorId(), h.getActorName(), h.getCreatedAt(),
                        h.getBeforeSnapshot() == null ? null : read(h.getBeforeSnapshot(), Snapshot.class), read(h.getAfterSnapshot(), Snapshot.class))).toList());
    }
    private void event(DisposalProposalEntity p, String type, UserEntity actor, Snapshot before) {
        var h = new DisposalHistoryEntity(); h.setProposalId(p.getProposalId()); h.setProposalVersion(p.getVersion()); h.setEventType(type);
        h.setActorId(actor.getUserId()); h.setActorName(name(actor)); h.setBeforeSnapshot(before == null ? null : write(before)); h.setAfterSnapshot(write(snapshot(p))); history.saveAndFlush(h);
    }
    private Snapshot snapshot(DisposalProposalEntity p) { return new Snapshot(p.getStatus(), p.getVersion(), p.getObservedCondition(), p.getReasonCode(), p.getReason(), p.getProposedAction(), p.getEvidenceUrl(), read(p.getSourceSnapshot(), Source.class)); }
    private Item item(DisposalProposalEntity p) { return new Item(p.getProposalId(), p.getRequestId(), p.getBarcode(), p.getBookId(), p.getBookTitle(), p.getAuditId(), p.getStatus(), p.getVersion(), p.getProposedAction(), p.getCreatedBy(), p.getCreatedByName(), p.getCreatedAt(), p.getUpdatedAt()); }
    private DisposalProposalEntity find(UUID id, boolean lock) { return (lock ? proposals.findForUpdate(id) : proposals.findById(id)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ xử lý sách")); }
    private InventoryAuditEntity audit(Long id, boolean lock) { return (lock ? audits.findForDisposal(id) : audits.findById(id)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên kiểm kê")); }
    private void requireCandidate(Candidate c, String barcode, String fingerprint) {
        if (c == null || !c.source().barcode().equals(barcode)) throw conflict("Barcode không khớp dòng kiểm kê nguồn");
        if (!c.eligible()) throw conflict(barcode + ": " + String.join("; ", c.blockers()));
        if (!c.fingerprint().equals(fingerprint)) throw conflict(barcode + ": dữ liệu kiểm kê/bản sao đã đổi; tải lại để xem và xác nhận");
    }
    private void owner(DisposalProposalEntity p, UserEntity actor) { if (!p.getCreatedBy().equals(actor.getUserId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ người lập được sửa/gửi/hủy hồ sơ"); }
    private void version(DisposalProposalEntity p, Long version) { if (!p.getVersion().equals(version)) throw conflict("Hồ sơ đã đổi; tải lại phiên bản mới"); }
    private void actor(UserEntity actor) { if (actor == null || actor.getUserId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không xác định được người lập"); }
    private void valid(Object request) { var errors = validator.validate(request); if (!errors.isEmpty()) throw bad(errors.stream().map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().collect(Collectors.joining("; "))); }
    private String evidence(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        try { URI uri = URI.create(value); if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException e) { throw bad("Chứng cứ phải là URL http/https hợp lệ, không chứa tài khoản"); }
        return value;
    }
    private String location(ShelfEntity shelf) {
        if (shelf == null) return null; var b = shelf.getBookshelf(); var f = b == null ? null : b.getFloor();
        return java.util.stream.Stream.of(f == null ? null : f.getName(), b == null ? null : b.getName(), shelf.getName()).filter(Objects::nonNull).collect(Collectors.joining(" / "));
    }
    private String title(Source source) { return source.bookTitle() == null || source.bookTitle().isBlank() ? "Đầu sách #" + source.bookId() : source.bookTitle(); }
    private String name(UserEntity actor) { return actor.getFullName() == null || actor.getFullName().isBlank() ? actor.getUserId().toString() : actor.getFullName(); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException("Không lưu được snapshot", e); } }
    private <T> T read(String value, Class<T> type) { try { return json.readValue(value, type); } catch (JsonProcessingException e) { throw new IllegalStateException("Không đọc được snapshot", e); } }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private static ResponseStatusException conflict(String m) { return new ResponseStatusException(HttpStatus.CONFLICT, m); }
}
