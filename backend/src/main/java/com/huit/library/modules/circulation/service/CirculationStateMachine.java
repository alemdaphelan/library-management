package com.huit.library.modules.circulation.service;

import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.model.LoanTicketStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class CirculationStateMachine {
    private static final Map<LoanTicketStatus, Set<LoanTicketStatus>> ALLOWED_TRANSITIONS = buildTransitions();

    public LoanTicketStatus transition(LoanTicketEntity ticket, LoanTicketStatus target) {
        LoanTicketStatus current = LoanTicketStatus.fromDatabaseValue(ticket.getStatus());
        if (!canTransition(current, target)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể chuyển phiếu mượn từ " + current + " sang " + target);
        }
        ticket.setStatus(target.name());
        return current;
    }

    public boolean canTransition(LoanTicketStatus current, LoanTicketStatus target) {
        return current != null
                && target != null
                && ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target);
    }

    private static Map<LoanTicketStatus, Set<LoanTicketStatus>> buildTransitions() {
        Map<LoanTicketStatus, Set<LoanTicketStatus>> transitions = new EnumMap<>(LoanTicketStatus.class);
        transitions.put(LoanTicketStatus.PENDING,
                EnumSet.of(LoanTicketStatus.RESERVED, LoanTicketStatus.REJECTED, LoanTicketStatus.CANCELLED));
        transitions.put(LoanTicketStatus.RESERVED,
                EnumSet.of(LoanTicketStatus.BORROWING, LoanTicketStatus.REJECTED, LoanTicketStatus.CANCELLED));
        transitions.put(LoanTicketStatus.BORROWING,
                EnumSet.of(LoanTicketStatus.OVERDUE, LoanTicketStatus.RETURNED));
        transitions.put(LoanTicketStatus.OVERDUE, EnumSet.of(LoanTicketStatus.RETURNED));
        transitions.put(LoanTicketStatus.RETURNED, EnumSet.noneOf(LoanTicketStatus.class));
        transitions.put(LoanTicketStatus.CANCELLED, EnumSet.noneOf(LoanTicketStatus.class));
        transitions.put(LoanTicketStatus.REJECTED, EnumSet.noneOf(LoanTicketStatus.class));
        return Map.copyOf(transitions);
    }
}
