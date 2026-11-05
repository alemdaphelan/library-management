import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { DebtEntry, PenaltyCase, PenaltyService } from '../../../services/penalty.service';
import { AuthService, LibraryCard } from '../../../services/auth.service';
import { DialogService } from '../../../services/dialog.service';
import { isDebtCharge, remainingDebt } from '../../../services/debt-amount';
import { FinancePager } from './finance-pager';
import { MockPayment } from './mock-payment';

const closedCases = new Set(['PAID', 'WAIVED', 'SETTLED', 'CLOSED', 'CANCELLED', 'COMPLETED']);

@Component({
  selector: 'app-my-penalties',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, FinancePager, MockPayment],
  templateUrl: './my-penalties.html',
  styleUrls: ['./my-penalties.css'],
})
export class MyPenalties implements OnInit {
  ledger: DebtEntry[] = [];
  penaltyCases: PenaltyCase[] = [];
  loading = true;
  errorMessage = '';
  libraryCard: LibraryCard | null = null;
  paymentPurpose: 'DEBT' | 'DEPOSIT' = 'DEBT';
  paymentAmount = 0;
  paymentSubmitting = false;
  readonly pageSize = 10;
  debtPage = 0;
  casePage = 0;
  paymentPage = 0;
  debtStatus: 'UNPAID' | 'PAID' | 'ALL' = 'UNPAID';
  debtType = '';
  debtQuery = '';
  caseStatus: 'OPEN' | 'CLOSED' | 'ALL' = 'OPEN';
  readonly feeTypes = [
    { value: 'FINE', label: 'Phạt quá hạn' },
    { value: 'REPAIR_FEE', label: 'Phí sửa chữa' },
    { value: 'COMPENSATION', label: 'Tiền đền bù' },
    { value: 'TECHNICAL_FEE', label: 'Phí xử lý kỹ thuật' },
  ];

  constructor(
    private penaltyService: PenaltyService,
    private authService: AuthService,
    private dialog: DialogService,
    private route: ActivatedRoute,
    private router: Router,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.handlePaymentResult();
    this.loadFinancials();
  }

  loadFinancials(): void {
    this.loading = true;
    this.errorMessage = '';
    forkJoin({
      ledger: this.penaltyService.getMyLedger(),
      cases: this.penaltyService.getMyCases(),
      card: this.authService.fetchLibraryCard(),
    }).subscribe({
      next: ({ ledger, cases, card }) => {
        this.ledger = ledger || [];
        this.penaltyCases = cases || [];
        this.libraryCard = card;
        this.debtPage = this.casePage = this.paymentPage = 0;
        if (this.paymentPurpose === 'DEBT') this.paymentAmount = this.outstandingDebt;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Không thể tải dữ liệu công nợ. Vui lòng thử lại hoặc liên hệ thủ thư.';
        this.cdr.detectChanges();
      },
    });
  }

  get debtEntries(): DebtEntry[] {
    return this.ledger
      .filter(isDebtCharge)
      .sort((a, b) => this.dateValue(b.createdAt) - this.dateValue(a.createdAt));
  }

  get paymentEntries(): DebtEntry[] {
    return this.ledger
      .filter(entry => entry.type === 'PAYMENT' || Number(entry.amount) < 0)
      .sort((a, b) => this.dateValue(b.paidAt || b.createdAt) - this.dateValue(a.paidAt || a.createdAt));
  }

  get filteredDebtEntries(): DebtEntry[] {
    const query = this.searchKey(this.debtQuery.trim());
    return this.debtEntries.filter(entry => {
      const open = this.remainingAmount(entry) > 0;
      const type = entry.type === 'REPAIR' ? 'REPAIR_FEE' : entry.type === 'OVERDUE_FINE' ? 'FINE' : entry.type;
      return (this.debtStatus === 'ALL' || (this.debtStatus === 'UNPAID' ? open : !open))
        && (!this.debtType || type === this.debtType)
        && (!query || this.searchKey(`${entry.transactionId} ${this.feeTypeLabel(entry.type)} ${entry.reason || ''} ${entry.receiptNumber || ''}`).includes(query));
    });
  }
  get visibleDebtEntries(): DebtEntry[] {
    return this.filteredDebtEntries.slice(this.debtPage * this.pageSize, (this.debtPage + 1) * this.pageSize);
  }
  get filteredCases(): PenaltyCase[] {
    return this.penaltyCases.filter(item => this.caseStatus === 'ALL'
      || (this.caseStatus === 'CLOSED' ? closedCases.has(item.status) : !closedCases.has(item.status)))
      .sort((a, b) => this.dateValue(b.reportedAt) - this.dateValue(a.reportedAt) || b.caseId - a.caseId);
  }
  get visibleCases(): PenaltyCase[] {
    return this.filteredCases.slice(this.casePage * this.pageSize, (this.casePage + 1) * this.pageSize);
  }
  get visiblePayments(): DebtEntry[] {
    return this.paymentEntries.slice(this.paymentPage * this.pageSize, (this.paymentPage + 1) * this.pageSize);
  }
  get cardStatusLabel(): string {
    if (!this.libraryCard?.issued) return 'Chưa cấp thẻ';
    return ({ ACTIVE: 'Đang hoạt động', LOCKED: 'Đã khóa', EXPIRED: 'Đã hết hạn' } as Record<string, string>)[this.libraryCard.cardStatus] || 'Chưa xác định';
  }
  caseRemaining(item: PenaltyCase): number {
    return closedCases.has(item.status) ? 0 : Math.max(0, item.totalAmount - (item.paidAmount || 0));
  }
  resetDebtFilters(): void {
    this.debtStatus = 'UNPAID'; this.debtType = ''; this.debtQuery = ''; this.debtPage = 0;
  }
  private searchKey(value: string): string {
    return value.normalize('NFD').replace(/\p{M}/gu, '').replace(/[đĐ]/g, 'd').toLocaleLowerCase('vi');
  }

  selectPaymentPurpose(purpose: 'DEBT' | 'DEPOSIT'): void {
    this.paymentPurpose = purpose;
    this.paymentAmount = purpose === 'DEBT' ? this.outstandingDebt : 50000;
  }

  startOnlinePayment(): void {
    const amount = Math.trunc(Number(this.paymentAmount));
    if (!Number.isFinite(amount) || amount < 1000) {
      this.dialog.show({ type: 'warning', title: 'Số tiền chưa hợp lệ', message: 'Số tiền thanh toán tối thiểu là 1.000 VNĐ.' });
      return;
    }
    if (this.paymentPurpose === 'DEBT' && amount > this.outstandingDebt) {
      this.dialog.show({ type: 'warning', title: 'Số tiền vượt công nợ', message: 'Số tiền thanh toán không được vượt quá công nợ hiện tại.' });
      return;
    }
    this.paymentSubmitting = true;
    this.penaltyService.createOnlinePayment(amount, this.paymentPurpose).subscribe({
      next: session => {
        if (!session.paymentUrl) {
          this.paymentSubmitting = false;
          this.dialog.show({ type: 'error', title: 'Không thể mở cổng thanh toán', message: 'Hệ thống không nhận được đường dẫn thanh toán từ VNPAY.' });
          return;
        }
        window.location.assign(session.paymentUrl);
      },
      error: error => {
        this.paymentSubmitting = false;
        this.dialog.show({
          type: 'error',
          title: 'Chưa thể thanh toán trực tuyến',
          message: error?.error?.message || error?.error?.detail || error?.message || 'Vui lòng thử lại hoặc liên hệ quản trị viên.',
        });
        this.cdr.detectChanges();
      },
    });
  }

  get openDebtEntries(): DebtEntry[] {
    return this.debtEntries.filter(entry => this.remainingAmount(entry) > 0);
  }

  get totalAssessed(): number {
    return this.debtEntries.reduce((sum, entry) => sum + Number(entry.amount || 0), 0);
  }

  get totalPaid(): number {
    return this.debtEntries.reduce(
      (sum, entry) => sum + Math.min(Number(entry.amount || 0), Number(entry.paidAmount || 0)),
      0,
    );
  }

  get outstandingDebt(): number {
    return this.debtEntries.reduce((sum, entry) => sum + this.remainingAmount(entry), 0);
  }

  remainingAmount(entry: DebtEntry): number {
    return remainingDebt(entry);
  }

  feeTypeLabel(type: string): string {
    return ({
      FINE: 'Phạt quá hạn',
      OVERDUE_FINE: 'Phạt quá hạn',
      COMPENSATION: 'Tiền đền bù',
      REPAIR: 'Phí sửa chữa',
      REPAIR_FEE: 'Phí sửa chữa',
      WAIVER_ADJUSTMENT: 'Miễn giảm',
      TECHNICAL_FEE: 'Phí xử lý kỹ thuật',
      PAYMENT: 'Thanh toán',
      DEPOSIT_TOPUP: 'Nạp tiền thế chân',
      ADJUSTMENT: 'Điều chỉnh',
      WAIVER: 'Miễn giảm',
    } as Record<string, string>)[type] || type;
  }

  statusLabel(status: string): string {
    return ({
      OUTSTANDING: 'Chưa thanh toán',
      PARTIALLY_PAID: 'Đã thanh toán một phần',
      PAID: 'Đã thanh toán',
      COMPLETED: 'Hoàn tất',
      PENDING_REVIEW: 'Đang chờ miễn giảm',
      OPEN: 'Đang xử lý',
      SETTLED: 'Đã quyết toán',
      CLOSED: 'Đã đóng',
      WAIVED: 'Đã miễn giảm',
      WAITING_REPLACEMENT: 'Chờ sách thay thế',
      CANCELLED: 'Đã hủy',
    } as Record<string, string>)[status] || status;
  }

  incidentLabel(type: string): string {
    return type === 'LOST' ? 'Báo mất tài liệu' : type === 'DAMAGED' ? 'Báo hư hỏng' : type;
  }

  private dateValue(value?: string): number {
    return value ? new Date(value).getTime() : 0;
  }

  private handlePaymentResult(): void {
    const result = this.route.snapshot.queryParamMap.get('payment');
    const reference = this.route.snapshot.queryParamMap.get('reference');
    if (!result) return;
    this.dialog.show({
      type: result === 'success' ? 'success' : 'error',
      title: result === 'success' ? 'Thanh toán thành công' : 'Thanh toán chưa hoàn tất',
      message: result === 'success'
        ? `VNPAY đã xác nhận giao dịch${reference ? ` ${reference}` : ''}. Công nợ và trạng thái thẻ đang được đối soát lại.`
        : 'Giao dịch bị hủy hoặc không thành công. Hệ thống không ghi nhận khoản thu.',
    });
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { payment: null, reference: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}
