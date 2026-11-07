import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { DialogService } from '../../../services/dialog.service';
import { AuthService } from '../../../services/auth.service';
import { CashDepositReceipt, CashDepositRequest, PenaltyService } from '../../../services/penalty.service';
import { ReaderProfile, UserService } from '../../../services/user.service';

interface PendingDeposit {
  request: CashDepositRequest;
  mssv: string;
  readerName: string;
}

@Component({
  selector: 'app-counter-deposit',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './counter-deposit.html',
  styleUrl: './penalties.css',
  styles: [':host { min-width: 0; } .receipt { overflow-wrap: anywhere; }'],
})
export class CounterDeposit implements OnInit {
  private readonly users = inject(UserService);
  private readonly penalties = inject(PenaltyService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(DialogService);
  private readonly destroyRef = inject(DestroyRef);
  private lookupVersion = 0;
  private storageKey?: string;

  readonly maxAmount = 999_999_999_999_999;
  mssv = '';
  amount: number | null = null;
  note = '';
  reader?: ReaderProfile;
  pending?: PendingDeposit;
  receipt?: CashDepositReceipt & { mssv: string; readerName: string };
  lookingUp = false;
  submitting = false;

  ngOnInit(): void {
    const operator = this.auth.getUserProfile();
    const owner = operator?.userId || operator?.email;
    if (!owner) return;
    this.storageKey = `huit_lib_pending_cash_deposit:${encodeURIComponent(owner)}`;
    try {
      const saved = sessionStorage.getItem(this.storageKey);
      if (!saved) return;
      const pending: PendingDeposit = JSON.parse(saved);
      if (!pending?.request?.idempotencyKey || !pending.request.userId || !pending.mssv) {
        throw new Error('Invalid saved deposit');
      }
      this.pending = pending;
    } catch {
      this.showError('Không đọc được lượt thu đã lưu trong trình duyệt.');
    }
  }

  resetReader(): void {
    this.lookupVersion++;
    this.reader = undefined;
    this.lookingUp = false;
  }

  lookup(): void {
    if (this.pending || this.submitting) return;
    this.resetReader();
    const mssv = this.mssv.trim();
    if (!mssv) return;
    const version = this.lookupVersion;
    this.lookingUp = true;
    this.users.getReaderProfile(mssv).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: reader => {
        if (version !== this.lookupVersion) return;
        this.lookingUp = false;
        this.reader = reader;
      },
      error: () => {
        if (version !== this.lookupVersion) return;
        this.lookingUp = false;
        this.showError('Không tìm thấy MSSV hoặc không tải được hồ sơ sinh viên.');
      },
    });
  }

  submit(): void {
    if (this.submitting) return;
    const retry = !!this.pending;
    if (!this.pending) {
      if (!this.reader || this.lookingUp || this.amount === null || !Number.isSafeInteger(this.amount)
          || this.amount < 1 || this.amount > this.maxAmount || this.note.trim().length > 200) {
        this.showError('Tra cứu sinh viên và nhập số tiền nguyên hợp lệ.');
        return;
      }
      try {
        if (!this.storageKey) throw new Error('Missing operator identity');
        const student = this.reader.student;
        const pending: PendingDeposit = {
          request: { userId: student.userId, amount: this.amount, note: this.note.trim(), idempotencyKey: crypto.randomUUID() },
          mssv: student.mssv,
          readerName: student.fullName,
        };
        sessionStorage.setItem(this.storageKey, JSON.stringify(pending));
        this.pending = pending;
      } catch {
        this.showError('Không lưu được lượt thu để thử lại an toàn. Kiểm tra phiên đăng nhập và bộ nhớ trình duyệt.');
        return;
      }
    }

    const pending = this.pending;
    this.submitting = true;
    this.receipt = undefined;
    this.penalties.recordCashDeposit(pending.request).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: receipt => {
        this.clearPending();
        this.submitting = false;
        this.receipt = { ...receipt, mssv: pending.mssv, readerName: pending.readerName };
        this.mssv = '';
        this.amount = null;
        this.note = '';
        this.reader = undefined;
      },
      error: error => {
        this.submitting = false;
        const detail = error?.error?.detail;
        if (!retry && [400, 401, 403, 404, 409, 422].includes(error?.status)) {
          this.clearPending();
          this.showError(typeof detail === 'string' && /quầy thư viện đã đóng cửa|chưa có ví thư viện/i.test(detail)
            ? detail : 'Máy chủ từ chối ghi nhận tiền thế chân. Kiểm tra tài khoản và số tiền.');
        } else {
          this.showError('Chưa nhận được kết quả nạp cọc. Hãy thử lại lượt thu đang chờ.');
        }
      },
    });
  }

  private showError(message: string): void {
    this.dialog.show({ title: 'Không thể ghi nhận tiền thế chân', message, type: 'error', confirmText: 'Đã hiểu' });
  }

  private clearPending(): void {
    this.pending = undefined;
    try {
      if (this.storageKey) sessionStorage.removeItem(this.storageKey);
    } catch {
      // A retained request still replays the same receipt on reload.
    }
  }
}
