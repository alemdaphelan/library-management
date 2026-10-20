package com.huit.library.modules.circulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.circulation.dto.LoanItemDTO;
import com.huit.library.modules.circulation.dto.LoanTicketDetailDTO;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LibrarianQrServiceTest {
    private static final String SECRET = "test-secret-with-at-least-thirty-two-characters";
    private static final Instant NOW = Instant.parse("2026-10-18T08:00:00Z");

    private LoanService loanService;
    private LoanTicketRepository loanTicketRepository;
    private UserRepository userRepository;
    private WalletRepository walletRepository;
    private LibrarianQrService service;

    @BeforeEach
    void setUp() {
        loanService = mock(LoanService.class);
        loanTicketRepository = mock(LoanTicketRepository.class);
        userRepository = mock(UserRepository.class);
        walletRepository = mock(WalletRepository.class);
        service = createService(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void resolvesValidSignedTicketAndReturnsServerControlledAction() {
        UUID userId = UUID.randomUUID();
        UserEntity reader = user(userId, "READER");
        LoanTicketEntity ticket = ticket(91L, userId, "RESERVED");
        LoanItemDTO item = LoanItemDTO.builder().barcode("BC-001").status("RESERVED").build();

        when(loanTicketRepository.findById(91L)).thenReturn(Optional.of(ticket));
        when(loanService.getLoan(91L)).thenReturn(LoanTicketDetailDTO.builder()
                .ticketId(91L)
                .userId(userId)
                .readerName("Nguyen Van A")
                .mssv("20010001")
                .status("RESERVED")
                .items(List.of(item))
                .build());

        var issued = service.issueTicketPayload(91L, reader);
        var resolved = service.resolve(issued.getPayload());

        assertThat(issued.getPayload()).startsWith("HUITQR1.");
        assertThat(resolved.getTicketId()).isEqualTo(91L);
        assertThat(resolved.getAllowedActions()).containsExactly("CHECKOUT");
        assertThat(resolved.getItems()).extracting(LoanItemDTO::getBarcode).containsExactly("BC-001");
    }

    @Test
    void rejectsPayloadWhenSignatureWasChanged() {
        UUID userId = UUID.randomUUID();
        when(loanTicketRepository.findById(5L)).thenReturn(Optional.of(ticket(5L, userId, "PENDING")));
        String payload = service.issueTicketPayload(5L, user(userId, "READER")).getPayload();
        char replacement = payload.endsWith("A") ? 'B' : 'A';
        String tampered = payload.substring(0, payload.length() - 1) + replacement;

        assertThatThrownBy(() -> service.resolve(tampered))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsExpiredPayload() {
        UUID userId = UUID.randomUUID();
        when(loanTicketRepository.findById(7L)).thenReturn(Optional.of(ticket(7L, userId, "PENDING")));
        String payload = service.issueTicketPayload(7L, user(userId, "READER")).getPayload();
        LibrarianQrService later = createService(
                Clock.fixed(NOW.plusSeconds(11 * 60), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.resolve(payload))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.GONE));
    }

    @Test
    void readerCannotIssueQrForAnotherUsersTicket() {
        UUID ownerId = UUID.randomUUID();
        when(loanTicketRepository.findById(8L)).thenReturn(Optional.of(ticket(8L, ownerId, "PENDING")));

        assertThatThrownBy(() -> service.issueTicketPayload(8L, user(UUID.randomUUID(), "READER")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    private LibrarianQrService createService(Clock clock) {
        return new LibrarianQrService(new ObjectMapper(), loanService, loanTicketRepository,
                userRepository, walletRepository, SECRET, 10, clock);
    }

    private UserEntity user(UUID userId, String role) {
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setRoleId(role);
        return user;
    }

    private LoanTicketEntity ticket(Long ticketId, UUID userId, String status) {
        LoanTicketEntity ticket = new LoanTicketEntity();
        ticket.setTicketId(ticketId);
        ticket.setUserId(userId);
        ticket.setStatus(status);
        return ticket;
    }
}
