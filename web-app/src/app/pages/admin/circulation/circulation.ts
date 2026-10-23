import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import {
  BookCopy,
  CirculationOperationResponse,
  CirculationService,
  LoanItem,
  LoanTicketDetail,
} from '../../../services/circulation.service';

interface CounterBook extends BookCopy {
  displayTitle: string;
  displayAuthor?: string;
  loanTicketId?: number;
}

@Component({
  selector: 'app-circulation',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './circulation.html',
  styleUrls: ['./circulation.css'],
})
export class Circulation {
  activeTab: 'borrow' | 'return' = 'borrow';
  ticketInput = '';
  barcodeInput = '';
  returnConditionNote = '';
  selectedTicket: LoanTicketDetail | null = null;
  scannedBooks: CounterBook[] = [];
  isResolvingTicket = false;
  isScanningBook = false;
  isSubmitting = false;
  errorMessage = '';
  successMessage = '';
  lastOperation: CirculationOperationResponse | null = null;

  constructor(private circulationService: CirculationService) {}

  get canSubmit(): boolean {
    if (this.isSubmitting || this.scannedBooks.length === 0) return false;
    if (this.activeTab === 'return') return true;
    if (!this.selectedTicket || this.selectedTicket.status !== 'RESERVED') return false;
    const expected = new Set(this.selectedTicket.items.map(item => item.barcode));
    return expected.size === this.scannedBooks.length
      && this.scannedBooks.every(book => expected.has(book.barcode));
  }

  switchTab(tab: 'borrow' | 'return'): void {
    this.activeTab = tab;
    this.resetForm();
  }

  resolveTicket(): void {
    const ticketId = this.parseTicketId(this.ticketInput);
    if (ticketId === null) {
      this.errorMessage = 'Mã phiếu không hợp lệ. Hãy nhập số phiếu hoặc mã HUIT-PM-{số phiếu}.';
      return;
    }

    this.isResolvingTicket = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.selectedTicket = null;
    this.scannedBooks = [];

    this.circulationService.getLoan(ticketId).subscribe({
      next: ticket => {
        this.isResolvingTicket = false;
        if (ticket.status !== 'RESERVED') {
          this.errorMessage = `Phiếu #${ticket.ticketId} đang ở trạng thái ${this.statusLabel(ticket.status)}; chỉ phiếu đã duyệt mới được giao sách.`;
          return;
        }
        this.selectedTicket = ticket;
        this.ticketInput = String(ticket.ticketId);
      },
      error: error => {
        this.isResolvingTicket = false;
        this.errorMessage = this.extractError(error, `Không thể tải phiếu #${ticketId}.`);
      },
    });
  }

  scanBookBarcode(): void {
    const barcode = this.barcodeInput.trim();
    if (!barcode) return;
    if (this.scannedBooks.some(book => book.barcode === barcode)) {
      this.errorMessage = `Barcode ${barcode} đã được quét trong giao dịch này.`;
      return;
    }
    if (this.scannedBooks.length >= 3) {
      this.errorMessage = 'Mỗi giao dịch chỉ xử lý tối đa 3 tài liệu.';
      return;
    }
    if (this.activeTab === 'borrow' && !this.selectedTicket) {
      this.errorMessage = 'Hãy tải một phiếu đã duyệt trước khi quét sách.';
      return;
    }

    const expectedItem = this.activeTab === 'borrow'
      ? this.selectedTicket?.items.find(item => item.barcode === barcode)
      : undefined;
    if (this.activeTab === 'borrow' && !expectedItem) {
      this.errorMessage = `Barcode ${barcode} không thuộc phiếu #${this.selectedTicket?.ticketId}.`;
      return;
    }

    this.isScanningBook = true;
    this.errorMessage = '';
    this.successMessage = '';
    const activeItemRequest = this.activeTab === 'return'
      ? this.circulationService.getActiveLoanItemByBarcode(barcode)
      : of(expectedItem);
    forkJoin({
      copy: this.circulationService.getBookCopyByBarcode(barcode),
      activeItem: activeItemRequest,
    }).subscribe({
      next: ({ copy, activeItem }) => {
        this.isScanningBook = false;
        const validationError = this.validateCopyStatus(copy);
        if (validationError) {
          this.errorMessage = validationError;
          return;
        }
        this.scannedBooks = [...this.scannedBooks, this.toCounterBook(copy, activeItem)];
        this.barcodeInput = '';
      },
      error: error => {
        this.isScanningBook = false;
        this.errorMessage = this.extractError(error, `Không tìm thấy bản sao có barcode ${barcode}.`);
      },
    });
  }

  removeBook(barcode: string): void {
    this.scannedBooks = this.scannedBooks.filter(book => book.barcode !== barcode);
  }

  completeTransaction(): void {
    if (!this.canSubmit) return;
    const barcodes = this.scannedBooks.map(book => book.barcode);
    this.isSubmitting = true;
    this.errorMessage = '';
    this.successMessage = '';
    const request = this.activeTab === 'borrow'
      ? this.circulationService.checkout(this.selectedTicket!.ticketId, barcodes)
      : this.circulationService.returnBooks(barcodes, crypto.randomUUID(), this.returnConditionNote);

    request.subscribe({
      next: operation => {
        this.isSubmitting = false;
        this.lastOperation = operation;
        this.successMessage = operation.message || 'Giao dịch hoàn tất thành công.';
        this.selectedTicket = null;
        this.ticketInput = '';
        this.barcodeInput = '';
        this.scannedBooks = [];
        this.returnConditionNote = '';
      },
      error: error => {
        this.isSubmitting = false;
        this.errorMessage = this.extractError(error, 'Không thể hoàn tất giao dịch.');
      },
    });
  }

  resetForm(): void {
    this.ticketInput = '';
    this.barcodeInput = '';
    this.returnConditionNote = '';
    this.selectedTicket = null;
    this.scannedBooks = [];
    this.isResolvingTicket = false;
    this.isScanningBook = false;
    this.isSubmitting = false;
    this.errorMessage = '';
    this.successMessage = '';
    this.lastOperation = null;
  }

  statusLabel(status: string): string {
    return ({ PENDING: 'chờ duyệt', RESERVED: 'đã duyệt', BORROWING: 'đang mượn', OVERDUE: 'quá hạn', RETURNED: 'đã trả', CANCELLED: 'đã hủy', REJECTED: 'từ chối' } as Record<string, string>)[status] || status;
  }

  private parseTicketId(raw: string): number | null {
    const value = raw.trim();
    const match = value.match(/^(?:#|HUIT-PM-)?(\d+)$/i);
    if (!match) return null;
    const ticketId = Number(match[1]);
    return Number.isSafeInteger(ticketId) && ticketId > 0 ? ticketId : null;
  }

  private validateCopyStatus(copy: BookCopy): string | null {
    const status = copy.status?.toUpperCase();
    if (this.activeTab === 'borrow' && status !== 'RESERVED') {
      return `Bản sao ${copy.barcode} đang ở trạng thái ${copy.status}, không còn được đặt giữ.`;
    }
    if (this.activeTab === 'return' && status !== 'BORROWING' && status !== 'OVERDUE') {
      return `Bản sao ${copy.barcode} không ở trạng thái đang mượn hoặc quá hạn.`;
    }
    return null;
  }

  private toCounterBook(copy: BookCopy, expectedItem?: LoanItem): CounterBook {
    return {
      ...copy,
      displayTitle: expectedItem?.bookTitle || `Tài liệu #${copy.bookId}`,
      displayAuthor: expectedItem?.author,
      loanTicketId: expectedItem?.ticketId,
    };
  }

  private extractError(error: any, fallback: string): string {
    const message = error?.error?.detail ?? error?.error?.message ?? error?.error;
    return typeof message === 'string' && message.trim() ? message : fallback;
  }
}
