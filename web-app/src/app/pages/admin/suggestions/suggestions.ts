import { ChangeDetectorRef, Component, OnInit, Optional } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProposalService, BookProposal } from '../../../services/proposal.service';
import { ActivatedRoute } from '@angular/router';
import { ProposalSummary } from './proposal-summary';

@Component({
  selector: 'app-admin-suggestions',
  standalone: true,
  imports: [CommonModule, FormsModule, ProposalSummary],
  templateUrl: './suggestions.html',
  styleUrls: ['./suggestions.css']
})
export class AdminSuggestions implements OnInit {
  view: 'individual' | 'summary' = 'individual';
  summaryLoaded = false;
  activeTab: 'PENDING' | 'APPROVED' | 'REJECTED' | 'ALL' = 'PENDING';
  selectedSuggestion: BookProposal | null = null;
  isDrawerOpen = false;
  isLoading = false;
  isUpdating = false;
  searchTerm = '';
  adminNoteInput = '';
  alertMessage: { type: 'success' | 'error'; text: string } | null = null;

  proposals: BookProposal[] = [];

  private requestedProposalId?: number;

  constructor(
    private proposalService: ProposalService,
    private cdr: ChangeDetectorRef,
    @Optional() private route?: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    const referenceId = this.route?.snapshot.queryParamMap.get('referenceId');
    this.requestedProposalId = referenceId ? Number(referenceId) : undefined;
    this.loadProposals();
  }

  loadProposals(): void {
    this.isLoading = true;
    this.proposalService.getAllProposalsForAdmin().subscribe({
      next: (data) => {
        this.proposals = data || [];
        this.isLoading = false;
        if (this.requestedProposalId) {
          const requested = this.proposals.find(item => item.proposalId === this.requestedProposalId);
          if (requested) {
            this.activeTab = 'ALL';
            this.openDrawer(requested);
          }
          this.requestedProposalId = undefined;
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Lỗi khi tải danh sách đề xuất:', err);
        this.isLoading = false;
        this.showAlert('error', 'Không thể tải danh sách đề xuất. Vui lòng thử lại!');
        this.cdr.detectChanges();
      }
    });
  }

  get pendingCount(): number {
    return this.proposals.filter(s => s.status === 'PENDING').length;
  }

  get approvedCount(): number {
    return this.proposals.filter(s => s.status === 'APPROVED').length;
  }

  get rejectedCount(): number {
    return this.proposals.filter(s => s.status === 'REJECTED').length;
  }

  get filteredSuggestions(): BookProposal[] {
    let result = this.proposals;

    if (this.activeTab !== 'ALL') {
      result = result.filter(s => s.status === this.activeTab);
    }

    if (this.searchTerm.trim()) {
      const q = this.searchTerm.trim().toLowerCase();
      result = result.filter(s =>
        (s.title && s.title.toLowerCase().includes(q)) ||
        (s.authorName && s.authorName.toLowerCase().includes(q)) ||
        (s.userName && s.userName.toLowerCase().includes(q)) ||
        (s.userMssv && s.userMssv.toLowerCase().includes(q)) ||
        (s.userEmail && s.userEmail.toLowerCase().includes(q)) ||
        (s.publisher && s.publisher.toLowerCase().includes(q))
      );
    }

    return result;
  }

  setTab(tab: 'PENDING' | 'APPROVED' | 'REJECTED' | 'ALL'): void {
    this.activeTab = tab;
  }

  showSummary(): void { this.summaryLoaded = true; this.view = 'summary'; }
  openSummaryProposal(id: number): void {
    const proposal = this.proposals.find(p => p.proposalId === id);
    if (!proposal) { this.showAlert('error', 'Đề xuất chưa có trong danh sách hiện tại. Làm mới danh sách để xem.'); return; }
    this.view = 'individual'; this.activeTab = 'ALL'; this.searchTerm = ''; this.openDrawer(proposal);
  }

  openDrawer(suggestion: BookProposal): void {
    this.selectedSuggestion = suggestion;
    this.adminNoteInput = suggestion.adminNote || '';
    this.isDrawerOpen = true;
    document.body.style.overflow = 'hidden';
  }

  closeDrawer(): void {
    this.isDrawerOpen = false;
    document.body.style.overflow = '';
    setTimeout(() => {
      this.selectedSuggestion = null;
      this.adminNoteInput = '';
    }, 300);
  }

  approveSuggestion(): void {
    if (!this.selectedSuggestion || !this.selectedSuggestion.proposalId) return;

    this.isUpdating = true;
    this.proposalService.updateProposalStatus(
      this.selectedSuggestion.proposalId,
      'APPROVED',
      this.adminNoteInput.trim() || undefined
    ).subscribe({
      next: (updated) => {
        this.isUpdating = false;
        if (this.selectedSuggestion) {
          this.selectedSuggestion.status = 'APPROVED';
          this.selectedSuggestion.adminNote = updated.adminNote || this.adminNoteInput.trim();
        }
        // Update in list
        const index = this.proposals.findIndex(p => p.proposalId === updated.proposalId);
        if (index !== -1) {
          this.proposals[index] = { ...this.proposals[index], ...updated };
        }
        this.showAlert('success', 'Đã duyệt đề xuất sách thành công!');
        this.closeDrawer();
      },
      error: (err) => {
        this.isUpdating = false;
        console.error('Lỗi khi duyệt đề xuất:', err);
        this.showAlert('error', this.extractError(err, 'Có lỗi xảy ra khi duyệt đề xuất!'));
      }
    });
  }

  rejectSuggestion(): void {
    if (!this.selectedSuggestion || !this.selectedSuggestion.proposalId) return;
    if (!this.adminNoteInput.trim()) {
      this.showAlert('error', 'Vui lòng nhập lý do từ chối để phản hồi cho sinh viên.');
      return;
    }

    this.isUpdating = true;
    this.proposalService.updateProposalStatus(
      this.selectedSuggestion.proposalId,
      'REJECTED',
      this.adminNoteInput.trim() || undefined
    ).subscribe({
      next: (updated) => {
        this.isUpdating = false;
        if (this.selectedSuggestion) {
          this.selectedSuggestion.status = 'REJECTED';
          this.selectedSuggestion.adminNote = updated.adminNote || this.adminNoteInput.trim();
        }
        // Update in list
        const index = this.proposals.findIndex(p => p.proposalId === updated.proposalId);
        if (index !== -1) {
          this.proposals[index] = { ...this.proposals[index], ...updated };
        }
        this.showAlert('success', 'Đã từ chối đề xuất sách.');
        this.closeDrawer();
      },
      error: (err) => {
        this.isUpdating = false;
        console.error('Lỗi khi từ chối đề xuất:', err);
        this.showAlert('error', this.extractError(err, 'Có lỗi xảy ra khi từ chối đề xuất!'));
      }
    });
  }

  urgencyLabel(level?: string): string {
    return ({ LOW: 'Thấp', NORMAL: 'Bình thường', HIGH: 'Cao' } as Record<string, string>)[level || 'NORMAL'] || 'Bình thường';
  }

  private extractError(error: any, fallback: string): string {
    const body = error?.error;
    const message = typeof body === 'string' ? body : body?.detail ?? body?.message ?? body?.error;
    return typeof message === 'string' && message.trim() ? message : fallback;
  }

  private showAlert(type: 'success' | 'error', text: string): void {
    this.alertMessage = { type, text };
    setTimeout(() => {
      this.alertMessage = null;
    }, 4000);
  }
}
