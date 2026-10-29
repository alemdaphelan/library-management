import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, Optional } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { StudentObligationReport } from './student-obligation-report';
import {
  CirculationService,
  LoanTicketDetail,
  LoanTicketStatus,
  LoanTicketSummary,
} from '../../../services/circulation.service';

@Component({
  selector: 'app-borrows',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, StudentObligationReport],
  templateUrl: './borrows.html',
  styleUrl: './borrows.css',
})
export class Borrows implements OnInit {
  activeView: 'loans' | 'report' = 'loans';
  reportVisited = false;
  reportInitialQuery = '';
  reportInitialMode: 'EITHER'|'OVERDUE' = 'EITHER';
  loans: LoanTicketSummary[] = [];
  selectedLoan: LoanTicketDetail | null = null;
  searchTerm = '';
  statusFilter = 'ALL';
  fromDate = this.todayValue();
  toDate = this.todayValue();
  currentPage = 1;
  readonly pageSize = 10;
  isLoading = false;
  detailLoading = false;
  actionInProgress = false;
  errorMessage = '';
  ticketError = '';
  successMessage = '';
  rejectionReason = '';
  requestedTicketId?: number;

  constructor(
    private circulationService: CirculationService,
    private cdr: ChangeDetectorRef,
    @Optional() private route?: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    if(this.route?.snapshot.queryParamMap.get('view')==='report') {
      this.reportInitialQuery=(this.route.snapshot.queryParamMap.get('q')||'').slice(0,100);
      this.reportInitialMode=this.route.snapshot.queryParamMap.get('reportMode')==='OVERDUE'?'OVERDUE':'EITHER';
      this.switchView('report');
    }
    const referenceType = this.route?.snapshot.queryParamMap.get('referenceType');
    const referenceId = Number(this.route?.snapshot.queryParamMap.get('referenceId'));
    if (referenceType === 'LOAN_TICKET' && Number.isSafeInteger(referenceId) && referenceId > 0) {
      this.requestedTicketId = referenceId;
    }
    this.loadLoans();
  }

  switchView(view: 'loans' | 'report'): void {
    if (this.actionInProgress) return;
    this.activeView = view;
    if (view === 'report') this.reportVisited = true;
  }

  get filteredLoans(): LoanTicketSummary[] {
    const query = this.searchTerm.trim().toLocaleLowerCase('vi');
    return this.loans.filter(loan => {
      const matchesStatus = this.statusFilter === 'ALL' || loan.status === this.statusFilter;
      const created = loan.createdAt ? new Date(loan.createdAt) : null;
      const from = this.fromDate ? new Date(`${this.fromDate}T00:00:00`) : null;
      const to = this.toDate ? new Date(`${this.toDate}T23:59:59.999`) : null;
      const matchesDate = !created || ((!from || created >= from) && (!to || created <= to));
      const searchable = [loan.ticketId, loan.mssv, loan.readerName, loan.department]
        .filter(value => value !== null && value !== undefined)
        .join(' ')
        .toLocaleLowerCase('vi');
      return matchesStatus && matchesDate && (!query || searchable.includes(query));
    });
  }

  get pagedLoans(): LoanTicketSummary[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredLoans.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredLoans.length / this.pageSize));
  }

  applyFilters(): void {
    this.currentPage = 1;
  }

  changePage(page: number): void {
    this.currentPage = Math.min(this.totalPages, Math.max(1, page));
  }

  get activeCount(): number {
    return this.loans.filter(loan => loan.status === 'BORROWING' || loan.status === 'OVERDUE').length;
  }

  get overdueCount(): number {
    return this.loans.filter(loan => loan.status === 'OVERDUE' || loan.overdueItemCount > 0).length;
  }

  get recordedLateFine(): number {
    return this.loans.reduce((sum, loan) => sum + Number(loan.totalLateFine || 0), 0);
  }

  loadLoans(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.circulationService.getStaffLoans().subscribe({
      next: loans => {
        this.loans = loans || [];
        this.isLoading = false;
        if (this.requestedTicketId) {
          const ticketId = this.requestedTicketId;
          this.requestedTicketId = undefined;
          this.viewDetails(ticketId);
        }
        this.cdr.detectChanges();
      },
      error: error => {
        this.isLoading = false;
        this.errorMessage = this.extractError(error, 'Không thể tải danh sách phiếu mượn.');
        this.cdr.detectChanges();
      },
    });
  }

  viewDetails(ticketId: number): void {
    this.detailLoading = true;
    this.ticketError = '';
    this.errorMessage = '';
    this.rejectionReason = '';
    this.circulationService.getLoan(ticketId).subscribe({
      next: loan => {
        this.selectedLoan = loan;
        this.detailLoading = false;
        this.cdr.detectChanges();
      },
      error: error => {
        this.detailLoading = false;
        this.ticketError = this.extractError(error, `Không thể tải phiếu #${ticketId}.`);
        this.cdr.detectChanges();
      },
    });
  }

  closeDetails(): void {
    this.selectedLoan = null;
    this.rejectionReason = '';
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
      PENDING: 'Phiếu cũ cần kiểm tra',
      RESERVED: 'Đang giữ chỗ',
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

  private todayValue(): string {
    const now = new Date();
    const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
    return local.toISOString().slice(0, 10);
  }

  private extractError(error: any, fallback: string): string {
    const message = error?.error?.message ?? error?.error;
    return typeof message === 'string' && message.trim() ? message : fallback;
  }
}
