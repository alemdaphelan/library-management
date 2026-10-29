import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { ReportFileSaver, obligationFilename } from '../../../services/report-file-saver';
import { AcademicFaculty, ClassificationCoverage, ObligationFilters, StudentObligation, StudentObligationReport as Report } from '../../../services/student-obligation-report.models';
import { StudentObligationReportService } from '../../../services/student-obligation-report.service';

@Component({
  selector: 'app-student-obligation-report', standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './student-obligation-report.html', styleUrl: './student-obligation-report.css',
})
export class StudentObligationReport implements OnInit {
  @Input() initialQuery='';
  @Input() initialMode: 'EITHER'|'OVERDUE'='EITHER';
  @Output() ticketRequested = new EventEmitter<number>();
  private readonly destroyRef = inject(DestroyRef);
  filters: ObligationFilters = { mode: 'EITHER', facultyId: '', q: '' };
  pageSize = 25;
  faculties: AcademicFaculty[] = [];
  coverage: ClassificationCoverage | null = null;
  report: Report | null = null;
  selectedUserId: string | null = null;
  loading = false; exporting = false; facultiesLoading = false; coverageLoading = false;
  errorMessage = ''; exportError = ''; exportMessage = ''; facultyError = ''; coverageError = '';
  private previewSubscription?: Subscription;
  private lastRequest = { filters: { ...this.filters }, page: 0, size: this.pageSize };

  constructor(private service: StudentObligationReportService, private saver: ReportFileSaver, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void { this.filters={...this.filters,q:this.initialQuery.slice(0,100),mode:this.initialMode};this.loadFaculties(); this.loadCoverage(); this.applyFilters(); }

  get busy(): boolean { return this.loading || this.exporting; }
  get totalPages(): number { return this.report ? Math.max(1, Math.ceil(this.report.totalStudents / this.report.size)) : 1; }
  get selectedStudent(): StudentObligation | null { return this.report?.students.find(item => item.userId === this.selectedUserId) || null; }
  get selectedOverdue() { return this.report?.overdueItems.filter(item => item.userId === this.selectedUserId) || []; }
  get selectedLedger() { return this.report?.ledgerEntries.filter(item => item.userId === this.selectedUserId) || []; }
  get dirty(): boolean {
    const shown = this.report;
    return !!shown && (this.filters.mode !== shown.filter.mode || this.filters.facultyId !== (shown.filter.facultyId || '')
      || this.filters.q.trim() !== shown.filter.query || this.pageSize !== shown.size);
  }
  get showUnclassified(): boolean {
    return !!this.coverage?.unclassifiedStudents || this.filters.facultyId === 'UNCLASSIFIED'
      || !!this.report?.students.some(student => student.mappingStatus !== 'MAPPED');
  }

  loadFaculties(): void {
    if (this.facultiesLoading) return;
    this.facultiesLoading = true; this.facultyError = '';
    this.service.faculties().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: faculties => { this.faculties = faculties.filter(item => item.facultyId !== 'UNCLASSIFIED'); this.facultiesLoading = false; this.cdr.detectChanges(); },
      error: async error => {
        this.facultyError = await this.errorText(error, 'Không tải được danh mục khoa. Có thể xem mọi khoa và thử tải lại danh mục.');
        this.facultiesLoading = false; this.refreshView();
      },
    });
  }
  loadCoverage(): void {
    if (this.coverageLoading) return;
    this.coverageLoading = true; this.coverageError = ''; this.coverage = null;
    this.service.coverage().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: coverage => { this.coverage = coverage; this.coverageLoading = false; this.cdr.detectChanges(); },
      error: async error => {
        this.coverageError = await this.errorText(error, 'Chưa kiểm tra được việc phân loại sinh viên.');
        this.coverageLoading = false; this.refreshView();
      },
    });
  }
  applyFilters(): void {
    if (this.busy) return;
    if (this.filters.q.trim().length > 100) { this.errorMessage = 'Từ khóa tối đa 100 ký tự.'; return; }
    this.filters = { ...this.filters, q: this.filters.q.trim() };
    this.loadReport({ ...this.filters }, 0, Number(this.pageSize));
  }
  resetFilters(): void {
    if (this.busy) return;
    this.filters = { mode: 'EITHER', facultyId: '', q: '' }; this.pageSize = 25;
    this.applyFilters();
  }
  retryPreview(): void {
    if (!this.busy) this.loadReport({ ...this.lastRequest.filters }, this.lastRequest.page, this.lastRequest.size);
  }
  changePage(page: number): void {
    if (this.busy || this.dirty || !this.report || page < 0 || page >= this.totalPages || page === this.report.page) return;
    this.loadReport(this.shownFilters(), page, this.report.size);
  }
  selectStudent(student: StudentObligation): void { this.selectedUserId = this.selectedUserId === student.userId ? null : student.userId; }
  hasDiscrepancy(student: StudentObligation): boolean {
    return (student.walletDifference !== null && student.walletDifference !== 0)
      || student.reconciliationDifference !== 0 || student.paidOrDepositAmount < 0;
  }
  modeLabel(mode: string): string { return ({ EITHER: 'Quá hạn hoặc còn nợ', OVERDUE: 'Còn tài liệu quá hạn', DEBT: 'Còn công nợ' } as Record<string, string>)[mode] || mode; }
  facultyLabel(id: string | null): string { return !id ? 'Mọi khoa' : id === 'UNCLASSIFIED' ? 'Cần bổ sung phân loại' : this.faculties.find(item => item.facultyId === id)?.facultyName || id; }
  feeLabel(type: string): string { return ({ FINE: 'Phí trễ', REPAIR_FEE: 'Sửa chữa', COMPENSATION: 'Đền bù', TECHNICAL_FEE: 'Xử lý kỹ thuật', PAYMENT: 'Thanh toán', ADJUSTMENT: 'Điều chỉnh giảm', WAIVER_ADJUSTMENT: 'Miễn giảm' } as Record<string, string>)[type] || type; }

  exportReport(): void {
    if (this.busy || this.dirty || !this.report || !this.report.totalStudents) return;
    const snapshot = this.report;
    const filters = this.shownFilters();
    this.exporting = true; this.exportError = ''; this.exportMessage = '';
    this.service.export(filters).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => {
        try {
          const type = (response.headers.get('Content-Type') || response.body?.type || '').toLowerCase();
          if (!response.body?.size || (!type.includes('spreadsheetml.sheet') && !type.includes('application/octet-stream'))) {
            throw new Error('Phản hồi không phải file Excel hợp lệ. Hãy thử tải lại.');
          }
          const filename = obligationFilename(response.headers.get('Content-Disposition'), filters.mode, snapshot.snapshotAt);
          this.saver.save(response.body, filename);
          this.exportMessage = `Đã yêu cầu tải ${filename}. File gồm toàn bộ kết quả bộ lọc tại thời điểm xuất.`;
        } catch (error) { this.exportError = error instanceof Error ? error.message : 'Trình duyệt chưa tải được file. Hãy thử lại.'; }
        this.exporting = false; this.cdr.detectChanges();
      },
      error: async error => {
        this.exportError = await this.errorText(error, 'Không tải được Excel. Hãy thử lại.');
        this.exporting = false; this.refreshView();
      },
    });
  }

  private loadReport(filters: ObligationFilters, page: number, size: number): void {
    this.previewSubscription?.unsubscribe();
    this.lastRequest = { filters: { ...filters }, page, size };
    this.loading = true; this.report = null; this.selectedUserId = null;
    this.errorMessage = ''; this.exportError = ''; this.exportMessage = '';
    this.previewSubscription = this.service.preview(filters, page, size).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: report => {
        if (!this.validReport(report)) { this.errorMessage = 'Phản hồi báo cáo thiếu dữ liệu. Hãy thử tải lại.'; }
        else this.report = report;
        this.loading = false; this.cdr.detectChanges();
      },
      error: async error => {
        this.errorMessage = await this.errorText(error, 'Không tải được báo cáo. Hãy thử lại.');
        this.loading = false; this.refreshView();
      },
    });
  }
  private shownFilters(): ObligationFilters {
    return { mode: this.report!.filter.mode, facultyId: this.report!.filter.facultyId || '', q: this.report!.filter.query };
  }
  private validReport(value: Report): boolean {
    return !!value && typeof value.snapshotAt === 'string' && value.currency === 'VND' && !!value.timezone && !!value.filter
      && ['OVERDUE', 'DEBT', 'EITHER'].includes(value.filter.mode) && typeof value.filter.query === 'string'
      && Number.isSafeInteger(value.totalStudents) && value.totalStudents >= 0
      && Number.isSafeInteger(value.page) && value.page >= 0 && Number.isSafeInteger(value.size) && value.size > 0
      && Array.isArray(value.students) && Array.isArray(value.overdueItems) && Array.isArray(value.ledgerEntries)
      && value.students.every(student => student && typeof student.userId === 'string'
        && [student.chargedAmount, student.paidOrDepositAmount, student.waivedAmount, student.adjustedAmount,
          student.outstandingAmount, student.reconciliationDifference, student.overdueItemCount].every(Number.isFinite));
  }
  private async errorText(error: any, fallback: string): Promise<string> {
    if (error?.status === 401 || error?.status === 403) return 'Phiên đăng nhập hết hạn hoặc tài khoản không có quyền xem/tải báo cáo. Hãy đăng nhập bằng tài khoản thủ thư hoặc Admin.';
    if (error?.status === 0) return 'Không kết nối được máy chủ. Kiểm tra kết nối rồi thử lại.';
    let body = error?.error;
    if (body instanceof Blob) { try { body = JSON.parse(await body.text()); } catch { return fallback; } }
    const message = body?.detail ?? body?.message ?? body;
    return typeof message === 'string' && message.trim() ? message : fallback;
  }
  private refreshView(): void { if (!this.destroyRef.destroyed) this.cdr.detectChanges(); }
}
