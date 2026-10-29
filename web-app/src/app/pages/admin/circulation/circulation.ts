import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, Optional, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, of, switchMap } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { ReturnInspection } from './return-inspection';
import { BrowserMultiFormatReader, IScannerControls } from '@zxing/browser';
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
  imports: [CommonModule, FormsModule, ReturnInspection],
  templateUrl: './circulation.html',
  styleUrls: ['./circulation.css'],
})
export class Circulation implements OnInit, OnDestroy {
  @ViewChild('cameraVideo') cameraVideo?: ElementRef<HTMLVideoElement>;
  @ViewChild(ReturnInspection) returnDesk?: ReturnInspection;
  returnLocked = false;
  returnInitialTicketId: number | null = null;

  activeTab: 'borrow' | 'return' = 'borrow';
  borrowMode: 'approved' | 'manual' = 'approved';
  ticketInput = '';
  barcodeInput = '';
  manualMssv = '';
  manualTicketType: 'BORROW' | 'ONSITE' = 'BORROW';
  manualNote = '';
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
  private scannerControls?: IScannerControls;
  private detectingFrame = false;
  private counterClock?: number;
  isCounterOpen = this.computeCounterOpen();

  constructor(
    private circulationService: CirculationService,
    private cdr: ChangeDetectorRef,
    @Optional() private route?: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    this.counterClock = window.setInterval(() => {
      this.isCounterOpen = this.computeCounterOpen();
      this.cdr.detectChanges();
    }, 30_000);
    const referenceType = this.route?.snapshot.queryParamMap.get('referenceType');
    const referenceId = this.route?.snapshot.queryParamMap.get('referenceId');
    if (referenceType === 'LOAN_TICKET' && referenceId) {
      if (this.route?.snapshot.queryParamMap.get('mode') === 'return') {
        const ticketId = this.parseTicketId(referenceId);
        if (ticketId !== null) { this.activeTab = 'return'; this.returnInitialTicketId = ticketId; }
      } else {
        this.borrowMode = 'approved';
        this.ticketInput = referenceId;
        this.resolveTicket();
      }
    }
  }

  get canSubmit(): boolean {
    if (this.isSubmitting || this.scannedBooks.length === 0) return false;
    if (this.activeTab === 'return') return false;
    if (this.borrowMode === 'manual') return this.manualMssv.trim().length > 0;
    if (!this.selectedTicket || this.selectedTicket.status !== 'RESERVED') return false;
    const expected = new Set(this.selectedTicket.items.map(item => item.barcode));
    return expected.size === this.scannedBooks.length
      && this.scannedBooks.every(book => expected.has(book.barcode));
  }

  switchTab(tab: 'borrow' | 'return'): void {
    if (this.isSubmitting || this.returnDesk?.locked) return;
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
    if (this.isResolvingTicket || this.isSubmitting) return;
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

    this.circulationService.getLoan(ticketId).pipe(
      switchMap(ticket => ticket.status === 'PENDING'
        ? this.circulationService.approveReservation(ticketId).pipe(
            switchMap(() => this.circulationService.getLoan(ticketId)),
          )
        : of(ticket)),
    ).subscribe({
      next: ticket => {
        this.isResolvingTicket = false;
        if (ticket.status !== 'RESERVED') {
          this.errorMessage = `Phiếu #${ticket.ticketId} đang ở trạng thái ${this.statusLabel(ticket.status)}; chỉ phiếu đang giữ chỗ hợp lệ mới được giao sách.`;
          this.cdr.detectChanges();
          return;
        }
        this.selectedTicket = ticket;
        this.ticketInput = String(ticket.ticketId);
        this.cdr.detectChanges();
      },
      error: error => {
        this.isResolvingTicket = false;
        this.errorMessage = this.extractError(error, `Không thể tải phiếu #${ticketId}.`);
        this.cdr.detectChanges();
      },
    });
  }

  scanBookBarcode(): void {
    if (this.activeTab === 'return') { this.returnDesk?.addBarcode(this.barcodeInput); return; }
    const barcode = this.barcodeInput.trim();
    if (!barcode) return;
    if (this.isSubmitting) {
      this.errorMessage = 'Hệ thống đang ghi nhận lần quét trước. Vui lòng chờ trong giây lát.';
      return;
    }
    if (this.scannedBooks.some(book => book.barcode === barcode)) {
      this.errorMessage = `Barcode ${barcode} đã được quét trong giao dịch này.`;
      return;
    }
    if (this.scannedBooks.length >= 3) {
      this.errorMessage = 'Mỗi giao dịch chỉ xử lý tối đa 3 tài liệu.';
      return;
    }
    if (this.activeTab === 'borrow' && this.borrowMode === 'approved' && !this.selectedTicket) {
      this.errorMessage = 'Hãy tải một phiếu giữ chỗ trước khi quét sách.';
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
    const activeItemRequest = of(expectedItem);
    forkJoin({
      copy: this.circulationService.getBookCopyByBarcode(barcode),
      activeItem: activeItemRequest,
    }).subscribe({
      next: ({ copy, activeItem }) => {
        this.isScanningBook = false;
        const validationError = this.validateCopyStatus(copy);
        if (validationError) {
          this.errorMessage = validationError;
          this.cdr.detectChanges();
          return;
        }
        const counterBook = this.toCounterBook(copy, activeItem);
        this.barcodeInput = '';
        this.scannedBooks = [...this.scannedBooks, counterBook];
        this.cdr.detectChanges();
      },
      error: error => {
        this.isScanningBook = false;
        this.errorMessage = this.extractError(error, `Không tìm thấy bản sao có barcode ${barcode}.`);
        this.cdr.detectChanges();
      },
    });
  }

  removeBook(barcode: string): void {
    this.scannedBooks = this.scannedBooks.filter(book => book.barcode !== barcode);
  }

  completeTransaction(): void {
    if (!this.canSubmit || this.activeTab === 'return') return;
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
      : this.circulationService.checkout(this.selectedTicket!.ticketId, barcodes);

    request.subscribe({
      next: operation => {
        this.isSubmitting = false;
        this.lastOperation = operation;
        this.successMessage = '';
        this.selectedTicket = null;
        this.ticketInput = '';
        this.barcodeInput = '';
        this.scannedBooks = [];
        this.manualMssv = '';
        this.manualNote = '';
        this.cdr.detectChanges();
      },
      error: error => {
        this.isSubmitting = false;
        this.errorMessage = this.extractError(error, 'Không thể hoàn tất giao dịch.');
        this.cdr.detectChanges();
      },
    });
  }

  resetForm(): void {
    if (this.isSubmitting) return;
    this.ticketInput = '';
    this.barcodeInput = '';
    this.manualMssv = '';
    this.manualTicketType = 'BORROW';
    this.manualNote = '';
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
    if (this.isSubmitting || this.returnDesk?.locked) return;
    this.cameraTarget = target;
    if (!navigator.mediaDevices?.getUserMedia) {
      this.cameraError = 'Thiết bị hoặc trình duyệt không cho phép truy cập camera. Bạn vẫn có thể dùng máy quét USB hoặc nhập mã thủ công.';
      this.showCamera = true;
      return;
    }

    this.stopCamera();
    this.cameraTarget = target;
    this.cameraError = '';
    this.showCamera = true;
    this.cdr.detectChanges();
    try {
      await new Promise(resolve => window.setTimeout(resolve));
      const video = this.cameraVideo?.nativeElement;
      if (!video) {
        throw new Error('Không tìm thấy vùng hiển thị camera.');
      }

      const Detector = (window as any).BarcodeDetector;
      if (!Detector) {
        await this.startZxingCamera(video);
        return;
      }

      this.cameraStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
      video.srcObject = this.cameraStream;
      await video.play();
      const formats = target === 'ticket'
        ? ['qr_code']
        : ['code_128', 'code_39', 'ean_13', 'ean_8', 'qr_code'];
      const detector = new Detector({ formats });
      this.cameraTimer = window.setInterval(() => void this.detectCameraFrame(detector), 450);
    } catch {
      this.cameraError = 'Không thể mở camera. Hãy cấp quyền camera hoặc nhập mã thủ công.';
      this.releaseCameraResources();
      this.cdr.detectChanges();
    }
  }

  stopCamera(): void {
    if (this.cameraTimer) {
      window.clearInterval(this.cameraTimer);
      this.cameraTimer = undefined;
    }
    this.releaseCameraResources();
    this.showCamera = false;
    this.detectingFrame = false;
  }

  ngOnDestroy(): void {
    if (this.counterClock) window.clearInterval(this.counterClock);
    this.stopCamera();
  }

  private computeCounterOpen(now = new Date()): boolean {
    const minutes = now.getHours() * 60 + now.getMinutes();
    return minutes >= 7 * 60 + 30 && minutes < 17 * 60;
  }

  statusLabel(status: string): string {
    return ({ PENDING: 'phiếu cũ cần kiểm tra', RESERVED: 'đang giữ chỗ', BORROWING: 'đang mượn', OVERDUE: 'quá hạn', RETURNED: 'đã trả', CANCELLED: 'đã hủy', REJECTED: 'từ chối' } as Record<string, string>)[status] || status;
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

  private async startZxingCamera(video: HTMLVideoElement): Promise<void> {
    const reader = new BrowserMultiFormatReader(undefined, {
      delayBetweenScanAttempts: 120,
      delayBetweenScanSuccess: 500,
    });
    this.scannerControls = await reader.decodeFromConstraints(
      { video: { facingMode: { ideal: 'environment' } }, audio: false },
      video,
      result => {
        const value = result?.getText().trim();
        if (!value || !this.showCamera) return;
        const target = this.cameraTarget;
        this.stopCamera();
        this.handleScannedValue(target, value);
      },
    );
  }

  private handleScannedValue(target: 'ticket' | 'barcode', value: string): void {
    if (target === 'barcode' && this.activeTab === 'return') { this.returnDesk?.addBarcode(value); return; }
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
          this.cdr.detectChanges();
          return;
        }
        this.errorMessage = 'QR hợp lệ nhưng không chứa phiếu mượn hoặc MSSV có thể sử dụng.';
        this.cdr.detectChanges();
      },
      error: error => {
        this.isResolvingTicket = false;
        this.errorMessage = this.extractError(error, 'QR không hợp lệ, đã hết hạn hoặc không thuộc hệ thống HUIT.');
        this.cdr.detectChanges();
      },
    });
  }

  private releaseCameraResources(): void {
    this.scannerControls?.stop();
    this.scannerControls = undefined;
    this.cameraStream?.getTracks().forEach(track => track.stop());
    this.cameraStream = undefined;
  }
}
