import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, Router } from '@angular/router';
import * as QRCode from 'qrcode';
import { AuthService } from '../../../services/auth.service';
import { CirculationService } from '../../../services/circulation.service';
import { DebtEntry, PenaltyCase, PenaltyService } from '../../../services/penalty.service';

export interface LoanItem {
  ticketId: number;
  detailId?: number;
  bookId?: number;
  bookTitle: string;
  bookCover?: string;
  author?: string;
  barcode?: string;
  ticketType?: string;
  status: string; // 'PENDING', 'RESERVED', 'BORROWING', 'RETURNED', 'CANCELLED'
  createdAt?: string;
  dueDate?: string;
  returnDate?: string;
  daysRemaining?: number;
  progress?: number;
  statusType?: 'normal' | 'warning' | 'overdue' | 'pending';
  renewalCount?: number;
}

@Component({
  selector: 'app-history',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './history.html',
  styleUrls: ['./history.css']
})
export class History implements OnInit {
  isLoading = true;
  isLoggedIn = false;
  errorMessage = '';
  toastMessage: string | null = null;
  private toastTimer?: any;

  // Tabs state
  selectedTab: 'BORROWING' | 'PENDING' | 'ALL' | 'PAST' = 'BORROWING';

  borrowingList: LoanItem[] = [];
  pendingList: LoanItem[] = [];
  pastList: LoanItem[] = [];
  allList: LoanItem[] = [];

  // Backward compatibility
  activeBorrows: LoanItem[] = [];
  pastBorrows: LoanItem[] = [];

  // QR Code Modal State
  showQrModal = false;
  selectedTicketForQr: LoanItem | null = null;
  qrCodeDataUrl: string | null = null;
  isGeneratingQr = false;

  // Action states
  cancellingTicketId: number | null = null;
  renewingTicketId: number | null = null;
  debtEntries: DebtEntry[] = [];
  penaltyCases: PenaltyCase[] = [];

  constructor(
    private authService: AuthService,
    private circulationService: CirculationService,
    private penaltyService: PenaltyService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.isLoggedIn = this.authService.isLoggedIn();
    if (this.isLoggedIn) {
      this.loadLoans();
      this.loadFinancials();
    } else {
      this.isLoading = false;
    }
  }

  get outstandingDebt(): number {
    return this.debtEntries
      .filter(entry => Number(entry.amount) > 0)
      .reduce((sum, entry) => sum + Math.max(0, Number(entry.amount) - Number(entry.paidAmount || 0)), 0);
  }

  loadFinancials(): void {
    this.penaltyService.getMyLedger().subscribe({ next: value => this.debtEntries = value || [] });
    this.penaltyService.getMyCases().subscribe({ next: value => this.penaltyCases = value || [] });
  }

  setTab(tab: 'BORROWING' | 'PENDING' | 'ALL' | 'PAST'): void {
    this.selectedTab = tab;
  }

  loadLoans(): void {
    this.isLoading = true;
    this.errorMessage = '';

    this.circulationService.getMyLoans().subscribe({
      next: (data: any[]) => {
        this.isLoading = false;
        this.processLoans(data || []);
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isLoading = false;
        console.error('Error fetching loan history:', err);
        if (err.status === 401 || err.status === 403) {
          this.isLoggedIn = false;
        } else {
          this.errorMessage = 'Không thể tải lịch sử mượn sách. Vui lòng thử lại sau!';
        }
        this.cdr.detectChanges();
      }
    });
  }

  private processLoans(items: any[]): void {
    const borrowing: LoanItem[] = [];
    const pending: LoanItem[] = [];
    const past: LoanItem[] = [];
    const all: LoanItem[] = [];

    items.forEach(raw => {
      const statusUpper = (raw.status || '').toUpperCase();
      const days = raw.daysRemaining != null ? Number(raw.daysRemaining) : null;

      let statusType: 'normal' | 'warning' | 'overdue' | 'pending' = 'normal';
      let progress = 0;

      if (statusUpper === 'PENDING' || statusUpper === 'RESERVED') {
        statusType = 'pending';
      } else if (days !== null) {
        if (days < 0) {
          statusType = 'overdue';
          progress = 100;
        } else if (days <= 3) {
          statusType = 'warning';
          progress = Math.min(100, Math.round(((14 - days) / 14) * 100));
        } else {
          statusType = 'normal';
          progress = Math.min(100, Math.max(0, Math.round(((14 - days) / 14) * 100)));
        }
      }

      const item: LoanItem = {
        ticketId: raw.ticketId,
        detailId: raw.detailId,
        bookId: raw.bookId,
        bookTitle: raw.bookTitle || 'Tài liệu Thư viện',
        bookCover: raw.bookCover || '/assets/images/cover_1.jpg',
        author: raw.author || 'Đang cập nhật',
        barcode: raw.barcode,
        ticketType: raw.ticketType || 'BORROW',
        status: statusUpper,
        createdAt: raw.createdAt,
        dueDate: raw.dueDate,
        returnDate: raw.returnDate,
        daysRemaining: days !== null ? days : undefined,
        progress,
        statusType,
        renewalCount: raw.renewalCount != null ? Number(raw.renewalCount) : 0
      };

      all.push(item);

      if (statusUpper === 'BORROWING') {
        borrowing.push(item);
      } else if (statusUpper === 'PENDING' || statusUpper === 'RESERVED') {
        pending.push(item);
      } else {
        past.push(item);
      }
    });

    this.allList = all;
    this.borrowingList = borrowing;
    this.pendingList = pending;
    this.pastList = past;

    // Backward compatibility for existing references
    this.activeBorrows = [...borrowing, ...pending];
    this.pastBorrows = past;

    // Tự động chuyển tab nếu tab hiện tại trống nhưng tab khác có dữ liệu
    if (this.selectedTab === 'BORROWING' && borrowing.length === 0 && pending.length > 0) {
      this.selectedTab = 'PENDING';
    }
  }

  openQrModal(item: LoanItem): void {
    this.selectedTicketForQr = item;
    this.showQrModal = true;
    this.isGeneratingQr = true;
    this.qrCodeDataUrl = null;

    this.circulationService.getTicketQrPayload(item.ticketId).subscribe({
      next: ({ payload }) => {
        QRCode.toDataURL(payload, {
          width: 240,
          margin: 1.5,
          color: {
            dark: '#0f172a',
            light: '#ffffff'
          },
          errorCorrectionLevel: 'M'
        }).then((url: string) => {
          this.qrCodeDataUrl = url;
          this.isGeneratingQr = false;
          this.cdr.detectChanges();
        }).catch((err: any) => this.handleQrError(err));
      },
      error: (err) => this.handleQrError(err),
    });
  }

  private handleQrError(err: unknown): void {
    console.error('Lỗi tạo mã QR:', err);
    this.isGeneratingQr = false;
    this.errorMessage = 'Không thể tạo mã QR bảo mật. Vui lòng thử lại.';
    this.cdr.detectChanges();
  }

  closeQrModal(): void {
    this.showQrModal = false;
    this.selectedTicketForQr = null;
    this.qrCodeDataUrl = null;
  }

  downloadQrCode(): void {
    if (!this.qrCodeDataUrl || !this.selectedTicketForQr) return;
    const link = document.createElement('a');
    link.href = this.qrCodeDataUrl;
    link.download = `HUIT-PhieuMuon-${this.selectedTicketForQr.ticketId}.png`;
    link.click();
    this.showToast('Đã tải hình ảnh mã QR phiếu mượn về máy!');
  }

  renewBook(item: LoanItem): void {
    if (item.renewalCount !== undefined && item.renewalCount >= 1) {
      alert('Tài liệu này đã đạt số lần gia hạn tối đa (1/1 lần). Vui lòng hoàn trả đúng hạn tại quầy!');
      return;
    }

    if (!confirm(`Bạn có chắc chắn muốn gia hạn thêm 10 ngày cho cuốn sách "${item.bookTitle}" (Phiếu #${item.ticketId}) không?`)) {
      return;
    }

    this.renewingTicketId = item.ticketId;
    this.circulationService.renewLoan(item.ticketId, item.detailId).subscribe({
      next: (res: any) => {
        this.renewingTicketId = null;
        const newDueStr = res.dueDate ? new Date(res.dueDate).toLocaleDateString('vi-VN') : 'thêm 10 ngày';
        this.showToast(`Gia hạn thành công! Hạn trả mới: ${newDueStr} (Lần ${res.renewalCount}/1)`);
        this.loadLoans();
      },
      error: (err: any) => {
        this.renewingTicketId = null;
        console.error('Lỗi khi gia hạn sách:', err);
        let msg = 'Không thể gia hạn tài liệu này vào lúc này!';
        if (err.error && typeof err.error === 'string') {
          msg = err.error;
        } else if (err.error?.message) {
          msg = err.error.message;
        }
        alert(msg);
      }
    });
  }

  cancelReservation(item: LoanItem): void {
    if (!confirm(`Bạn có chắc muốn hủy phiếu đặt giữ #${item.ticketId} cho cuốn sách "${item.bookTitle}" không?`)) {
      return;
    }

    this.cancellingTicketId = item.ticketId;
    this.circulationService.cancelReservation(item.ticketId).subscribe({
      next: () => {
        this.cancellingTicketId = null;
        this.showToast(`Đã hủy phiếu mượn #${item.ticketId} thành công!`);
        this.loadLoans();
      },
      error: (err) => {
        this.cancellingTicketId = null;
        console.error('Error cancelling reservation:', err);
        const msg = (err.error && typeof err.error === 'string') ? err.error : 'Không thể hủy phiếu mượn vào lúc này!';
        alert(msg);
      }
    });
  }

  showToast(message: string): void {
    this.toastMessage = message;
    if (this.toastTimer) {
      clearTimeout(this.toastTimer);
    }
    this.toastTimer = setTimeout(() => {
      this.toastMessage = null;
      this.cdr.detectChanges();
    }, 3500);
  }

  goToLogin(): void {
    this.router.navigate(['/login'], { queryParams: { returnUrl: '/history' } });
  }
}
