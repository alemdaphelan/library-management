package com.huit.library.modules.auth.service;

import com.huit.library.modules.auth.dto.ReaderProfileResponseDTO;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.circulation.service.LoanService;
import com.huit.library.modules.finance.service.DebtLedgerService;
import com.huit.library.modules.penalty.service.PenaltyCaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReaderProfileService {
    private final UserService userService;
    private final LibraryCardService libraryCardService;
    private final LoanService loanService;
    private final DebtLedgerService debtLedgerService;
    private final PenaltyCaseService penaltyCaseService;

    public ReaderProfileService(
            UserService userService,
            LibraryCardService libraryCardService,
            LoanService loanService,
            DebtLedgerService debtLedgerService,
            PenaltyCaseService penaltyCaseService) {
        this.userService = userService;
        this.libraryCardService = libraryCardService;
        this.loanService = loanService;
        this.debtLedgerService = debtLedgerService;
        this.penaltyCaseService = penaltyCaseService;
    }

    @Transactional(readOnly = true)
    public ReaderProfileResponseDTO getByMssv(String mssv) {
        UserEntity student = userService.getStudentByMssv(mssv);
        if (!"STUDENT".equalsIgnoreCase(student.getUserType())) {
            throw new IllegalArgumentException("Tài khoản không phải là bạn đọc sinh viên");
        }
        var userId = student.getUserId();
        return ReaderProfileResponseDTO.builder()
                .student(student)
                .card(libraryCardService.get(userId))
                .loans(loanService.getStaffLoans().stream()
                        .filter(ticket -> userId.equals(ticket.getUserId()))
                        .toList())
                .ledger(debtLedgerService.getUserLedger(userId))
                .penaltyCases(penaltyCaseService.listMyCases(userId))
                .build();
    }
}
