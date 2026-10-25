import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { BorrowCartItem, BorrowCartService } from '../../../services/borrow-cart.service';
import {
  BatchBorrowResponse,
  CirculationService,
} from '../../../services/circulation.service';

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

  constructor(
    private borrowCartService: BorrowCartService,
    private circulationService: CirculationService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.refreshItems();
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
      },
      error: error => {
        this.errorMessage = this.extractError(error);
      },
    });
  }

  private refreshItems(): void {
    this.items = this.borrowCartService.allItems;
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
    const message = body?.detail ?? body?.message ?? body?.error;
    return typeof message === 'string' && message.trim()
      ? message
      : 'Không thể tạo phiếu mượn. Vui lòng thử lại.';
  }
}
