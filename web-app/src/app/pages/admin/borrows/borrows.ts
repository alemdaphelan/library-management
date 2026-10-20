import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CirculationService,
  LoanTicketDetail,
  LoanTicketStatus,
  LoanTicketSummary,
} from '../../../services/circulation.service';

@Component({
  selector: 'app-borrows',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './borrows.html',
  styleUrl: './borrows.css',
})
export class Borrows implements OnInit {
  loans: LoanTicketSummary[] = [];
  selectedLoan: LoanTicketDetail | null = null;
  searchTerm = '';
  statusFilter = 'ALL';
  isLoading = false;
  detailLoading = false;
  actionInProgress = false;
  errorMessage = '';
  successMessage = '';
  rejectionReason = '';

  constructor(private circulationService: CirculationService) {}

  ngOnInit(): void {
    this.loadLoans();
  }

  get filteredLoans(): LoanTicketSummary[] {
    const query = this.searchTerm.trim().toLocaleLowerCase('vi');
    return this.loans.filter(loan => {
      const matchesStatus = this.statusFilter === 'ALL' || loan.status === this.statusFilter;
      const searchable = [loan.ticketId, loan.mssv, loan.readerName, loan.department]
        .filter(value => value !== null && value !== undefined)
        .join(' ')
        .toLocaleLowerCase('vi');
      return matchesStatus && (!query || searchable.includes(query));
    });
  }

  get activeCount(): number {
    return this.loans.filter(loan => loan.status === 'BORROWING' || loan.status === 'OVERDUE').length;
  }

  get overdueCount(): number {
    return this.loans.filter(loan => loan.status === 'OVERDUE' || loan.overdueItemCount > 0).length;
  }

  get outstandingFine(): number {
    return this.loans.reduce((sum, loan) => sum + Number(loan.totalLateFine || 0), 0);
  }

  loadLoans(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.circulationService.getStaffLoans().subscribe({
      next: loans => {
        this.loans = loans || [];
        this.isLoading = false;
      },
      error: error => {
        this.isLoading = false;
        this.errorMessage = this.extractError(error, 'Không thể tải danh sách phiếu mượn.');
      },
    });
  }

  viewDetails(ticketId: number): void {
    this.detailLoading = true;
    this.errorMessage = '';
    this.rejectionReason = '';
    this.circulationService.getLoan(ticketId).subscribe({
      next: loan => {
        this.selectedLoan = loan;
        this.detailLoading = false;
      },
      error: error => {
        this.detailLoading = false;
        this.errorMessage = this.extractError(error, `Không thể tải phiếu #${ticketId}.`);
      },
    });
  }

  closeDetails(): void {
    this.selectedLoan = null;
    this.rejectionReason = '';
  }

  approveSelected(): void {
    if (!this.selectedLoan || this.selectedLoan.status !== 'PENDING' || this.actionInProgress) return;
    const ticketId = this.selectedLoan.ticketId;
    this.actionInProgress = true;
    this.errorMessage = '';
    this.circulationService.approveReservation(ticketId).subscribe({
      next: result => {
        this.actionInProgress = false;
        this.successMessage = result.message || `Đã duyệt phiếu #${ticketId}.`;
        this.closeDetails();
        this.loadLoans();
      },
      error: error => {
        this.actionInProgress = false;
        this.errorMessage = this.extractError(error, `Không thể duyệt phiếu #${ticketId}.`);
      },
    });
  }

  rejectSelected(): void {
    const reason = this.rejectionReason.trim();
    if (!this.selectedLoan || !['PENDING', 'RESERVED'].includes(this.selectedLoan.status) || this.actionInProgress) return;
    if (!reason) {
      this.errorMessage = 'Cần nhập lý do từ chối phiếu.';
      return;
    }
    const ticketId = this.selectedLoan.ticketId;
    this.actionInProgress = true;
    this.errorMessage = '';
    this.circulationService.rejectReservation(ticketId, reason).subscribe({
      next: result => {
        this.actionInProgress = false;
        this.successMessage = result.message || `Đã từ chối phiếu #${ticketId}.`;
        this.closeDetails();
        this.loadLoans();
      },
      error: error => {
        this.actionInProgress = false;
        this.errorMessage = this.extractError(error, `Không thể từ chối phiếu #${ticketId}.`);
      },
    });
  }

  statusLabel(status: LoanTicketStatus | string): string {
    return ({
      PENDING: 'Chờ duyệt',
      RESERVED: 'Đã duyệt',
      BORROWING: 'Đang mượn',
      OVERDUE: 'Quá hạn',
      RETURNED: 'Đã trả',
      CANCELLED: 'Đã hủy',
      REJECTED: 'Từ chối',
    } as Record<string, string>)[status] || status;
  }

  statusClass(status: LoanTicketStatus | string): string {
    if (status === 'OVERDUE' || status === 'REJECTED') return 'badge-locked';
    if (status === 'PENDING' || status === 'RESERVED') return 'badge-warn';
    if (status === 'BORROWING') return 'badge-borrowing';
    return 'badge-ok';
  }

  private extractError(error: any, fallback: string): string {
    const message = error?.error?.message ?? error?.error;
    return typeof message === 'string' && message.trim() ? message : fallback;
  }
}
