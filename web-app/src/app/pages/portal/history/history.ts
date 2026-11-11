import {
  Component,
  OnInit,
  ChangeDetectorRef,
  HostListener,
  DestroyRef,
  inject,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { timeout } from 'rxjs';
import { RouterLink, Router } from '@angular/router';
import * as QRCode from 'qrcode';
import { AuthService } from '../../../services/auth.service';
import { CirculationService } from '../../../services/circulation.service';
import { DebtEntry, PenaltyCase, PenaltyService } from '../../../services/penalty.service';
import { DialogService } from '../../../services/dialog.service';
import { LibraryPolicyService } from '../../../services/library-policy.service';
import { remainingDebt } from '../../../services/debt-amount';

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
  itemStatus?: string;
  decisionNote?: string;
  createdAt?: string;
  pickupDeadline?: string;
  dueDate?: string;
  returnDate?: string;
  physicalReturnAt?: string;
  lossReportedAt?: string;
  lossReportDescription?: string;
  isRecalled?: boolean;
  recallRequestedAt?: string;
  recallReason?: string;
  recallDueDate?: string;
  recallPreviousDueDate?: string;
  daysRemaining?: number;
  progress?: number;
  statusType?: 'normal' | 'warning' | 'overdue' | 'pending';
  renewalCount?: number;
}

export interface LoanTicketGroup extends LoanItem {
  items: LoanItem[];
  timeGroup?: string;
}

@Component({
  selector: 'app-history',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './history.html',
  styleUrls: ['./history.css'],
})
export class History implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  isLoading = true;
  isLoggedIn = false;
  errorMessage = '';
  toastMessage: string | null = null;
  private toastTimer?: any;

  // Tabs state
  selectedTab: 'BORROWING' | 'PENDING' | 'ALL' | 'PAST' | 'CANCELLED' = 'BORROWING';

  borrowingList: LoanTicketGroup[] = [];
  pendingList: LoanTicketGroup[] = [];
  pastList: LoanTicketGroup[] = [];
  cancelledList: LoanTicketGroup[] = [];
  allList: LoanTicketGroup[] = [];
  displayedAllCount = 7;

  // Backward compatibility
  activeBorrows: LoanTicketGroup[] = [];
  pastBorrows: LoanTicketGroup[] = [];

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
  maximumRenewalCount = 0;
  renewalDays = 0;
  policyLoaded = false;
  lossReportItem: LoanItem | null = null;
  lossReportDescription = '';
  lossReportSubmitting = false;
  lossReportError = '';

  constructor(
    private authService: AuthService,
    private circulationService: CirculationService,
    private penaltyService: PenaltyService,
    private router: Router,
    private cdr: ChangeDetectorRef,
    private dialog: DialogService,
    private policyService: LibraryPolicyService,
  ) {}

  ngOnInit(): void {
    this.loadPolicy();
    this.isLoggedIn = this.authService.isLoggedIn();
    if (this.isLoggedIn) {
      this.loadLoans();
      this.loadFinancials();
    } else {
      this.isLoading = false;
    }
  }

  private loadPolicy(): void {
    this.policyService.getPublicPolicy().subscribe({
      next: (policy) => {
        this.maximumRenewalCount = policy.maximumRenewalCount;
        this.renewalDays = policy.renewalDays;
        this.policyLoaded = true;
        this.cdr.detectChanges();
      },
      error: () => {
        this.policyLoaded = false;
        this.errorMessage =
          this.errorMessage || 'Không thể tải quy định gia hạn. Vui lòng tải lại trang.';
        this.cdr.detectChanges();
      },
    });
  }

  get outstandingDebt(): number {
    return this.debtEntries.reduce((sum, entry) => sum + remainingDebt(entry), 0);
  }
  get openDebtEntries(): DebtEntry[] {
    return this.debtEntries.filter((entry) => remainingDebt(entry) > 0);
  }
  amountDue(entry: DebtEntry): number {
    return remainingDebt(entry);
  }

  loadFinancials(): void {
    this.penaltyService.getMyLedger().subscribe({
      next: (value) => {
        this.debtEntries = value || [];
        this.cdr.markForCheck();
      },
    });
    this.penaltyService.getMyCases().subscribe({
      next: (value) => {
        this.penaltyCases = value || [];
        this.cdr.markForCheck();
      },
    });
  }

  refresh(): void {
    if (!this.isLoggedIn) return;
    this.loadLoans();
    this.loadFinancials();
  }

  setTab(tab: 'BORROWING' | 'PENDING' | 'ALL' | 'PAST' | 'CANCELLED'): void {
    this.selectedTab = tab;
    if (tab === 'ALL') this.displayedAllCount = 7;
  }

  get visibleAllList(): LoanTicketGroup[] {
    return this.allList.slice(0, this.displayedAllCount);
  }

  @HostListener('window:scroll')
  onWindowScroll(): void {
    if (this.selectedTab !== 'ALL' || this.displayedAllCount >= this.allList.length) return;
    const remaining = document.documentElement.scrollHeight - (window.scrollY + window.innerHeight);
    if (remaining < 240) this.displayedAllCount += 7;
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
      },
    });
  }

  private processLoans(items: any[]): void {
    const borrowing: LoanItem[] = [];
    const pending: LoanItem[] = [];
    const past: LoanItem[] = [];
    const cancelled: LoanItem[] = [];
    const all: LoanItem[] = [];

    items.forEach((raw) => {
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
        itemStatus: String(raw.itemStatus || raw.returnStatus || statusUpper).toUpperCase(),
        decisionNote: raw.decisionNote,
        createdAt: raw.createdAt,
        pickupDeadline: raw.pickupDeadline,
        dueDate: raw.dueDate,
        returnDate: raw.returnDate,
        physicalReturnAt: raw.physicalReturnAt,
        lossReportedAt: raw.lossReportedAt,
        lossReportDescription: raw.lossReportDescription,
        isRecalled: raw.isRecalled === true,
        recallRequestedAt: raw.recallRequestedAt,
        recallReason: raw.recallReason,
        recallDueDate: raw.recallDueDate,
        recallPreviousDueDate: raw.recallPreviousDueDate,
        daysRemaining: days !== null ? days : undefined,
        progress,
        statusType,
        renewalCount: raw.renewalCount != null ? Number(raw.renewalCount) : 0,
      };

      all.push(item);

      if (statusUpper === 'BORROWING' || statusUpper === 'OVERDUE') {
        borrowing.push(item);
      } else if (statusUpper === 'PENDING' || statusUpper === 'RESERVED') {
        pending.push(item);
      } else if (statusUpper === 'RETURNED') {
        past.push(item);
      } else if (statusUpper === 'CANCELLED' || statusUpper === 'REJECTED') {
        cancelled.push(item);
      }
    });

    this.allList = this.groupLoans(all);
    this.borrowingList = this.groupLoans(borrowing);
    this.pendingList = this.groupLoans(pending);
    this.pastList = this.groupLoans(past);
    this.cancelledList = this.groupLoans(cancelled);

    // Backward compatibility for existing references
    this.activeBorrows = [...this.borrowingList, ...this.pendingList];
    this.pastBorrows = this.pastList;

    // Tự động chuyển tab nếu tab hiện tại trống nhưng tab khác có dữ liệu
    if (this.selectedTab === 'BORROWING' && borrowing.length === 0 && pending.length > 0) {
      this.selectedTab = 'PENDING';
    }
  }

  private groupLoans(items: LoanItem[]): LoanTicketGroup[] {
    const byTicket = new Map<number, LoanItem[]>();
    items.forEach((item) =>
      byTicket.set(item.ticketId, [...(byTicket.get(item.ticketId) || []), item]),
    );
    return [...byTicket.values()]
      .map((ticketItems) => ({
        ...ticketItems[0],
        bookTitle:
          ticketItems.length === 1
            ? ticketItems[0].bookTitle
            : `${ticketItems.length} tài liệu trong phiếu`,
        items: ticketItems,
        timeGroup: this.timeGroup(ticketItems[0].createdAt),
      }))
      .sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime());
  }

  private timeGroup(createdAt?: string): string {
    if (!createdAt) return 'Cũ hơn';
    const days = Math.floor((Date.now() - new Date(createdAt).getTime()) / 86_400_000);
    if (days <= 1) return 'Trong 1 ngày qua';
    if (days <= 7) return 'Trong 1 tuần qua';
    if (days <= 31) return 'Trong 1 tháng qua';
    return 'Cũ hơn 1 tháng';
  }

  showTimeHeader(index: number): boolean {
    return (
      index === 0 ||
      this.visibleAllList[index - 1]?.timeGroup !== this.visibleAllList[index]?.timeGroup
    );
  }

  itemStatusLabel(item: LoanItem): string {
    const status = (item.itemStatus || item.status || '').toUpperCase();
    if (item.lossReportedAt && this.isHeldItem(item)) return 'Đã báo mất · chờ thủ thư';
    if (item.physicalReturnAt) {
      return ['DAMAGED', 'LOST', 'SETTLED'].includes(status) ? 'Đã trả · sách hỏng' : 'Đã trả';
    }
    return (
      (
        {
          BORROWING: 'Đang giữ',
          OVERDUE: 'Quá hạn',
          RETURNED: 'Đã trả',
          DAMAGED: 'Hồ sơ hỏng · chưa xác nhận nhận lại',
          LOST: 'Báo mất · đang xử lý',
          SETTLED: 'Đã giải quyết hồ sơ',
          CANCELLED: 'Đã hủy',
          PENDING: 'Chờ nhận',
          RESERVED: 'Đã giữ chỗ',
        } as Record<string, string>
      )[status] || status
    );
  }

  canRenewItem(item: LoanItem): boolean {
    return (
      item.status === 'BORROWING' &&
      item.itemStatus === 'BORROWING' &&
      item.detailId != null &&
      !item.returnDate &&
      !item.physicalReturnAt &&
      !item.lossReportedAt &&
      !item.isRecalled
    );
  }

  canReportLoss(item: LoanItem): boolean {
    return (
      ['BORROWING', 'OVERDUE'].includes(item.status) &&
      item.detailId != null &&
      this.isHeldItem(item) &&
      !item.lossReportedAt
    );
  }

  reportableItems(ticket: LoanTicketGroup): LoanItem[] {
    return ticket.items.filter((item) => this.canReportLoss(item));
  }

  openLossReport(item: LoanItem): void {
    if (!this.canReportLoss(item) || this.lossReportSubmitting || this.renewingTicketId !== null)
      return;
    this.lossReportItem = item;
    this.lossReportDescription = '';
    this.lossReportError = '';
  }

  closeLossReport(): void {
    if (this.lossReportSubmitting) return;
    this.lossReportItem = null;
    this.lossReportError = '';
  }

  submitLossReport(): void {
    const item = this.lossReportItem;
    const description = this.lossReportDescription.trim();
    if (!item || !this.canReportLoss(item) || this.lossReportSubmitting) return;
    if (!description || description.length > 1000) {
      this.lossReportError = 'Nhập mô tả báo mất, tối đa 1000 ký tự.';
      return;
    }
    this.lossReportSubmitting = true;
    this.lossReportError = '';
    this.circulationService
      .reportLoss(item.ticketId, item.detailId!, description)
      .pipe(timeout(30_000), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.lossReportSubmitting = false;
          if (
            !updated ||
            updated.ticketId !== item.ticketId ||
            updated.detailId !== item.detailId ||
            !updated.lossReportedAt ||
            Number.isNaN(Date.parse(updated.lossReportedAt)) ||
            typeof updated.lossReportDescription !== 'string' ||
            !updated.lossReportDescription.trim()
          ) {
            this.lossReportError =
              'Chưa xác nhận được kết quả. Làm mới lịch sử để kiểm tra hoặc gửi lại báo mất.';
          } else {
            this.processLoans(
              this.allList
                .flatMap((ticket) => ticket.items)
                .map((book) =>
                  book.detailId === updated.detailId && book.ticketId === updated.ticketId
                    ? { ...book, ...updated }
                    : book,
                ),
            );
            this.lossReportItem = null;
            this.showToast(
              'Đã gửi báo mất đến thủ thư. Vui lòng liên hệ thư viện để được hướng dẫn xử lý.',
            );
          }
          this.cdr.markForCheck();
        },
        error: (error) => {
          this.lossReportSubmitting = false;
          const reason = error?.error?.detail ?? error?.error?.message ?? error?.error;
          this.lossReportError =
            typeof reason === 'string' && reason.trim()
              ? reason
              : 'Chưa xác nhận được kết quả. Mô tả được giữ lại; kiểm tra lịch sử hoặc gửi lại báo mất.';
          this.cdr.markForCheck();
        },
      });
  }

  isHeldItem(item: LoanItem): boolean {
    return (
      ['BORROWING', 'OVERDUE'].includes(item.itemStatus || '') &&
      !item.returnDate &&
      !item.physicalReturnAt
    );
  }

  renewableItems(ticket: LoanTicketGroup): LoanItem[] {
    return ticket.items.filter((item) => this.canRenewItem(item));
  }

  statusLabel(status: string): string {
    return (
      (
        {
          BORROWING: 'Đang mượn',
          OVERDUE: 'Quá hạn',
          PENDING: 'Chờ thủ thư duyệt',
          RESERVED: 'Chờ nhận sách',
          RETURNED: 'Đã hoàn trả',
          CANCELLED: 'Đã hủy',
          REJECTED: 'Đã từ chối',
        } as Record<string, string>
      )[status] || status
    );
  }

  statusClass(status: string): string {
    if (status === 'BORROWING' || status === 'OVERDUE') return 'borrowing';
    if (status === 'PENDING' || status === 'RESERVED') return 'pending';
    if (status === 'CANCELLED' || status === 'REJECTED') return 'cancelled';
    return 'returned';
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
            light: '#ffffff',
          },
          errorCorrectionLevel: 'M',
        })
          .then((url: string) => {
            this.qrCodeDataUrl = url;
            this.isGeneratingQr = false;
            this.cdr.detectChanges();
          })
          .catch((err: any) => this.handleQrError(err));
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

  async renewBook(item: LoanItem): Promise<void> {
    if (!this.canRenewItem(item) || this.renewingTicketId !== null || this.lossReportSubmitting)
      return;
    if (!this.policyLoaded) {
      this.dialog.show({
        title: 'Chưa tải được quy định',
        message: 'Vui lòng tải lại trang trước khi gia hạn.',
        type: 'warning',
        confirmText: 'Đã hiểu',
      });
      return;
    }
    if (item.renewalCount !== undefined && item.renewalCount >= this.maximumRenewalCount) {
      this.dialog.show({
        title: 'Không thể gia hạn',
        message: `Tài liệu này đã đạt số lần gia hạn tối đa (${item.renewalCount}/${this.maximumRenewalCount} lần). Vui lòng hoàn trả đúng hạn tại quầy!`,
        type: 'warning',
        confirmText: 'Đã hiểu',
      });
      return;
    }

    const accepted = await this.dialog.confirm({
      title: 'Xác nhận gia hạn',
      message: `Gia hạn thêm ${this.renewalDays} ngày cho cuốn sách “${item.bookTitle}” (Phiếu #${item.ticketId})?`,
      type: 'info',
      confirmText: 'Gia hạn',
    });
    if (!accepted) {
      return;
    }

    this.renewingTicketId = item.ticketId;
    this.circulationService.renewLoan(item.ticketId, item.detailId).subscribe({
      next: (res: any) => {
        this.renewingTicketId = null;
        const newDueStr = res.dueDate
          ? new Date(res.dueDate).toLocaleDateString('vi-VN')
          : `thêm ${this.renewalDays} ngày`;
        this.showToast(
          `Gia hạn thành công! Hạn trả mới: ${newDueStr} (Lần ${res.renewalCount}/${this.maximumRenewalCount})`,
        );
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
        this.dialog.show({
          title: 'Không thể gia hạn',
          message: msg,
          type: 'error',
          confirmText: 'Đóng',
        });
      },
    });
  }

  async cancelReservation(item: LoanItem): Promise<void> {
    const accepted = await this.dialog.confirm({
      title: 'Hủy phiếu đặt giữ',
      message: `Bạn có chắc muốn hủy phiếu #${item.ticketId} cho cuốn sách “${item.bookTitle}”?`,
      type: 'warning',
      confirmText: 'Hủy phiếu',
    });
    if (!accepted) {
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
        const rawMessage =
          typeof err?.error === 'string' ? err.error : (err?.error?.detail ?? err?.error?.message);
        const msg =
          typeof rawMessage === 'string' && rawMessage.trim()
            ? rawMessage
            : 'Không thể hủy phiếu mượn vào lúc này!';
        this.dialog.show({
          title: 'Không thể hủy phiếu',
          message: msg,
          type: 'error',
          confirmText: 'Đóng',
        });
        this.loadLoans();
      },
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
