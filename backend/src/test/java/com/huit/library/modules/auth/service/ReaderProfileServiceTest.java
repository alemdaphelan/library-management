package com.huit.library.modules.auth.service;

import com.huit.library.modules.auth.dto.LibraryCardResponseDTO;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.circulation.dto.LoanTicketSummaryDTO;
import com.huit.library.modules.circulation.service.LoanService;
import com.huit.library.modules.finance.entity.TransactionEntity;
import com.huit.library.modules.finance.service.DebtLedgerService;
import com.huit.library.modules.penalty.entity.PenaltyCaseEntity;
import com.huit.library.modules.penalty.service.PenaltyCaseService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReaderProfileServiceTest {
    @Test
    void aggregatesOnlyTheSelectedReadersOperationalData() {
        UserService userService = mock(UserService.class);
        LibraryCardService cardService = mock(LibraryCardService.class);
        LoanService loanService = mock(LoanService.class);
        DebtLedgerService debtService = mock(DebtLedgerService.class);
        PenaltyCaseService penaltyService = mock(PenaltyCaseService.class);
        ReaderProfileService service = new ReaderProfileService(
                userService, cardService, loanService, debtService, penaltyService);
        UUID userId = UUID.randomUUID();
        UserEntity student = new UserEntity();
        student.setUserId(userId);
        student.setUserType("STUDENT");
        student.setMssv("2001231849");
        LoanTicketSummaryDTO ownLoan = LoanTicketSummaryDTO.builder().ticketId(1L).userId(userId).build();
        TransactionEntity debt = new TransactionEntity();
        debt.setTransactionId(3L);
        PenaltyCaseEntity penaltyCase = new PenaltyCaseEntity();
        penaltyCase.setCaseId(4L);
        LibraryCardResponseDTO card = new LibraryCardResponseDTO(
                true, student.getMssv(), "Sinh viên", "CNTT", "ACTIVE",
                null, null, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        when(userService.getStudentByMssv(student.getMssv())).thenReturn(student);
        when(cardService.get(userId)).thenReturn(card);
        when(loanService.getUserLoanSummaries(userId)).thenReturn(List.of(ownLoan));
        when(debtService.getUserLedger(userId)).thenReturn(List.of(debt));
        when(penaltyService.listMyCases(userId)).thenReturn(List.of(penaltyCase));

        var result = service.getByMssv(student.getMssv());

        assertThat(result.getStudent()).isSameAs(student);
        assertThat(result.getCard()).isSameAs(card);
        assertThat(result.getLoans()).extracting(LoanTicketSummaryDTO::getTicketId).containsExactly(1L);
        assertThat(result.getLedger()).containsExactly(debt);
        assertThat(result.getPenaltyCases()).containsExactly(penaltyCase);
    }
}
