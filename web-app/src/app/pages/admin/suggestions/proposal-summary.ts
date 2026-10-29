import { ChangeDetectorRef, Component, DestroyRef, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ProposalSummaryService, ProposalSummaryFilters, ProposalSummaryReport } from '../../../services/proposal-summary.service';
import { AcademicFaculty } from '../../../services/student-obligation-report.models';
import { ReportFileSaver, xlsxFilename } from '../../../services/report-file-saver';

@Component({ selector: 'app-proposal-summary', standalone: true, imports: [CommonModule, FormsModule],
  templateUrl: './proposal-summary.html', styleUrl: './proposal-summary.css' })
export class ProposalSummary implements OnInit {
  @Output() openProposal = new EventEmitter<number>();
  private readonly destroyRef = inject(DestroyRef);
  filters: ProposalSummaryFilters = { facultyId: '', from: '', to: '', status: 'ALL' };
  pageSize = 25;
  faculties: AcademicFaculty[] = [];
  report: ProposalSummaryReport | null = null;
  loading = false; exporting = false; facultiesLoading = false;
  error = ''; facultyError = ''; exportError = ''; exportMessage = '';
  constructor(private service: ProposalSummaryService, private saver: ReportFileSaver, private cdr: ChangeDetectorRef) {}
  ngOnInit(): void { this.loadFaculties(); this.applyFilters(); }
  get busy(): boolean { return this.loading || this.exporting; }
  get dirty(): boolean {
    const r = this.report;
    return !!r && (this.filters.facultyId !== (r.filter.facultyId || '') || this.filters.from !== (r.filter.from || '')
      || this.filters.to !== (r.filter.to || '') || this.filters.status !== r.filter.status || Number(this.pageSize) !== r.size);
  }
  get totalPages(): number { return this.report ? Math.max(1, Math.ceil(this.report.totalGroups / this.report.size)) : 1; }
  loadFaculties(): void {
    if (this.facultiesLoading) return;
    this.facultiesLoading = true; this.facultyError = '';
    this.service.faculties().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: data => { this.faculties = data; this.facultiesLoading = false; this.refresh(); },
      error: () => { this.facultyError = 'Không tải được danh mục khoa. Thử lại để chọn khoa.'; this.facultiesLoading = false; this.refresh(); },
    });
  }
  applyFilters(): void {
    if (this.busy) return;
    if (this.filters.from && this.filters.to && this.filters.from > this.filters.to) {
      this.error = 'Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.'; return;
    }
    this.load({ ...this.filters }, 0, Number(this.pageSize));
  }
  resetFilters(): void {
    if (this.busy) return;
    this.filters = { facultyId: '', from: '', to: '', status: 'ALL' }; this.pageSize = 25; this.applyFilters();
  }
  changePage(page: number): void {
    if (this.busy || this.dirty || !this.report || page < 0 || page >= this.totalPages || page === this.report.page) return;
    this.load(this.shownFilters(), page, this.report.size);
  }
  private load(filters: ProposalSummaryFilters, page: number, size: number): void {
    this.loading = true; this.report = null; this.error = ''; this.exportError = ''; this.exportMessage = '';
    this.service.preview(filters, page, size).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: report => { this.report = report; this.loading = false; this.refresh(); },
      error: async e => { this.error = await this.errorText(e, 'Không tải được bảng tổng hợp. Hãy thử áp dụng lại bộ lọc.'); this.loading = false; this.refresh(); },
    });
  }
  exportReport(): void {
    if (this.busy || this.dirty || !this.report) return;
    const snapshot = this.report;
    this.exporting = true; this.exportError = ''; this.exportMessage = '';
    this.service.export(this.shownFilters()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => {
        try {
          const type = (response.headers.get('Content-Type') || response.body?.type || '').toLowerCase();
          if (!response.body?.size || (!type.includes('spreadsheetml.sheet') && !type.includes('application/octet-stream'))) throw new Error('Phản hồi không phải file Excel hợp lệ.');
          const filename = xlsxFilename(response.headers.get('Content-Disposition'), `de-xuat-mua-${snapshot.snapshotAt.replace(/\D/g, '').slice(0, 14)}.xlsx`);
          this.saver.save(response.body, filename);
          this.exportMessage = `Đã yêu cầu tải ${filename}. File gồm toàn bộ kết quả bộ lọc tại thời điểm xuất; bạn tự gửi file cho khoa.`;
        } catch (e) { this.exportError = e instanceof Error ? e.message : 'Trình duyệt chưa tải được file.'; }
        this.exporting = false; this.refresh();
      },
      error: async e => { this.exportError = await this.errorText(e, 'Không tải được Excel. Hãy thử lại.'); this.exporting = false; this.refresh(); },
    });
  }
  statusLabel(status: string): string { return ({ ALL: 'Mọi trạng thái', PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Từ chối' } as Record<string,string>)[status] || status; }
  identityLabel(type: string): string { return ({ ISBN: 'ISBN chuẩn', METADATA: 'Tên/tác giả/ấn bản/năm/NXB', INVALID: 'ISBN cần kiểm tra', INCOMPLETE: 'Thông tin chưa đủ để gộp' } as Record<string,string>)[type] || type; }
  facultyLabel(id: string | null): string { return !id ? 'Mọi khoa' : this.faculties.find(f => f.facultyId === id)?.facultyName || id; }
  private shownFilters(): ProposalSummaryFilters {
    const f = this.report!.filter; return { facultyId: f.facultyId || '', from: f.from || '', to: f.to || '', status: f.status };
  }
  private async errorText(e: any, fallback: string): Promise<string> {
    if (e?.status === 401 || e?.status === 403) return 'Bạn không có quyền xem hoặc xuất báo cáo này.';
    let body = e?.error;
    if (body instanceof Blob) { try { body = JSON.parse(await body.text()); } catch { return fallback; } }
    const text = typeof body === 'string' ? body : body?.detail || body?.message;
    return typeof text === 'string' && text.trim() ? text : fallback;
  }
  private refresh(): void { if (!this.destroyRef.destroyed) this.cdr.detectChanges(); }
}
