import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TimeoutError, defaultIfEmpty, finalize, take, timeout } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { BorrowCartItem, BorrowCartService } from '../../../services/borrow-cart.service';
import { BatchBorrowResponse, BorrowEligibility, CirculationService } from '../../../services/circulation.service';
import { LibraryPolicy, LibraryPolicyService } from '../../../services/library-policy.service';

@Component({
  selector: 'app-borrow-cart', standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './borrow-cart.html', styleUrls: ['./borrow-cart.css'],
})
export class BorrowCart implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);
  private activePendingKey: string | null = null;
  items: BorrowCartItem[] = [];
  ticketType: 'BORROW' | 'ONSITE' = 'BORROW';
  pickupDate = ''; minPickupDate = ''; maxPickupDate = ''; note = '';
  isSubmitting = false;
  requiresReview = false;
  errorMessage = '';
  result: BatchBorrowResponse | null = null;
  eligibility: BorrowEligibility | null = null;
  isCheckingEligibility = false;
  eligibilityError = '';
  policy: LibraryPolicy | null = null;
  private readonly reviewMessage = 'Chưa xác nhận được kết quả tạo phiếu. Hãy kiểm tra lịch sử mượn trước khi gửi lại để tránh đặt trùng.';

  constructor(
    private borrowCartService: BorrowCartService,
    private circulationService: CirculationService,
    private policyService: LibraryPolicyService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.refreshItems();
    this.activePendingKey = this.pendingKey();
    try {
      if (this.activePendingKey && sessionStorage.getItem(this.activePendingKey)) this.requireReview();
    } catch { /* The in-memory guard still works when session storage is unavailable. */ }
    this.loadEligibility();
    this.policyService.getPublicPolicy().pipe(timeout(15000), take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: policy => {
        this.policy = policy;
        if (policy) this.borrowCartService.setMaximumItems(policy.maximumOpenItems);
        this.cdr.markForCheck();
      },
      error: () => { /* The reservation endpoint always enforces the current policy. */ },
    });
    this.minPickupDate = this.formatLocalDate(new Date());
    this.pickupDate = this.minPickupDate;
    this.maxPickupDate = this.minPickupDate;
  }

  remove(bookId: number): void {
    if (this.isSubmitting || this.requiresReview) return;
    this.borrowCartService.remove(bookId); this.refreshItems(); this.errorMessage = '';
  }

  submit(): void {
    if (this.isSubmitting || this.requiresReview || this.result || this.isCheckingEligibility || !this.items.length) return;
    if (!this.pickupDate) { this.errorMessage = 'Vui lòng chọn ngày đến nhận sách.'; return; }
    if (this.eligibility && (this.eligibility.cardEligible === false || this.items.length > this.eligibility.remainingSlots)) {
      this.errorMessage = this.eligibility.message; return;
    }
    const bookIds = this.items.map(item => item.bookId);
    this.isSubmitting = true; this.errorMessage = ''; this.result = null;
    this.activePendingKey = this.pendingKey();
    try {
      if (this.activePendingKey) sessionStorage.setItem(this.activePendingKey, JSON.stringify({ bookIds, at: new Date().toISOString() }));
    } catch { /* Never store auth tokens or automatically replay the POST. */ }
    this.circulationService.reserveBooks({bookIds, ticketType: this.ticketType, pickupDate: this.pickupDate, note: this.note.trim() || undefined}).pipe(
      timeout(30000), take(1), defaultIfEmpty(null), takeUntilDestroyed(this.destroyRef),
      finalize(() => {
        this.isSubmitting = false;
        if (!this.destroyRef.destroyed) this.cdr.markForCheck();
      }),
    ).subscribe({
      next: result => {
        // HTTP 200 alone does not prove that this cart was reserved successfully.
        if (!this.validResult(result, bookIds)) { this.requireReview(); return; }
        this.isSubmitting = false;
        this.result = result;
        this.clearPending();
        this.borrowCartService.clear(); this.refreshItems();
        // The confirmation does not depend on another eligibility request finishing.
        this.cdr.markForCheck();
      },
      error: error => {
        if (error instanceof TimeoutError || !error?.status || error.status >= 500 || error.status === 200 || error.status === 408) {
          this.requireReview();
        } else {
          this.clearPending(); this.errorMessage = this.extractError(error);
        }
        this.cdr.markForCheck();
      },
    });
  }

  /** An explicit user action after checking history; server duplicate checks remain authoritative. */
  unlockAfterReview(): void {
    if (this.isSubmitting) return;
    this.clearPending(); this.requiresReview = false; this.errorMessage = ''; this.loadEligibility();
  }

  loadEligibility(): void {
    if (this.isCheckingEligibility) return;
    this.isCheckingEligibility = true; this.eligibilityError = '';
    this.circulationService.getMyBorrowEligibility().pipe(
      timeout(15000), take(1), takeUntilDestroyed(this.destroyRef),
      finalize(() => { this.isCheckingEligibility = false; if (!this.destroyRef.destroyed) this.cdr.markForCheck(); }),
    ).subscribe({
      next: eligibility => {
        if (!eligibility || !Number.isInteger(eligibility.maximumItems) || eligibility.maximumItems < 1
          || !Number.isInteger(eligibility.remainingSlots) || eligibility.remainingSlots < 0) {
          this.eligibility = null; this.eligibilityError = 'Không tải được hạn mức mượn. Vui lòng thử lại.'; return;
        }
        this.eligibility = eligibility;
        this.borrowCartService.setMaximumItems(eligibility.maximumItems);
        this.cdr.markForCheck();
      },
      error: () => { this.eligibility = null; this.eligibilityError = 'Không tải được hạn mức mượn. Vui lòng thử lại.'; this.cdr.markForCheck(); },
    });
  }

  private validResult(result: BatchBorrowResponse | null, bookIds: number[]): result is BatchBorrowResponse {
    return !!result && result.success === true && result.status === 'RESERVED' && Number.isSafeInteger(result.ticketId) && result.ticketId > 0
      && Array.isArray(result.bookIds) && result.bookIds.length === bookIds.length && new Set(result.bookIds).size === bookIds.length
      && result.bookIds.every(id => bookIds.includes(id))
      && Array.isArray(result.bookTitles) && result.bookTitles.length === bookIds.length
      && Array.isArray(result.barcodes) && result.barcodes.length === bookIds.length
      && typeof result.pickupDeadline === 'string' && Number.isFinite(Date.parse(result.pickupDeadline));
  }
  private requireReview(): void { this.requiresReview = true; this.errorMessage = this.reviewMessage; }
  private pendingKey(): string | null {
    const profile = this.auth.getUserProfile(); const actor = profile?.userId || profile?.email;
    return actor ? 'huit_lib_pending_borrow:' + encodeURIComponent(actor) : null;
  }
  private clearPending(): void {
    try { if (this.activePendingKey) sessionStorage.removeItem(this.activePendingKey); } catch { /* Storage can be disabled. */ }
  }
  private refreshItems(): void { this.items = this.borrowCartService.allItems; }
  private formatLocalDate(date: Date): string {
    return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0');
  }
  private extractError(error: any): string {
    if (error?.status === 401 || error?.status === 403) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
    const body = error?.error;
    if (typeof body === 'string' && body.trim()) return body;
    const message = body?.detail ?? body?.message;
    if (typeof message === 'string' && message.trim()) return message;
    if (error?.status === 409) return 'Không thể tạo phiếu vì tài liệu hoặc tài khoản không còn đáp ứng điều kiện mượn. Kiểm tra lại các phiếu đang hoạt động.';
    return 'Không thể tạo phiếu mượn. Vui lòng kiểm tra thông tin và thử lại.';
  }
}
