package com.huit.library.modules.circulation.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.circulation.dto.ReturnInspectionRequestDTO;
import com.huit.library.modules.circulation.dto.ReturnInspectionResponseDTO;
import com.huit.library.modules.circulation.service.ReturnInspectionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/loans")
public class ReturnInspectionController {
    private final ReturnInspectionService service;

    public ReturnInspectionController(ReturnInspectionService service) {
        this.service = service;
    }

    @PostMapping("/return-inspection")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Inspect returned or lost items and create penalty cases atomically",
            description = "Up to three loanDetailIds, including lost items without a physical barcode scan. "
                    + "NORMAL returns to stock; DAMAGED/LOST creates a case using existing policy. "
                    + "Retry the same idempotency key and payload to replay the original receipt.")
    public ReturnInspectionResponseDTO inspect(
            @Valid @RequestBody ReturnInspectionRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails operator) {
        return service.inspect(request, operator.getUser().getUserId());
    }

    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    public ProblemDetail concurrentConflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Giao dịch xung đột với thao tác khác. Hãy thử lại cùng idempotency key và nội dung ban đầu.");
    }
}
