import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CirculationService, LoanItem, LoanTicketDetail, LoanTicketSummary } from '../../../services/circulation.service';
import { ReturnInspectionReceipt, ReturnInspectionRequest } from '../../../services/return-inspection.models';
import { InspectionDraft, createInspectionDraft, draftError, inspectionPayload, isActiveReturnItem, normalizeSettlement, treatedAsLoss } from './return-inspection.form';

@Component({
  selector: 'app-return-inspection',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './return-inspection.html',
  styleUrl: './return-inspection.css',
})
export class ReturnInspection implements OnInit, OnChanges {
  @Input() counterOpen = false;
  @Input() initialTicketId: number | null = null;
  @Output() scanRequested = new EventEmitter<void>();
  @Output() lockChanged = new EventEmitter<boolean>();
  private readonly destroyRef = inject(DestroyRef);
  barcodeInput = '';
  lookupMode: 'ticket' | 'reader' = 'ticket';
  lookupInput = '';
  matchingLoans: LoanTicketSummary[] = [];
  selectedTicket: LoanTicketDetail | null = null;
  drafts: InspectionDraft[] = [];
  loading = false;
  submitting = false;
  errorMessage = '';
  storageMessage = '';
  editableFailure = false;
  receipt: ReturnInspectionReceipt | null = null;
  receiptTitles: Record<number, string> = {};
  private pendingJson: string | null = null;
  private storageBlocked = false;
  private readonly storageKey: string | null;
  readonly isActiveItem = isActiveReturnItem;
  readonly isLoss = treatedAsLoss;

  constructor(private service: CirculationService, private cdr: ChangeDetectorRef) {
    this.storageKey = this.ownerStorageKey();
    this.restorePending();
  }

  ngOnInit(): void {
    queueMicrotask(() => { if (!this.destroyRef.destroyed) this.lockChanged.emit(this.locked); });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['initialTicketId'] && this.initialTicketId && !this.locked) this.loadTicket(this.initialTicketId);
  }

  get locked(): boolean { return this.pendingJson !== null || this.submitting || this.storageBlocked; }
  get hasPending(): boolean { return this.pendingJson !== null; }
  get busy(): boolean { return this.locked || this.loading; }

  addBarcode(value = this.barcodeInput): void {
    if (this.busy) return;
    const barcode = value.trim();
    if (!barcode) return;
    if (this.drafts.some(draft => draft.loan.barcode === barcode)) {
      this.errorMessage = 'Barcode đã có trong lượt kiểm tra.';
      return;
    }
    if (this.drafts.length >= 3) { this.errorMessage = 'Một lượt kiểm tra tối đa 3 tài liệu.'; return; }
    this.loading = true;
    this.errorMessage = '';
    this.service.getActiveLoanItemByBarcode(barcode).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: item => {
        this.loading = false;
        if (item.barcode !== barcode) this.errorMessage = 'Thông tin tài liệu không khớp barcode đã quét.';
        else this.addItem(item);
        this.barcodeInput = '';
        this.cdr.detectChanges();
      },
      error: error => this.lookupFailed(error, 'Không tìm thấy lượt mượn đang hoạt động của barcode này.'),
    });
  }

  lookup(): void {
    if (this.busy) return;
    const query = this.lookupInput.trim();
    this.errorMessage = '';
    if (this.lookupMode === 'ticket') {
      const match = query.match(/^(?:#|HUIT-PM-)?(\d+)$/i);
      const id = match ? Number(match[1]) : 0;
      if (!Number.isSafeInteger(id) || id <= 0) { this.errorMessage = 'Nhập số phiếu hoặc mã HUIT-PM-123 hợp lệ.'; return; }
      this.loadTicket(id);
      return;
    }
    if (!query) { this.errorMessage = 'Nhập MSSV hoặc họ tên người mượn.'; return; }
    this.loading = true;
    this.selectedTicket = null;
    this.matchingLoans = [];
    this.service.getStaffLoans().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: loans => {
        this.loading = false;
        const search = query.toLocaleLowerCase('vi');
        this.matchingLoans = loans.filter(loan => ['BORROWING', 'OVERDUE'].includes(loan.status)
          && [loan.mssv, loan.readerName].some(value => value?.toLocaleLowerCase('vi').includes(search)));
        if (!this.matchingLoans.length) this.errorMessage = 'Không tìm thấy phiếu đang mượn của bạn đọc này.';
        this.cdr.detectChanges();
      },
      error: error => this.lookupFailed(error, 'Không thể tra cứu người mượn.'),
    });
  }

  loadTicket(ticketId: number): void {
    if (this.busy) return;
    this.loading = true;
    this.errorMessage = '';
    this.selectedTicket = null;
    this.service.getLoan(ticketId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ticket => {
        this.loading = false;
        this.selectedTicket = ticket;
        if (!ticket.items.some(isActiveReturnItem)) this.errorMessage = 'Phiếu không còn tài liệu đang mượn cần kiểm tra trả. Các sự cố đã ghi nhận tiếp tục xử lý tại hồ sơ mất/hỏng.';
        this.cdr.detectChanges();
      },
      error: error => this.lookupFailed(error, 'Không thể tải phiếu mượn.'),
    });
  }

  addItem(item: LoanItem, lost = false): void {
    if (this.busy) return;
    this.errorMessage = '';
    if (!isActiveReturnItem(item)) { this.errorMessage = 'Tài liệu đã trả/đã có hồ sơ sự cố hoặc không ở trạng thái đang mượn.'; return; }
    if (this.drafts.length >= 3) { this.errorMessage = 'Một lượt kiểm tra tối đa 3 tài liệu.'; return; }
    if (this.drafts.some(draft => draft.loan.detailId === item.detailId || draft.loan.barcode === item.barcode)) {
      this.errorMessage = 'Tài liệu đã có trong lượt kiểm tra.';
      return;
    }
    const reader = this.selectedTicket?.ticketId === item.ticketId
      ? `${this.selectedTicket.readerName || this.selectedTicket.mssv || 'Bạn đọc'} · Phiếu #${item.ticketId}`
      : `Phiếu #${item.ticketId}`;
    const draft = createInspectionDraft(item, reader);
    if (lost) { draft.condition = 'LOST'; normalizeSettlement(draft); }
    this.drafts = [...this.drafts, draft];
    this.receipt = null;
  }

  changeCondition(draft: InspectionDraft): void {
    if (this.locked) return;
    normalizeSettlement(draft);
    this.errorMessage = '';
  }

  removeItem(id: number): void {
    if (!this.busy) this.drafts = this.drafts.filter(draft => draft.loan.detailId !== id);
  }

  cancelDraft(): void {
    if (this.busy) return;
    this.drafts = [];
    this.selectedTicket = null;
    this.matchingLoans = [];
    this.lookupInput = '';
    this.barcodeInput = '';
    this.errorMessage = '';
  }

  submitInspection(): void {
    if (this.submitting || this.loading || this.storageBlocked) return;
    this.errorMessage = '';
    if (!this.pendingJson) {
      if (!this.counterOpen) { this.errorMessage = 'Quầy hiện đóng cửa, chưa thể ghi nhận lượt mới.'; return; }
      if (!this.drafts.length) { this.errorMessage = 'Chọn ít nhất một tài liệu để kiểm tra.'; return; }
      for (const draft of this.drafts) {
        const error = draftError(draft);
        if (error) { this.errorMessage = `${draft.loan.bookTitle}: ${error}`; return; }
      }
      const payload: ReturnInspectionRequest = { idempotencyKey: crypto.randomUUID(), items: this.drafts.map(inspectionPayload) };
      this.pendingJson = JSON.stringify(payload);
      this.persistPending();
    }
    this.submitting = true;
    this.editableFailure = false;
    this.lockChanged.emit(true);
    // Retry uses this frozen payload, never a rebuilt form with a new key.
    this.service.inspectReturn(JSON.parse(this.pendingJson)).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: receipt => {
        this.submitting = false;
        if (!this.validReceipt(receipt)) {
          this.errorMessage = 'Phản hồi chưa đủ thông tin biên nhận. Hãy thử lại lượt này để đối chiếu kết quả.';
          this.cdr.detectChanges();
          return;
        }
        this.receiptTitles = Object.fromEntries(this.drafts.map(draft => [draft.loan.detailId, draft.loan.bookTitle]));
        this.receipt = receipt;
        this.pendingJson = null;
        this.clearStoredPending();
        this.drafts = [];
        this.selectedTicket = null;
        this.matchingLoans = [];
        this.lookupInput = '';
        this.barcodeInput = '';
        this.lockChanged.emit(false);
        this.cdr.detectChanges();
      },
      error: error => {
        this.submitting = false;
        this.editableFailure = error?.status >= 400 && error.status < 500 && ![408, 429].includes(error.status);
        this.errorMessage = this.errorText(error, 'Chưa nhận được kết quả. Hãy thử lại lượt này trước khi đổi tài liệu hoặc tình trạng.');
        this.cdr.detectChanges();
      },
    });
  }

  editRejectedRequest(): void {
    if (!this.editableFailure || this.submitting) return;
    this.pendingJson = null;
    this.clearStoredPending();
    this.editableFailure = false;
    this.errorMessage = '';
    this.lockChanged.emit(false);
  }

  total(field: 'lateFineAdded' | 'penaltyAmount' | 'depositApplied' | 'debtAdded'): number {
    return this.receipt?.items.reduce((sum, item) => sum + Number(item[field] || 0), 0) || 0;
  }

  statusLabel(status: string | null): string {
    return ({ AVAILABLE: 'Sẵn sàng trong kho', RESERVED: 'Giữ cho bạn đọc tiếp theo', RETURNED: 'Đã trả',
      MAINTENANCE: 'Chờ sửa chữa', LOST: 'Ghi nhận mất', DAMAGED: 'Hư hỏng', SETTLED: 'Đã giải quyết',
      BORROWING: 'Đang mượn', OVERDUE: 'Quá hạn', OUTSTANDING: 'Còn nợ', PAID: 'Đã thanh toán',
      WAITING_REPLACEMENT: 'Chờ nộp sách thay thế', PENDING_REVIEW: 'Chờ xét miễn giảm',
      PENDING: 'Chưa giao sách', CANCELLED: 'Đã hủy' } as Record<string, string>)[status || ''] || status || '—';
  }

  readerTickets(userId: string): string {
    return [...new Set(this.receipt?.items.filter(item => item.userId === userId).map(item => item.ticketId))]
      .map(id => `#${id}`).join(', ');
  }

  private validReceipt(value: unknown): value is ReturnInspectionReceipt {
    if (!value || typeof value !== 'object' || !this.pendingJson) return false;
    const result = value as ReturnInspectionReceipt;
    const request: ReturnInspectionRequest = JSON.parse(this.pendingJson);
    return typeof result.operationId === 'string' && !!result.operationId && result.operationType === 'RETURN_INSPECT'
      && typeof result.processedAt === 'string' && typeof result.replayed === 'boolean'
      && Array.isArray(result.items) && result.items.length === request.items.length
      && result.items.every(item => item && Number.isSafeInteger(item.ticketId) && item.ticketId > 0)
      && new Set(result.items.map(item => item.loanDetailId)).size === request.items.length
      && result.items.every(item => request.items.some(sent => sent.loanDetailId === item.loanDetailId && sent.condition === item.condition)
        && typeof item.userId === 'string' && typeof item.barcode === 'string'
        && typeof item.detailStatus === 'string' && typeof item.copyStatus === 'string'
        && [item.lateFineAdded, item.lateFineTotal, item.penaltyAmount, item.depositApplied, item.debtAdded, item.penaltyOutstanding].every(Number.isFinite))
      && Array.isArray(result.tickets) && result.tickets.length > 0
      && result.tickets.every(ticket => ticket && Number.isSafeInteger(ticket.ticketId) && typeof ticket.status === 'string')
      && result.items.every(item => result.tickets.some(ticket => ticket.ticketId === item.ticketId))
      && Array.isArray(result.balances) && result.balances.length > 0
      && result.balances.every(balance => balance && typeof balance.userId === 'string'
        && Number.isFinite(balance.depositBalance) && Number.isFinite(balance.totalDebt))
      && result.items.every(item => result.balances.some(balance => balance.userId === item.userId));
  }

  private lookupFailed(error: unknown, fallback: string): void {
    this.loading = false;
    this.errorMessage = this.errorText(error, fallback);
    this.cdr.detectChanges();
  }

  private errorText(error: any, fallback: string): string {
    const message = error?.error?.detail ?? error?.error?.message ?? error?.error;
    return typeof message === 'string' && message.trim() ? message : fallback;
  }

  private ownerStorageKey(): string | null {
    try {
      const profile = JSON.parse(localStorage.getItem('huit_lib_profile') || 'null');
      const owner = profile?.userId || profile?.mssv || profile?.email;
      return typeof owner === 'string' && owner ? `huit_lib_pending_inspection:${encodeURIComponent(owner)}` : null;
    } catch { return null; }
  }

  private persistPending(): void {
    if (!this.storageKey) return;
    try { sessionStorage.setItem(this.storageKey, JSON.stringify({ version: 1, request: JSON.parse(this.pendingJson!), drafts: this.drafts })); }
    catch { this.storageMessage = 'Trình duyệt không lưu được lượt đang gửi. Giữ trang này để thử lại nếu chưa nhận được kết quả.'; }
  }

  private restorePending(): void {
    if (!this.storageKey) return;
    try {
      const stored = sessionStorage.getItem(this.storageKey);
      if (!stored) return;
      const state = JSON.parse(stored);
      if (state.version !== 1 || !state.request?.idempotencyKey || !Array.isArray(state.request.items)
          || !state.request.items.length || state.request.items.length > 3 || !Array.isArray(state.drafts)
          || state.drafts.length !== state.request.items.length
          || state.drafts.some((draft: InspectionDraft, index: number) => !Number.isSafeInteger(draft?.loan?.detailId)
            || draft.loan.detailId !== state.request.items[index]?.loanDetailId
            || typeof draft.loan.bookTitle !== 'string' || typeof draft.loan.barcode !== 'string'
            || typeof draft.note !== 'string' || typeof draft.readerLabel !== 'string'
            || !['NORMAL', 'DAMAGED', 'LOST'].includes(draft.condition)
            || draft.condition !== state.request.items[index]?.condition
            || !['', 'YES', 'NO'].includes(draft.market)
            || typeof draft.penalty?.description !== 'string')) throw new Error('Invalid pending return');
      this.pendingJson = JSON.stringify(state.request);
      this.drafts = state.drafts;
      this.errorMessage = 'Có lượt kiểm tra chưa nhận được kết quả. Hãy thử lại để lấy biên nhận trước khi lập lượt mới.';
    } catch {
      this.storageBlocked = true;
      this.errorMessage = 'Không đọc được lượt trả sách đã lưu. Hãy đối chiếu phiếu và hồ sơ mất/hỏng trước khi tiếp tục trên phiên này.';
    }
  }

  private clearStoredPending(): void {
    if (!this.storageKey) return;
    try { sessionStorage.removeItem(this.storageKey); this.storageMessage = ''; }
    catch { this.storageMessage = 'Không xóa được bản lưu cục bộ; nếu mở lại thấy lượt cũ, thử lại sẽ lấy biên nhận đã ghi nhận.'; }
  }
}
