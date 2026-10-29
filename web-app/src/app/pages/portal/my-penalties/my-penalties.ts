import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { DebtEntry, PenaltyCase, PenaltyService } from '../../../services/penalty.service';
import { AuthService, LibraryCard } from '../../../services/auth.service';
import { DialogService } from '../../../services/dialog.service';

@Component({
  selector: 'app-my-penalties',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
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
      .filter(entry => Number(entry.amount) > 0 && entry.type !== 'DEPOSIT_TOPUP')
      .sort((a, b) => this.dateValue(b.createdAt) - this.dateValue(a.createdAt));
  }

  get paymentEntries(): DebtEntry[] {
    return this.ledger
      .filter(entry => entry.type === 'PAYMENT' || Number(entry.amount) < 0)
      .sort((a, b) => this.dateValue(b.paidAt || b.createdAt) - this.dateValue(a.paidAt || a.createdAt));
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
    return Math.max(0, Number(entry.amount || 0) - Number(entry.paidAmount || 0));
  }

  feeTypeLabel(type: string): string {
    return ({
      FINE: 'Phạt quá hạn',
      OVERDUE_FINE: 'Phạt quá hạn',
      COMPENSATION: 'Tiền đền bù',
      REPAIR: 'Phí sửa chữa',
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
