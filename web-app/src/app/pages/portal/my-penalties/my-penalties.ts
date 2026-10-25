import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { DebtEntry, PenaltyCase, PenaltyService } from '../../../services/penalty.service';

@Component({
  selector: 'app-my-penalties',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './my-penalties.html',
  styleUrls: ['./my-penalties.css'],
})
export class MyPenalties implements OnInit {
  ledger: DebtEntry[] = [];
  penaltyCases: PenaltyCase[] = [];
  loading = true;
  errorMessage = '';

  constructor(
    private penaltyService: PenaltyService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadFinancials();
  }

  loadFinancials(): void {
    this.loading = true;
    this.errorMessage = '';
    forkJoin({
      ledger: this.penaltyService.getMyLedger(),
      cases: this.penaltyService.getMyCases(),
    }).subscribe({
      next: ({ ledger, cases }) => {
        this.ledger = ledger || [];
        this.penaltyCases = cases || [];
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
      .filter(entry => Number(entry.amount) > 0)
      .sort((a, b) => this.dateValue(b.createdAt) - this.dateValue(a.createdAt));
  }

  get paymentEntries(): DebtEntry[] {
    return this.ledger
      .filter(entry => entry.type === 'PAYMENT' || Number(entry.amount) < 0)
      .sort((a, b) => this.dateValue(b.paidAt || b.createdAt) - this.dateValue(a.paidAt || a.createdAt));
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
}
