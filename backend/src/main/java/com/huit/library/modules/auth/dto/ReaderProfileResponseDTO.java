package com.huit.library.modules.auth.dto;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.circulation.dto.LoanTicketSummaryDTO;
import com.huit.library.modules.finance.entity.TransactionEntity;
import com.huit.library.modules.penalty.entity.PenaltyCaseEntity;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReaderProfileResponseDTO {
    private UserEntity student;
    private LibraryCardResponseDTO card;
    private List<LoanTicketSummaryDTO> loans;
    private List<TransactionEntity> ledger;
    private List<PenaltyCaseEntity> penaltyCases;
}
