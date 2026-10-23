import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ProposalService, BookProposal } from '../../../services/proposal.service';
import { AuthService } from '../../../services/auth.service';
import { finalize } from 'rxjs/operators';

@Component({
  selector: 'app-suggest',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './suggest.html',
  styleUrls: ['./suggest.css']
})
export class Suggest implements OnInit {
  activeTab: 'form' | 'history' = 'form';

  suggestion = {
    title: '',
    authorName: '',
    publisher: '',
    isbn: '',
    publicationYear: null as number | null,
    edition: '',
    estimatedPrice: null as number | null,
    externalUrl: '',
    genre: '',
    urgencyLevel: 'NORMAL' as 'LOW' | 'NORMAL' | 'HIGH',
    reason: ''
  };

  myProposals: BookProposal[] = [];
  isLoadingHistory = false;
  isSubmitting = false;
  isSubmitted = false;
  errorMessage = '';

  constructor(
    private proposalService: ProposalService,
    public authService: AuthService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    if (this.authService.isLoggedIn()) {
      this.loadMyProposals();
    }
  }

  setTab(tab: 'form' | 'history') {
    this.activeTab = tab;
    if (tab === 'history' && this.authService.isLoggedIn()) {
      this.loadMyProposals();
    }
  }

  loadMyProposals() {
    this.isLoadingHistory = true;
    this.proposalService.getMyProposals().pipe(
      finalize(() => {
        this.isLoadingHistory = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (list) => {
        this.myProposals = list;
      },
      error: (err) => {
        console.error('Lỗi tải lịch sử đề xuất:', err);
      }
    });
  }

  onSubmit() {
    if (this.isSubmitting) {
      return;
    }

    if (!this.suggestion.title.trim() || !this.suggestion.reason.trim()) {
      this.errorMessage = 'Vui lòng nhập tên tài liệu và lý do đề xuất!';
      return;
    }

    if (!this.authService.isLoggedIn()) {
      this.errorMessage = 'Vui lòng đăng nhập để gửi đề xuất mua sách!';
      return;
    }

    this.isSubmitting = true;
    this.isSubmitted = false;
    this.errorMessage = '';

    const payload = {
      title: this.suggestion.title.trim(),
      authorName: this.suggestion.authorName.trim(),
      publisher: this.suggestion.publisher.trim(),
      isbn: this.suggestion.isbn.trim(),
      publicationYear: this.suggestion.publicationYear,
      edition: this.suggestion.edition.trim(),
      estimatedPrice: this.suggestion.estimatedPrice,
      externalUrl: this.suggestion.externalUrl.trim(),
      genre: this.suggestion.genre.trim(),
      urgencyLevel: this.suggestion.urgencyLevel,
      reason: this.suggestion.reason.trim(),
    };

    this.proposalService.submitProposal(payload).pipe(
      finalize(() => {
        this.isSubmitting = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (res) => {
        this.isSubmitted = true;
        this.suggestion = {
          title: '', authorName: '', publisher: '', isbn: '', publicationYear: null,
          edition: '', estimatedPrice: null, externalUrl: '', genre: '',
          urgencyLevel: 'NORMAL', reason: ''
        };
        this.loadMyProposals();
        setTimeout(() => {
          this.isSubmitted = false;
        }, 5000);
      },
      error: (err) => {
        console.error('Error submitting proposal:', err);
        this.errorMessage = this.extractErrorMessage(err);
      }
    });
  }

  private extractErrorMessage(err: any): string {
    if (err?.status === 401 || err?.status === 403) {
      return 'Phiên đăng nhập đã hết hạn hoặc không có quyền. Vui lòng đăng nhập lại!';
    }
    const body = err?.error;
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    const message = body?.detail ?? body?.message ?? body?.error;
    return typeof message === 'string' && message.trim()
      ? message
      : 'Có lỗi xảy ra khi gửi đề xuất. Vui lòng thử lại sau!';
  }

  goToLogin() {
    this.router.navigate(['/login']);
  }
}
