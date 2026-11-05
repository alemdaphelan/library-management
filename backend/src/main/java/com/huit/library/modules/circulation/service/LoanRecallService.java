package com.huit.library.modules.circulation.service;

import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.circulation.dto.LoanItemDTO;
import com.huit.library.modules.circulation.dto.LoanRecallModels.*;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.notification.service.NotificationService;
import com.huit.library.modules.policy.service.LibraryPolicyService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class LoanRecallService {
    private final LoanTicketRepository tickets;
    private final LoanDetailRepository details;
    private final UserRepository users;
    private final LoanService loans;
    private final NotificationService notifications;
    private final NamedParameterJdbcTemplate jdbc;
    private final LibraryPolicyService policy;

    public LoanRecallService(LoanTicketRepository tickets, LoanDetailRepository details, UserRepository users,
            LoanService loans, NotificationService notifications, NamedParameterJdbcTemplate jdbc,
            LibraryPolicyService policy) {
        this.tickets = tickets; this.details = details; this.users = users; this.loans = loans;
        this.notifications = notifications; this.jdbc = jdbc; this.policy = policy;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page list(String query, Filter filter, int page, int size) {
        String q = query == null ? "" : query.strip();
        if (q.length() > 200 || page < 0 || page > 100000 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bộ lọc hoặc phân trang không hợp lệ");
        String from = """
                FROM loan_details d JOIN loan_tickets t ON t.ticket_id=d.ticket_id
                LEFT JOIN users u ON u.user_id=t.user_id
                LEFT JOIN book_copies c ON c.barcode=d.barcode
                LEFT JOIN books b ON b.book_id=COALESCE(d.circulation_book_id,c.book_id)
                WHERE t.status IN ('BORROWING','OVERDUE') AND d.return_status IN ('BORROWING','OVERDUE')
                  AND d.return_date IS NULL AND d.physical_return_at IS NULL AND d.fine_stopped_at IS NULL
                  AND (:q='' OR position(lower(:q) in lower(concat_ws(' ',u.mssv,u.full_name,d.barcode,b.title,t.ticket_id::text)))>0)
                """;
        from += switch (filter) {
            case NOT_RECALLED -> " AND coalesce(d.is_recalled,false)=false ";
            case RECALLED -> " AND d.is_recalled=true ";
            case OVERDUE -> " AND d.due_date < :now ";
            default -> " ";
        };
        var args = new MapSqlParameterSource().addValue("q",q).addValue("now",now())
                .addValue("limit",size).addValue("offset",page*size);
        long total = jdbc.queryForObject("SELECT count(*) " + from, args, Long.class);
        var items = jdbc.query("""
                SELECT d.ticket_id,d.detail_id,u.user_id,u.mssv,u.full_name,b.book_id,
                       COALESCE(b.title,concat('Tài liệu ',d.barcode)) AS book_title,d.barcode,d.due_date,d.return_status,
                       coalesce(d.is_recalled,false) AS is_recalled,
                       coalesce(d.recall_requested_at,d.recalled_at) AS recall_requested_at,d.recall_reason,
                       coalesce(d.recall_due_date,d.recall_due_at) AS recall_due_date,d.loss_reported_at
                """ + from + " ORDER BY d.due_date NULLS LAST,d.detail_id LIMIT :limit OFFSET :offset", args,
                (r,n) -> new Item(r.getLong("ticket_id"),r.getLong("detail_id"),r.getObject("user_id",UUID.class),
                        r.getString("mssv"),r.getString("full_name"),r.getObject("book_id",Long.class),r.getString("book_title"),
                        r.getString("barcode"),r.getObject("due_date",LocalDateTime.class),r.getString("return_status"),
                        r.getBoolean("is_recalled"),r.getObject("recall_requested_at",LocalDateTime.class),r.getString("recall_reason"),
                        r.getObject("recall_due_date",LocalDateTime.class),r.getObject("loss_reported_at",LocalDateTime.class)));
        return new Page(items,total,page,size);
    }

    @Transactional
    public LoanItemDTO request(Long ticketId, Long detailId, String reason, UUID actor) {
        if (reason == null || reason.isBlank() || reason.length() > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhập lý do yêu cầu trả, tối đa 1000 ký tự");
        String normalized = reason.strip();
        var ticket = tickets.findByIdForUpdate(ticketId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy phiếu mượn"));
        var detail = details.findByTicketIdForUpdate(ticketId).stream().filter(d -> detailId.equals(d.getDetailId()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy tài liệu trong phiếu"));
        if (detail.getRecallRequestedAt() != null) {
            if (!normalized.equals(detail.getRecallReason()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Tài liệu đã có yêu cầu trả với lý do khác; tải lại để xem yêu cầu gốc");
            return item(ticketId,detailId);
        }
        if (Boolean.TRUE.equals(detail.getIsRecalled()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Tài liệu đã có lệnh thu hồi cũ; cần đối chiếu hồ sơ");
        if (!("BORROWING".equals(ticket.getStatus()) || "OVERDUE".equals(ticket.getStatus()))
                || !("BORROWING".equals(detail.getReturnStatus()) || "OVERDUE".equals(detail.getReturnStatus()))
                || detail.getReturnDate()!=null || detail.getPhysicalReturnAt()!=null || detail.getFineStoppedAt()!=null
                || detail.getLossReportedAt()!=null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Tài liệu không còn ở trạng thái có thể yêu cầu trả");
        if (detail.getDueDate()==null || ticket.getUserId()==null || !users.existsById(ticket.getUserId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Chưa đủ thông tin bạn đọc/hạn trả để đối chiếu");
        var requestedAt = now();
        var oldDue = detail.getDueDate();
        var deadline = requestedAt.plusDays(policy.recallReturnDays());
        var due = oldDue.isBefore(deadline) ? oldDue : deadline;
        detail.recordRecall(requestedAt, actor, normalized, oldDue, due);
        details.saveAndFlush(detail);
        notifications.createOnce(ticket.getUserId(),"LOAN_RECALLED",
                preview("Thư viện yêu cầu trả " + preview(detail.getBarcode(),32) + " (phiếu #" + ticketId
                        + ") trước " + due.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        + ". Lý do: " + preview(normalized,70) + ". Xem đầy đủ trong Lịch sử; không được gia hạn.",255),
                "LOAN_TICKET",ticketId.toString(),"LOAN_RECALLED:"+detailId,requestedAt);
        return item(ticketId,detailId);
    }
    private LoanItemDTO item(Long ticketId, Long detailId) {
        return loans.getLoan(ticketId).getItems().stream().filter(i -> detailId.equals(i.getDetailId())).findFirst().orElseThrow();
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).truncatedTo(ChronoUnit.MICROS); }
    private String preview(String text,int max) {
        if (text==null) return "Chưa có mã vạch";
        return text.codePointCount(0,text.length())<=max ? text : text.substring(0,text.offsetByCodePoints(0,max-1))+"…";
    }
}
