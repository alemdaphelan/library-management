import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ProposalService, BookProposal } from '../../../services/proposal.service';
import { AuthService } from '../../../services/auth.service';

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
    author: '',
    publisher: '',
    link: '',
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
    private router: Router
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
    this.proposalService.getMyProposals().subscribe({
      next: (list) => {
        this.myProposals = list;
        this.isLoadingHistory = false;
      },
      error: (err) => {
        console.error('Lỗi tải lịch sử đề xuất:', err);
        this.isLoadingHistory = false;
      }
    });
  }

  onSubmit() {
    if (!this.suggestion.title.trim() || !this.suggestion.reason.trim()) {
      this.errorMessage = 'Vui lòng nhập tên tài liệu và lý do đề xuất!';
      return;
    }

    if (!this.authService.isLoggedIn()) {
      this.errorMessage = 'Vui lòng đăng nhập để gửi đề xuất mua sách!';
      return;
    }

    this.isSubmitting = true;
    this.errorMessage = '';

    const payload = {
      title: this.suggestion.title.trim(),
      authorName: this.suggestion.author.trim(),
      publisher: this.suggestion.publisher.trim(),
      reason: this.suggestion.reason.trim(),
      note: this.suggestion.link.trim()
    };

    this.proposalService.submitProposal(payload).subscribe({
      next: (res) => {
        this.isSubmitting = false;
        this.isSubmitted = true;
        this.suggestion = { title: '', author: '', publisher: '', link: '', reason: '' };
        this.loadMyProposals();
        setTimeout(() => {
          this.isSubmitted = false;
        }, 5000);
      },
      error: (err) => {
        this.isSubmitting = false;
        console.error('Error submitting proposal:', err);
        if (typeof err.error === 'string') {
          this.errorMessage = err.error;
        } else if (err.error && typeof err.error.message === 'string') {
          this.errorMessage = err.error.message;
        } else if (err.error && typeof err.error.error === 'string') {
          this.errorMessage = err.error.error;
        } else if (err.status === 401 || err.status === 403) {
          this.errorMessage = 'Phiên đăng nhập đã hết hạn hoặc không có quyền. Vui lòng đăng nhập lại!';
        } else {
          this.errorMessage = 'Có lỗi xảy ra khi gửi đề xuất. Vui lòng thử lại sau!';
        }
      }
    });
  }

  goToLogin() {
    this.router.navigate(['/login']);
  }
}
