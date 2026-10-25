import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnDestroy, OnInit, Optional, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
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
export class Circulation implements OnInit, OnDestroy {
  @ViewChild('cameraVideo') cameraVideo?: ElementRef<HTMLVideoElement>;

  activeTab: 'borrow' | 'return' = 'borrow';
  borrowMode: 'approved' | 'manual' = 'approved';
  ticketInput = '';
  barcodeInput = '';
  manualMssv = '';
  manualTicketType: 'BORROW' | 'ONSITE' = 'BORROW';
  manualNote = '';
  returnConditionNote = '';
  selectedTicket: LoanTicketDetail | null = null;
  scannedBooks: CounterBook[] = [];
  isResolvingTicket = false;
  isScanningBook = false;
  isSubmitting = false;
  errorMessage = '';
  successMessage = '';
  lastOperation: CirculationOperationResponse | null = null;
  showCamera = false;
  cameraTarget: 'ticket' | 'barcode' = 'ticket';
  cameraError = '';
  private cameraStream?: MediaStream;
  private cameraTimer?: number;
  private detectingFrame = false;

  constructor(
    private circulationService: CirculationService,
    @Optional() private route?: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    const referenceType = this.route?.snapshot.queryParamMap.get('referenceType');
    const referenceId = this.route?.snapshot.queryParamMap.get('referenceId');
    if (referenceType === 'LOAN_TICKET' && referenceId) {
      this.borrowMode = 'approved';
      this.ticketInput = referenceId;
      this.resolveTicket();
    }
  }

  get canSubmit(): boolean {
    if (this.isSubmitting || this.scannedBooks.length === 0) return false;
    if (this.activeTab === 'return') return true;
    if (this.borrowMode === 'manual') return this.manualMssv.trim().length > 0;
    if (!this.selectedTicket || this.selectedTicket.status !== 'RESERVED') return false;
    const expected = new Set(this.selectedTicket.items.map(item => item.barcode));
    return expected.size === this.scannedBooks.length
      && this.scannedBooks.every(book => expected.has(book.barcode));
  }

  switchTab(tab: 'borrow' | 'return'): void {
    this.activeTab = tab;
    this.resetForm();
  }

  switchBorrowMode(mode: 'approved' | 'manual'): void {
    this.borrowMode = mode;
    this.ticketInput = '';
    this.manualMssv = '';
    this.manualTicketType = 'BORROW';
    this.manualNote = '';
    this.selectedTicket = null;
    this.scannedBooks = [];
    this.barcodeInput = '';
    this.errorMessage = '';
    this.successMessage = '';
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
    if (this.activeTab === 'borrow' && this.borrowMode === 'approved' && !this.selectedTicket) {
      this.errorMessage = 'Hãy tải một phiếu đã duyệt trước khi quét sách.';
      return;
    }

    const expectedItem = this.activeTab === 'borrow' && this.borrowMode === 'approved'
      ? this.selectedTicket?.items.find(item => item.barcode === barcode)
      : undefined;
    if (this.activeTab === 'borrow' && this.borrowMode === 'approved' && !expectedItem) {
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
    const request = this.activeTab === 'borrow' && this.borrowMode === 'manual'
      ? this.circulationService.manualCheckout(
          this.manualMssv,
          this.manualTicketType,
          barcodes,
          this.manualNote,
        )
      : this.activeTab === 'borrow'
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
        this.manualMssv = '';
        this.manualNote = '';
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
    this.manualMssv = '';
    this.manualTicketType = 'BORROW';
    this.manualNote = '';
    this.returnConditionNote = '';
    this.selectedTicket = null;
    this.scannedBooks = [];
    this.isResolvingTicket = false;
    this.isScanningBook = false;
    this.isSubmitting = false;
    this.errorMessage = '';
    this.successMessage = '';
    this.lastOperation = null;
    this.stopCamera();
  }

  async startCamera(target: 'ticket' | 'barcode'): Promise<void> {
    const Detector = (window as any).BarcodeDetector;
    if (!Detector || !navigator.mediaDevices?.getUserMedia) {
      this.cameraError = 'Trình duyệt này chưa hỗ trợ quét mã bằng camera. Bạn vẫn có thể dùng máy quét USB hoặc nhập mã thủ công.';
      this.showCamera = true;
      return;
    }

    this.stopCamera();
    this.cameraTarget = target;
    this.cameraError = '';
    this.showCamera = true;
    try {
      this.cameraStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
      window.setTimeout(() => {
        const video = this.cameraVideo?.nativeElement;
        if (!video || !this.cameraStream) return;
        video.srcObject = this.cameraStream;
        void video.play();
        const formats = target === 'ticket'
          ? ['qr_code']
          : ['code_128', 'code_39', 'ean_13', 'ean_8', 'qr_code'];
        const detector = new Detector({ formats });
        this.cameraTimer = window.setInterval(() => void this.detectCameraFrame(detector), 450);
      });
    } catch {
      this.cameraError = 'Không thể mở camera. Hãy cấp quyền camera hoặc nhập mã thủ công.';
      this.releaseCameraStream();
    }
  }

  stopCamera(): void {
    if (this.cameraTimer) {
      window.clearInterval(this.cameraTimer);
      this.cameraTimer = undefined;
    }
    this.releaseCameraStream();
    this.showCamera = false;
    this.detectingFrame = false;
  }

  ngOnDestroy(): void {
    this.stopCamera();
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
    if (this.activeTab === 'borrow' && this.borrowMode === 'manual' && status !== 'AVAILABLE') {
      return `Bản sao ${copy.barcode} đang ở trạng thái ${copy.status}, không sẵn sàng để lập phiếu mượn trực tiếp.`;
    }
    if (this.activeTab === 'borrow' && this.borrowMode === 'approved' && status !== 'RESERVED') {
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

  private async detectCameraFrame(detector: any): Promise<void> {
    const video = this.cameraVideo?.nativeElement;
    if (!video || video.readyState < 2 || this.detectingFrame) return;
    this.detectingFrame = true;
    try {
      const results = await detector.detect(video);
      const value = results?.[0]?.rawValue?.trim();
      if (value) {
        const target = this.cameraTarget;
        this.stopCamera();
        this.handleScannedValue(target, value);
      }
    } catch {
      this.cameraError = 'Camera chưa đọc được mã. Hãy đưa mã vào giữa khung hoặc nhập thủ công.';
    } finally {
      this.detectingFrame = false;
    }
  }

  private handleScannedValue(target: 'ticket' | 'barcode', value: string): void {
    if (target === 'barcode') {
      this.barcodeInput = value;
      this.scanBookBarcode();
      return;
    }
    this.isResolvingTicket = true;
    this.errorMessage = '';
    this.circulationService.resolveQrPayload(value).subscribe({
      next: resolved => {
        this.isResolvingTicket = false;
        if (resolved.type === 'LOAN_TICKET' && resolved.ticketId) {
          this.borrowMode = 'approved';
          this.ticketInput = String(resolved.ticketId);
          this.resolveTicket();
          return;
        }
        if (resolved.type === 'USER_CARD' && resolved.mssv) {
          this.borrowMode = 'manual';
          this.manualMssv = resolved.mssv;
          this.successMessage = `Đã nhận diện thẻ của ${resolved.readerName || resolved.mssv}. Hãy quét các bản sao cần mượn.`;
          return;
        }
        this.errorMessage = 'QR hợp lệ nhưng không chứa phiếu mượn hoặc MSSV có thể sử dụng.';
      },
      error: error => {
        this.isResolvingTicket = false;
        this.errorMessage = this.extractError(error, 'QR không hợp lệ, đã hết hạn hoặc không thuộc hệ thống HUIT.');
      },
    });
  }

  private releaseCameraStream(): void {
    this.cameraStream?.getTracks().forEach(track => track.stop());
    this.cameraStream = undefined;
  }
}
