import { CommonModule } from '@angular/common';
import {
  ChangeDetectorRef,
  Component,
  DestroyRef,
  EventEmitter,
  Input,
  OnInit,
  Output,
  inject,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, take, timeout } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { DialogService } from '../../../services/dialog.service';
import { OnlinePaymentSession, PenaltyService } from '../../../services/penalty.service';

interface Draft {
  amount: number;
  purpose: 'DEBT' | 'DEPOSIT';
  idempotencyKey: string;
  reference?: string;
}

@Component({
  selector: 'app-mock-payment',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './mock-payment.html',
  styleUrl: './mock-payment.css',
})
export class MockPayment implements OnInit {
  @Input() debt = 0;
  @Output() completed = new EventEmitter<void>();
  enabled = false;
  busy = false;
  error = '';
  amount = 0;
  purpose: 'DEBT' | 'DEPOSIT' = 'DEBT';
  draft: Draft | null = null;
  session: OnlinePaymentSession | null = null;
  private storageKey = '';
  private readonly destroyRef = inject(DestroyRef);
  constructor(
    private api: PenaltyService,
    private auth: AuthService,
    private dialog: DialogService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.amount = this.debt;
    this.api
      .getPaymentCapabilities()
      .pipe(timeout(15000), take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (caps) => {
          this.enabled = caps?.mockEnabled === true;
          if (this.enabled) {
            const actor = this.auth.getUserProfile()?.userId;
            if (!actor) this.enabled = false;
            else {
              this.storageKey = 'huit_lib_mock_payment:' + actor;
              try {
                const stored = JSON.parse(sessionStorage.getItem(this.storageKey) || 'null');
                if (stored && this.validDraft(stored)) {
                  this.draft = stored;
                  this.amount = stored.amount;
                  this.purpose = stored.purpose;
                } else sessionStorage.removeItem(this.storageKey);
              } catch {
                /* No automatic request when storage is missing or unavailable. */
              }
            }
          }
          this.cdr.markForCheck();
        },
        error: () => {
          this.enabled = false;
          this.cdr.markForCheck();
        },
      });
  }

  selectPurpose(): void {
    if (!this.draft && !this.busy) this.amount = this.purpose === 'DEBT' ? this.debt : 50000;
  }

  create(): void {
    if (!this.enabled || this.busy || this.draft) return;
    if (
      !Number.isInteger(this.amount) ||
      this.amount < 1000 ||
      this.amount > 100000000 ||
      (this.purpose === 'DEBT' && this.amount > this.debt)
    ) {
      this.error =
        'Nhập số tiền nguyên từ 1.000 đến 100.000.000 VNĐ; trả nợ không được vượt số còn nợ.';
      return;
    }
    this.draft = {
      amount: this.amount,
      purpose: this.purpose,
      idempotencyKey: crypto.randomUUID(),
    };
    this.session = null;
    this.save();
    this.recover();
  }

  recover(): void {
    if (!this.enabled || !this.draft || this.busy) return;
    const draft = this.draft;
    this.send(
      draft.reference
        ? this.api.getMockPayment(draft.reference)
        : this.api.createMockPayment({
            amount: draft.amount,
            purpose: draft.purpose,
            idempotencyKey: draft.idempotencyKey,
          }),
      !draft.reference,
    );
  }

  finish(outcome: 'SUCCESS' | 'CANCEL' | 'FAIL'): void {
    if (!this.enabled || this.busy || !this.draft?.reference || this.session?.status !== 'PENDING')
      return;
    this.send(this.api.finishMockPayment(this.draft.reference, outcome), false);
  }

  reset(): void {
    if (this.busy || this.draft) return;
    this.session = null;
    this.error = '';
    this.amount = this.purpose === 'DEBT' ? this.debt : 50000;
  }

  statusLabel(): string {
    return (
      (
        {
          PENDING: 'Chờ chọn kết quả giả lập',
          COMPLETED: 'Giả lập thành công',
          CANCELLED: 'Đã hủy giả lập',
          FAILED: 'Giả lập thất bại',
          EXPIRED: 'Phiên giả lập đã hết hạn',
        } as Record<string, string>
      )[this.session?.status || ''] || ''
    );
  }

  private send(request: Observable<OnlinePaymentSession>, creating: boolean): void {
    this.busy = true;
    this.error = '';
    request.pipe(timeout(30000), take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (session) => {
        this.busy = false;
        if (!this.validSession(session)) {
          this.error = 'Chưa xác nhận được kết quả. Giữ nguyên phiên và bấm kiểm tra lại.';
          this.cdr.markForCheck();
          return;
        }
        this.session = session;
        if (this.draft) this.draft.reference = session.reference;
        if (session.status === 'PENDING') this.save();
        else {
          this.draft = null;
          this.save();
        }
        this.cdr.markForCheck();
        if (session.status === 'COMPLETED') {
          this.dialog.show({
            type: 'success',
            title: 'Giả lập thanh toán thành công',
            message: `Đã ghi nhận ${session.amount.toLocaleString('vi-VN')} VNĐ ${session.purpose === 'DEPOSIT' ? 'vào tiền thế chân' : 'để trả công nợ'}. Đây là dữ liệu kiểm thử, không có tiền thật.`,
          });
          this.completed.emit();
        }
      },
      error: (error) => {
        this.busy = false;
        if (creating && [400, 401, 403, 409].includes(error.status)) {
          this.draft = null;
          this.save();
        }
        const reason = error?.error?.detail ?? error?.error?.message;
        this.error =
          typeof reason === 'string'
            ? reason
            : 'Chưa hoàn tất giao dịch. Bấm kiểm tra lại trước khi tạo giao dịch khác.';
        this.cdr.markForCheck();
      },
    });
  }

  private validDraft(value: Draft): boolean {
    return (
      ['DEBT', 'DEPOSIT'].includes(value.purpose) &&
      Number.isInteger(value.amount) &&
      value.amount >= 1000 &&
      value.amount <= 100000000 &&
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
        value.idempotencyKey,
      ) &&
      (!value.reference || /^MOCK-[0-9a-f]{64}$/.test(value.reference))
    );
  }
  private validSession(value: OnlinePaymentSession): boolean {
    return (
      !!value &&
      !!this.draft &&
      value.provider === 'MOCK' &&
      /^MOCK-[0-9a-f]{64}$/.test(value.reference) &&
      (!this.draft.reference || this.draft.reference === value.reference) &&
      value.purpose === this.draft.purpose &&
      value.amount === this.draft.amount &&
      ['PENDING', 'COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED'].includes(value.status) &&
      (value.status !== 'COMPLETED' ||
        (Number.isSafeInteger(value.paymentTransactionId) && value.paymentTransactionId! > 0))
    );
  }
  private save(): void {
    try {
      if (this.draft) sessionStorage.setItem(this.storageKey, JSON.stringify(this.draft));
      else sessionStorage.removeItem(this.storageKey);
    } catch {
      /* The in-memory request key still protects retries during this page visit. */
    }
  }
}
