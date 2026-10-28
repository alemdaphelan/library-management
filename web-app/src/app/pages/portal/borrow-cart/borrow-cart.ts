import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { BorrowCartItem, BorrowCartService } from '../../../services/borrow-cart.service';
import {
  BatchBorrowResponse,
  BorrowEligibility,
  CirculationService,
} from '../../../services/circulation.service';
import { LibraryPolicy, LibraryPolicyService } from '../../../services/library-policy.service';

@Component({
  selector: 'app-borrow-cart',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './borrow-cart.html',
  styleUrls: ['./borrow-cart.css'],
})
export class BorrowCart implements OnInit {
  items: BorrowCartItem[] = [];
  ticketType: 'BORROW' | 'ONSITE' = 'BORROW';
  pickupDate = '';
  minPickupDate = '';
  maxPickupDate = '';
  note = '';
  isSubmitting = false;
  errorMessage = '';
  result: BatchBorrowResponse | null = null;
  eligibility: BorrowEligibility | null = null;
  isCheckingEligibility = false;
  policy: LibraryPolicy | null = null;

  constructor(
    private borrowCartService: BorrowCartService,
    private circulationService: CirculationService,
    private policyService: LibraryPolicyService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.refreshItems();
    this.loadEligibility();
    this.policyService.getPublicPolicy().subscribe({
      next: policy => {
        this.policy = policy;
        this.borrowCartService.setMaximumItems(policy.maximumOpenItems);
        this.cdr.detectChanges();
      },
      error: () => { /* Backend vẫn xác thực hạn mức khi gửi phiếu. */ },
    });
    const today = new Date();
    this.minPickupDate = this.formatLocalDate(today);
    this.pickupDate = this.formatLocalDate(today);
    this.maxPickupDate = this.pickupDate;
  }

  remove(bookId: number): void {
    this.borrowCartService.remove(bookId);
    this.refreshItems();
    this.errorMessage = '';
  }

  submit(): void {
    if (this.isSubmitting || this.items.length === 0) return;
    if (!this.pickupDate) {
      this.errorMessage = 'Vui lòng chọn ngày đến nhận sách.';
      return;
    }
    if (this.eligibility && this.items.length > this.eligibility.remainingSlots) {
      this.errorMessage = this.eligibility.message;
      return;
    }
    this.isSubmitting = true;
    this.errorMessage = '';
    this.result = null;

    this.circulationService.reserveBooks({
      bookIds: this.items.map(item => item.bookId),
      ticketType: this.ticketType,
      pickupDate: this.pickupDate,
      note: this.note.trim() || undefined,
    }).pipe(
      finalize(() => {
        this.isSubmitting = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: result => {
        this.result = result;
        this.borrowCartService.clear();
        this.refreshItems();
        this.loadEligibility();
      },
      error: error => {
        this.errorMessage = this.extractError(error);
      },
    });
  }

  private refreshItems(): void {
    this.items = this.borrowCartService.allItems;
  }

  private loadEligibility(): void {
    this.isCheckingEligibility = true;
    this.circulationService.getMyBorrowEligibility().subscribe({
      next: eligibility => {
        this.eligibility = eligibility;
        this.borrowCartService.setMaximumItems(eligibility.maximumItems);
        this.isCheckingEligibility = false;
        if (this.items.length > eligibility.remainingSlots) {
          this.errorMessage = eligibility.message;
        }
        this.cdr.detectChanges();
      },
      error: error => {
        this.isCheckingEligibility = false;
        this.errorMessage = this.extractError(error);
        this.cdr.detectChanges();
      },
    });
  }

  private formatLocalDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private extractError(error: any): string {
    if (error?.status === 401 || error?.status === 403) {
      return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
    }
    const body = error?.error;
    if (typeof body === 'string' && body.trim()) return body;
    const message = body?.detail ?? body?.message;
    if (typeof message === 'string' && message.trim()) return message;
    if (error?.status === 409) {
      return 'Không thể tạo phiếu mượn vì tài liệu hoặc tài khoản hiện không còn đáp ứng điều kiện mượn. Vui lòng tải lại trang và kiểm tra các phiếu đang hoạt động.';
    }
    const generic = body?.error;
    return typeof generic === 'string' && generic.trim() && generic.toLowerCase() !== 'conflict'
      ? generic
      : 'Không thể tạo phiếu mượn. Vui lòng thử lại.';
  }
}
