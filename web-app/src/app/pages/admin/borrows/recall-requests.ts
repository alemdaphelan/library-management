import { CommonModule } from '@angular/common';
import {
  ChangeDetectorRef,
  Component,
  DestroyRef,
  EventEmitter,
  OnInit,
  Output,
  inject,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { take, timeout } from 'rxjs';
import {
  CirculationService,
  LoanItem,
  RecallFilter,
  RecallItem,
} from '../../../services/circulation.service';

@Component({
  selector: 'app-recall-requests',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './recall-requests.html',
  styleUrl: './recall-requests.css',
})
export class RecallRequests implements OnInit {
  @Output() recalled = new EventEmitter<void>();
  @Output() busyChange = new EventEmitter<boolean>();
  items: RecallItem[] = [];
  total = 0;
  page = 0;
  readonly size = 10;
  q = '';
  state: RecallFilter = 'ALL';
  loading = false;
  saving = false;
  error = '';
  formError = '';
  selected: RecallItem | null = null;
  reason = '';
  uncertain = false;
  result: LoanItem | null = null;
  private version = 0;
  private readonly destroyRef = inject(DestroyRef);
  constructor(
    private api: CirculationService,
    private cdr: ChangeDetectorRef,
  ) {}
  ngOnInit(): void {
    this.load();
  }
  get pages(): number {
    return Math.max(1, Math.ceil(this.total / this.size));
  }
  applyFilters(): void {
    if (this.saving) return;
    this.page = 0;
    this.load();
  }
  changePage(page: number): void {
    if (this.saving || this.loading) return;
    this.page = Math.min(this.pages - 1, Math.max(0, page));
    this.load();
  }
  load(): void {
    if (this.saving) return;
    const version = ++this.version;
    this.loading = true;
    this.error = '';
    this.api
      .getRecallItems(this.q.trim(), this.state, this.page, this.size)
      .pipe(timeout(15000), take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          if (version !== this.version) return;
          if (
            !data ||
            !Array.isArray(data.items) ||
            !Number.isSafeInteger(data.total) ||
            data.total < 0
          ) {
            this.loading = false;
            this.error = 'Dữ liệu danh sách chưa hợp lệ. Vui lòng thử lại.';
          } else {
            this.items = data.items;
            this.total = data.total;
            this.loading = false;
            if (this.page >= this.pages) {
              this.page = this.pages - 1;
              this.load();
              return;
            }
          }
          this.cdr.markForCheck();
        },
        error: () => {
          if (version !== this.version) return;
          this.loading = false;
          this.error = 'Không tải được sách đang mượn. Vui lòng thử lại.';
          this.cdr.markForCheck();
        },
      });
  }
  canRequest(item: RecallItem): boolean {
    return !!item.userId && !!item.dueDate && !item.isRecalled && !item.lossReportedAt;
  }
  open(item: RecallItem): void {
    if (this.saving || !this.canRequest(item)) return;
    this.selected = item;
    this.reason = '';
    this.formError = '';
    this.uncertain = false;
    this.result = null;
  }
  close(): void {
    if (!this.saving) {
      this.selected = null;
      this.uncertain = false;
      this.formError = '';
    }
  }
  submit(): void {
    const item = this.selected,
      reason = this.reason.trim();
    if (!item || this.saving) return;
    if (!reason || reason.length > 1000) {
      this.formError = 'Nhập lý do yêu cầu trả, tối đa 1000 ký tự.';
      return;
    }
    this.saving = true;
    this.busyChange.emit(true);
    this.formError = '';
    this.api
      .requestRecall(item.ticketId, item.detailId, reason)
      .pipe(timeout(30000), take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.saving = false;
          this.busyChange.emit(false);
          if (
            !result ||
            result.ticketId !== item.ticketId ||
            result.detailId !== item.detailId ||
            result.isRecalled !== true ||
            result.recallReason !== reason ||
            !result.recallRequestedAt ||
            !result.recallDueDate ||
            !result.recallPreviousDueDate ||
            [result.recallRequestedAt, result.recallDueDate, result.recallPreviousDueDate].some(
              (value) => Number.isNaN(Date.parse(value)),
            )
          ) {
            this.uncertain = true;
            this.formError =
              'Chưa xác nhận được kết quả. Kiểm tra danh sách hoặc gửi lại cùng yêu cầu.';
          } else {
            this.result = result;
            this.selected = null;
            this.uncertain = false;
            this.load();
            this.recalled.emit();
          }
          this.cdr.markForCheck();
        },
        error: (error) => {
          this.saving = false;
          this.busyChange.emit(false);
          this.uncertain = !error.status || error.status >= 500;
          const reason = error?.error?.detail ?? error?.error?.message;
          this.formError =
            typeof reason === 'string'
              ? reason
              : this.uncertain
                ? 'Chưa xác nhận được kết quả. Giữ nguyên lý do và kiểm tra danh sách hoặc gửi lại cùng yêu cầu.'
                : 'Không gửi được yêu cầu. Tải lại danh sách để kiểm tra trạng thái hiện tại.';
          this.cdr.markForCheck();
        },
      });
  }
}
