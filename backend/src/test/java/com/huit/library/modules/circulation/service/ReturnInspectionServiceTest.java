package com.huit.library.modules.circulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.circulation.dto.ReturnInspectionRequestDTO;
import com.huit.library.modules.circulation.dto.ReturnTarget;
import com.huit.library.modules.circulation.entity.CirculationOperationEntity;
import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.repository.CirculationOperationRepository;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.penalty.dto.PenaltyCaseRequestDTO;
import com.huit.library.modules.penalty.service.PenaltyCaseService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReturnInspectionServiceTest {
    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private final UUID readerId = UUID.randomUUID();
    private CirculationOperationRepository operations;
    private LoanDetailRepository details;
    private CirculationStateService circulation;
    private PenaltyCaseService penalties;
    private LibraryHoursService hours;
    private ReturnInspectionService service;
    private final AtomicReference<CirculationOperationEntity> receipt = new AtomicReference<>();

    @AfterAll
    static void closeValidators() { VALIDATORS.close(); }

    @BeforeEach
    void setUp() {
        operations = mock(CirculationOperationRepository.class);
        details = mock(LoanDetailRepository.class);
        var tickets = mock(LoanTicketRepository.class);
        var copies = mock(BookCopyRepository.class);
        var users = mock(UserRepository.class);
        circulation = mock(CirculationStateService.class);
        penalties = mock(PenaltyCaseService.class);
        hours = mock(LibraryHoursService.class);
        service = new ReturnInspectionService(tickets, details, copies, users, mock(WalletRepository.class),
                operations, circulation, penalties, hours, new ObjectMapper().findAndRegisterModules(), VALIDATORS.getValidator());

        var ticket = new LoanTicketEntity();
        ticket.setTicketId(10L);
        ticket.setUserId(readerId);
        ticket.setStatus("BORROWING");
        var detail = new LoanDetailEntity();
        detail.setDetailId(20L);
        detail.setTicketId(10L);
        detail.setBarcode("COPY-20");
        detail.setReturnStatus("BORROWING");
        var copy = new BookCopyEntity();
        copy.setBarcode("COPY-20");
        copy.setStatus("BORROWING");
        when(details.findReturnTargets(List.of(20L)))
                .thenReturn(List.of(new ReturnTarget(20L, 10L, "COPY-20", readerId)));
        when(tickets.findByIdForUpdate(10L)).thenReturn(Optional.of(ticket));
        when(users.findByIdForUpdate(readerId)).thenReturn(Optional.of(new UserEntity()));
        when(details.findByTicketIdForUpdate(10L)).thenReturn(List.of(detail));
        when(copies.findAllByBarcodeInForUpdate(List.of("COPY-20"))).thenReturn(List.of(copy));
        when(operations.findByIdempotencyKey(anyString())).thenAnswer(call -> Optional.ofNullable(receipt.get()));
        when(operations.saveAndFlush(any())).thenAnswer(call -> {
            CirculationOperationEntity value = call.getArgument(0);
            value.setProcessedAt(LocalDateTime.of(2026, 10, 29, 9, 0));
            receipt.set(value);
            return value;
        });
        when(circulation.receiveNormalReturn(eq(ticket), eq(detail), eq(copy), any(), any())).thenAnswer(call -> {
            detail.setReturnStatus("RETURNED");
            detail.setReturnDate(call.getArgument(4));
            copy.setStatus("AVAILABLE");
            return BigDecimal.ZERO;
        });
    }

    @Test
    void exactRetryReplaysImmutableReceiptEvenWhenCounterCloses() {
        var first = service.inspect(normalRequest(), UUID.randomUUID());
        doThrow(new IllegalStateException("closed")).when(hours).requireCounterOpen(anyString());
        var replay = service.inspect(normalRequest(), UUID.randomUUID());
        assertThat(replay.replayed()).isTrue();
        assertThat(first.replayed()).isFalse();
        assertThat(replay.operationId()).isEqualTo(first.operationId());
        assertThat(replay.items()).isEqualTo(first.items());
        assertThat(receipt.get().getResultPayload()).contains("COPY-20", "RETURNED");
        verify(circulation, times(1)).receiveNormalReturn(any(), any(), any(), any(), any());
        verifyNoInteractions(penalties);
    }

    @Test
    void keyCannotBeReusedWithChangedNoteOrAnotherOperationType() {
        service.inspect(normalRequest(), UUID.randomUUID());
        var changed = normalRequest();
        changed.getItems().get(0).setReturnConditionNote("Different note");
        assertThatThrownBy(() -> service.inspect(changed, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("yêu cầu khác");
        receipt.get().setOperationType("RETURN");
        assertThatThrownBy(() -> service.inspect(normalRequest(), UUID.randomUUID()))
                .hasMessageContaining("yêu cầu khác");
        verify(circulation, times(1)).receiveNormalReturn(any(), any(), any(), any(), any());
    }

    @Test
    void duplicateIdsAndInvalidIncidentCannotReachFinancialOrInventoryServices() {
        var duplicate = normalRequest();
        duplicate.setItems(List.of(duplicate.getItems().get(0), duplicate.getItems().get(0)));
        assertThatThrownBy(() -> service.inspect(duplicate, UUID.randomUUID())).hasMessageContaining("bị trùng");
        var missing = normalRequest();
        missing.getItems().get(0).setCondition(ReturnInspectionRequestDTO.Condition.LOST);
        assertThatThrownBy(() -> service.inspect(missing, UUID.randomUUID())).hasMessageContaining("Phiếu phạt");
        var unexpected = normalRequest();
        unexpected.getItems().get(0).setPenalty(new PenaltyCaseRequestDTO());
        assertThatThrownBy(() -> service.inspect(unexpected, UUID.randomUUID())).hasMessageContaining("không hợp lệ");
        verifyNoInteractions(operations, circulation, penalties);
    }

    @Test
    void corruptStoredReceiptFailsClosedInsteadOfReapplyingReturn() {
        service.inspect(normalRequest(), UUID.randomUUID());
        receipt.get().setResultPayload("broken-json");
        assertThatThrownBy(() -> service.inspect(normalRequest(), UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Biên nhận");
        verify(circulation, times(1)).receiveNormalReturn(any(), any(), any(), any(), any());
    }

    private ReturnInspectionRequestDTO normalRequest() {
        var item = new ReturnInspectionRequestDTO.Item();
        item.setLoanDetailId(20L);
        item.setCondition(ReturnInspectionRequestDTO.Condition.NORMAL);
        var request = new ReturnInspectionRequestDTO();
        request.setIdempotencyKey("inspection-1");
        request.setItems(List.of(item));
        return request;
    }
}
