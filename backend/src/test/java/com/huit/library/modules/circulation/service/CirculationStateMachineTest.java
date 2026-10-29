package com.huit.library.modules.circulation.service;

import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.model.LoanTicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CirculationStateMachineTest {
    private final CirculationStateMachine stateMachine = new CirculationStateMachine();

    @Test
    void supportsOfficialReservationAndLoanLifecycle() {
        assertThat(stateMachine.canTransition(LoanTicketStatus.PENDING, LoanTicketStatus.RESERVED)).isTrue();
        assertThat(stateMachine.canTransition(LoanTicketStatus.RESERVED, LoanTicketStatus.BORROWING)).isTrue();
        assertThat(stateMachine.canTransition(LoanTicketStatus.BORROWING, LoanTicketStatus.OVERDUE)).isTrue();
        assertThat(stateMachine.canTransition(LoanTicketStatus.BORROWING, LoanTicketStatus.RETURNED)).isTrue();
        assertThat(stateMachine.canTransition(LoanTicketStatus.OVERDUE, LoanTicketStatus.RETURNED)).isTrue();
    }

    @Test
    void supportsCancellationAndRejectionOnlyBeforeCheckout() {
        assertThat(stateMachine.canTransition(LoanTicketStatus.PENDING, LoanTicketStatus.CANCELLED)).isTrue();
        assertThat(stateMachine.canTransition(LoanTicketStatus.PENDING, LoanTicketStatus.REJECTED)).isTrue();
        assertThat(stateMachine.canTransition(LoanTicketStatus.RESERVED, LoanTicketStatus.CANCELLED)).isTrue();
        assertThat(stateMachine.canTransition(LoanTicketStatus.RESERVED, LoanTicketStatus.REJECTED)).isTrue();
        assertThat(stateMachine.canTransition(LoanTicketStatus.BORROWING, LoanTicketStatus.CANCELLED)).isFalse();
    }

    @Test
    void terminalStatesCannotTransition() {
        for (LoanTicketStatus terminal : new LoanTicketStatus[]{
                LoanTicketStatus.RETURNED,
                LoanTicketStatus.CANCELLED,
                LoanTicketStatus.REJECTED}) {
            for (LoanTicketStatus target : LoanTicketStatus.values()) {
                assertThat(stateMachine.canTransition(terminal, target)).isFalse();
            }
        }
    }

    @Test
    void transitionNormalizesLegacyCompletedStatusAndRejectsReopening() {
        LoanTicketEntity ticket = new LoanTicketEntity();
        ticket.setStatus("COMPLETED");

        assertThatThrownBy(() -> stateMachine.transition(ticket, LoanTicketStatus.BORROWING))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("RETURNED")
                .hasMessageContaining("BORROWING");
        assertThat(ticket.getStatus()).isEqualTo("COMPLETED");
    }
}
