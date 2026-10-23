package com.huit.library.modules.interactive.service;

import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.catalog.repository.CategoryRepository;
import com.huit.library.modules.interactive.dto.UpdateProposalStatusDTO;
import com.huit.library.modules.interactive.entity.BookProposalEntity;
import com.huit.library.modules.interactive.repository.BookProposalRepository;
import com.huit.library.modules.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookProposalServiceTest {
    private BookProposalRepository proposalRepository;
    private BookRepository bookRepository;
    private BookProposalService service;

    @BeforeEach
    void setUp() {
        proposalRepository = mock(BookProposalRepository.class);
        bookRepository = mock(BookRepository.class);
        service = new BookProposalService(
                proposalRepository,
                mock(UserRepository.class),
                mock(NotificationService.class),
                bookRepository,
                mock(CategoryRepository.class));
    }

    @Test
    void approvingCreatesCatalogBookWithoutCreatingCopies() {
        BookProposalEntity proposal = proposal();
        proposal.setIsbn("9780132350884");
        proposal.setPublicationYear(2024);
        proposal.setEdition("Tái bản lần 3");
        proposal.setEstimatedPrice(new BigDecimal("150000"));
        proposal.setExternalUrl("https://example.com/book");
        when(proposalRepository.findById(7L)).thenReturn(Optional.of(proposal));
        when(bookRepository.findFirstByIsbn(proposal.getIsbn())).thenReturn(Optional.empty());
        when(bookRepository.save(any(BookEntity.class))).thenAnswer(invocation -> {
            BookEntity book = invocation.getArgument(0);
            book.setBookId(99L);
            return book;
        });
        when(proposalRepository.save(any(BookProposalEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProposalStatusDTO request = new UpdateProposalStatusDTO();
        request.setStatus("APPROVED");
        request.setAdminNote("Phù hợp chương trình đào tạo");

        var result = service.updateStatus(7L, request);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getApprovedBookId()).isEqualTo(99L);
        ArgumentCaptor<BookEntity> bookCaptor = ArgumentCaptor.forClass(BookEntity.class);
        verify(bookRepository).save(bookCaptor.capture());
        assertThat(bookCaptor.getValue().getTitle()).isEqualTo("Clean Architecture");
        assertThat(bookCaptor.getValue().getDefaultPrice()).isEqualByComparingTo("150000");
        assertThat(bookCaptor.getValue().getDescription()).contains("chưa có bản sao");
    }

    @Test
    void rejectionRequiresReasonAndDoesNotCreateBook() {
        when(proposalRepository.findById(7L)).thenReturn(Optional.of(proposal()));
        UpdateProposalStatusDTO request = new UpdateProposalStatusDTO();
        request.setStatus("REJECTED");
        request.setAdminNote(" ");

        assertThatThrownBy(() -> service.updateStatus(7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lý do");
    }

    private BookProposalEntity proposal() {
        BookProposalEntity proposal = new BookProposalEntity();
        proposal.setProposalId(7L);
        proposal.setTitle("Clean Architecture");
        proposal.setAuthorName("Robert C. Martin");
        proposal.setPublisher("Prentice Hall");
        proposal.setReason("Phục vụ môn kiến trúc phần mềm");
        proposal.setUrgencyLevel("NORMAL");
        proposal.setStatus("PENDING");
        return proposal;
    }
}
