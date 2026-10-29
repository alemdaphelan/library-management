import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, Input, OnChanges, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DisposalService, DisposalCandidate, DisposalCreate, DisposalDetail, DisposalFields, DisposalFilters, DisposalList } from '../../../services/disposal.service';
import { InventoryAudit, InventoryAuditService } from '../../../services/inventory-audit.service';
import { AuthService } from '../../../services/auth.service';

interface DraftRow extends DisposalFields { candidate: DisposalCandidate; selected: boolean; }
@Component({ selector: 'app-disposal-workspace', standalone: true, imports: [CommonModule, FormsModule],
  templateUrl: './disposal-workspace.html', styleUrl: './disposal-workspace.css' })
export class DisposalWorkspace implements OnInit, OnChanges {
  @Input() auditId?: number;
  private readonly destroyRef = inject(DestroyRef);
  private initialized = false;
  audits: InventoryAudit[] = [];
  sourceAuditId = 0;
  loadedAuditId = 0;
  selectedDetailId = '';
  rows: DraftRow[] = [];
  loadingSources = false; loadingAudits = false; loadingList = false; loadingDetail = false; saving = false;
  sourceError = ''; auditError = ''; listError = ''; detailError = ''; formError = ''; message = '';
  pending: DisposalCreate | null = null;
  detailUncertain = false;
  list: DisposalList | null = null;
  filters: DisposalFilters = { status: '', auditId: '', barcode: '' };
  private applied: DisposalFilters = { ...this.filters };
  detail: DisposalDetail | null = null;
  editFields: DisposalFields = this.emptyFields();
  readonly actions = [{ code: 'REPAIR', label: 'Sửa chữa' }, { code: 'TRANSFER', label: 'Chuyển vị trí/đơn vị' }, { code: 'LIQUIDATE', label: 'Thanh lý' }, { code: 'DESTROY', label: 'Hủy' }];
  readonly reasons = [{ code: 'WORN', label: 'Cũ/xuống cấp' }, { code: 'OBSOLETE', label: 'Lỗi thời' }, { code: 'UNUSED', label: 'Không còn dùng' }, { code: 'DAMAGED', label: 'Hỏng' }];
  constructor(private service: DisposalService, private auditService: InventoryAuditService, private auth: AuthService, private cdr: ChangeDetectorRef) {}
  ngOnInit(): void {
    this.initialized = true; this.restore(); this.loadAudits(); this.applyFilters();
    if (this.auditId && !this.pending) { this.sourceAuditId = this.auditId; this.loadSources(); }
  }
  ngOnChanges(): void {
    if (this.initialized && this.auditId && !this.pending && !this.saving) { this.sourceAuditId = this.auditId; this.loadSources(); }
  }
  get busy(): boolean { return this.saving || this.loadingSources || this.loadingDetail; }
  get selectedCount(): number { return this.rows.filter(r => r.selected).length; }
  get editDirty(): boolean { return !!this.detail && JSON.stringify(this.fields(this.editFields)) !== JSON.stringify(this.fields(this.detail.saved)); }
  get totalPages(): number { return this.list ? Math.max(1, Math.ceil(this.list.total / this.list.size)) : 1; }
  get listDirty(): boolean { return JSON.stringify({ ...this.filters, barcode: this.filters.barcode.trim() }) !== JSON.stringify(this.applied); }
  loadAudits(): void {
    if (this.loadingAudits) return; this.loadingAudits = true; this.auditError = '';
    this.auditService.getAudits().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: data => { this.audits = data; this.loadingAudits = false; this.refresh(); },
      error: e => { this.auditError = this.error(e, 'Không tải được phiên kiểm kê.'); this.loadingAudits = false; this.refresh(); },
    });
  }
  loadSources(): void {
    if (this.busy || this.pending || !Number.isSafeInteger(Number(this.sourceAuditId)) || Number(this.sourceAuditId) <= 0) return;
    const auditId = Number(this.sourceAuditId);
    this.loadingSources = true; this.sourceError = ''; this.formError = ''; this.rows = []; this.loadedAuditId = 0;
    this.service.candidates(auditId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: data => { this.loadedAuditId = auditId; this.rows = data.map(candidate => ({ candidate, selected: false, ...this.emptyFields() })); this.loadingSources = false; this.refresh(); },
      error: e => { this.sourceError = this.error(e, 'Không tải được dữ liệu nguồn.'); this.loadingSources = false; this.refresh(); },
    });
  }
  cancelForm(): void { if (this.busy || this.pending) return; this.rows = []; this.formError = ''; }
  saveDrafts(): void {
    if (this.busy || this.pending) return;
    if (Number(this.sourceAuditId) !== this.loadedAuditId) { this.formError = 'Tải nguồn của phiên đang chọn trước khi lập hồ sơ.'; return; }
    const selected = this.rows.filter(r => r.selected);
    if (!selected.length || selected.length > 50) { this.formError = 'Chọn từ 1 đến 50 bản sao.'; return; }
    for (const row of selected) {
      const error = this.validate(row);
      if (!row.candidate.eligible || error) { this.formError = `${row.candidate.source.barcode}: ${error || row.candidate.blockers.join('; ')}`; return; }
    }
    this.pending = { requestId: crypto.randomUUID(), auditId: Number(this.sourceAuditId), lines: selected.map(r => ({ ...this.fields(r),
      barcode: r.candidate.source.barcode, sourceDetailId: r.candidate.source.detailId, expectedFingerprint: r.candidate.fingerprint })) };
    this.persist(); this.sendPending();
  }
  sendPending(): void {
    if (this.busy || !this.pending) return;
    const request = this.pending;
    this.saving = true; this.formError = ''; this.message = '';
    this.service.create(request).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: batch => {
        this.clearPending(); this.saving = false; this.rows = [];
        this.message = `Đã lập ${batch.proposals.length} hồ sơ nháp${batch.replay ? ' (đã đối chiếu yêu cầu gửi trước)' : ''}. Chưa gửi và chưa xử lý thực tế bản sao.`;
        this.applyFilters(); if (batch.proposals[0]) this.openDetail(batch.proposals[0].proposalId); this.refresh();
      },
      error: e => {
        this.saving = false;
        if (e?.status === 0 || e?.status >= 500) this.formError = 'Chưa rõ kết quả lập hồ sơ. Giữ nguyên yêu cầu; bấm gửi lại để server đối chiếu, không lập yêu cầu mới.';
        else { this.clearPending(); this.formError = this.error(e, 'Không lập được hồ sơ. Dữ liệu nguồn có thể đã đổi; tải lại và chọn lại bản sao.'); }
        this.refresh();
      },
    });
  }
  applyFilters(): void {
    if (this.loadingList || this.saving) return;
    if (this.filters.auditId && (!/^\d+$/.test(this.filters.auditId) || Number(this.filters.auditId) < 1)) { this.listError = 'Mã phiên kiểm kê phải là số dương.'; return; }
    this.applied = { ...this.filters, barcode: this.filters.barcode.trim() }; this.loadList(0);
  }
  changePage(page: number): void { if (!this.loadingList && !this.listDirty && page >= 0 && page < this.totalPages) this.loadList(page); }
  private loadList(page: number): void {
    this.loadingList = true; this.listError = ''; this.list = null;
    this.service.list({ ...this.applied }, page, 25).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: data => { this.list = data; this.loadingList = false; this.refresh(); },
      error: e => { this.listError = this.error(e, 'Không tải được danh sách hồ sơ.'); this.loadingList = false; this.refresh(); },
    });
  }
  openDetail(id: string): void {
    if (this.busy) return; this.selectedDetailId = id; this.loadingDetail = true; this.detailError = ''; this.detail = null;
    this.service.get(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: data => { this.setDetail(data); this.detailUncertain = false; this.loadingDetail = false; this.refresh(); },
      error: e => { this.detailError = this.error(e, 'Không tải được chi tiết hồ sơ.'); this.loadingDetail = false; this.refresh(); },
    });
  }
  reloadDetail(): void { if (this.selectedDetailId) this.openDetail(this.selectedDetailId); }
  saveEdit(): void {
    const d = this.detail;
    if (this.busy || !d?.canEdit || !d.current.eligible || this.detailUncertain) return;
    const error = this.validate(this.editFields); if (error) { this.detailError = error; return; }
    this.mutate('edit');
  }
  transition(submit: boolean): void {
    if (submit && this.editDirty) { this.detailError = 'Lưu thay đổi nháp trước khi gửi đề xuất.'; return; }
    if (this.busy || !this.detail || this.detailUncertain || (submit ? !this.detail.canSubmit : !this.detail.canCancel)) return;
    this.mutate(submit ? 'submit' : 'cancel');
  }
  private mutate(action: 'edit' | 'submit' | 'cancel'): void {
    const d = this.detail!; this.saving = true; this.detailError = ''; this.message = '';
    const request = action === 'edit' ? this.service.edit(d.item.proposalId, { ...this.fields(this.editFields), version: d.item.version, expectedFingerprint: d.current.fingerprint })
      : action === 'submit' ? this.service.submit(d.item.proposalId, d.item.version) : this.service.cancel(d.item.proposalId, d.item.version);
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: data => { this.saving = false; this.setDetail(data); this.message = action === 'submit' ? 'Đã gửi đề xuất; đang chờ luồng quyết định của Admin. Chưa xử lý thực tế bản sao.' : action === 'cancel' ? 'Đã hủy hồ sơ; lịch sử vẫn được giữ.' : 'Đã lưu nháp và xác nhận lại dữ liệu nguồn hiện tại.'; this.applyFilters(); this.refresh(); },
      error: e => { this.saving = false; this.detailError = this.error(e, 'Không lưu được hồ sơ. Tải lại chi tiết để đối chiếu trước khi thao tác tiếp.'); this.detailUncertain = true; this.refresh(); },
    });
  }
  statusLabel(status: string): string { return ({ DRAFT: 'Nháp', SUBMITTED: 'Đã gửi — chờ Admin', CANCELLED: 'Đã hủy hồ sơ' } as Record<string,string>)[status] || status; }
  actionLabel(code: string): string { return this.actions.find(a => a.code === code)?.label || code; }
  reasonLabel(code: string): string { return this.reasons.find(r => r.code === code)?.label || code; }
  eventLabel(code: string): string { return ({ CREATED: 'Lập nháp', UPDATED: 'Sửa nháp / xác nhận lại nguồn', SUBMITTED: 'Gửi đề xuất', CANCELLED: 'Hủy hồ sơ' } as Record<string,string>)[code] || code; }
  private setDetail(data: DisposalDetail): void { this.detail = data; this.editFields = this.fields(data.saved); }
  private fields(f: DisposalFields): DisposalFields { return { observedCondition: f.observedCondition.trim(), reasonCode: f.reasonCode, reason: f.reason.trim(), proposedAction: f.proposedAction, evidenceUrl: f.evidenceUrl?.trim() || null }; }
  private emptyFields(): DisposalFields { return { observedCondition: '', reasonCode: 'WORN', reason: '', proposedAction: 'REPAIR', evidenceUrl: '' }; }
  private validate(f: DisposalFields): string {
    if (!f.observedCondition.trim() || !f.reason.trim()) return 'Nhập tình trạng thực tế và lý do.';
    if (f.observedCondition.length > 1000 || f.reason.length > 1000 || (f.evidenceUrl?.length || 0) > 1000) return 'Mỗi nội dung tối đa 1000 ký tự.';
    if (f.evidenceUrl?.trim()) { try { const u = new URL(f.evidenceUrl.trim()); if (!['http:', 'https:'].includes(u.protocol) || !u.hostname || u.username || u.password) throw new Error(); } catch { return 'Chứng cứ phải là URL http/https hợp lệ, không chứa tài khoản.'; } }
    return '';
  }
  private storageKey(): string | null { const p = this.auth.getUserProfile(); const id = p?.userId || p?.email; return id ? `huit_disposal_pending:${encodeURIComponent(id)}` : null; }
  private persist(): void { const key = this.storageKey(); if (key) { try { sessionStorage.setItem(key, JSON.stringify(this.pending)); } catch { /* retain in memory */ } } }
  private clearPending(): void { this.pending = null; const key = this.storageKey(); if (key) { try { sessionStorage.removeItem(key); } catch { /* retain server idempotency */ } } }
  private restore(): void {
    const key = this.storageKey(); if (!key) return;
    try { const value = JSON.parse(sessionStorage.getItem(key) || 'null');
      if (value && typeof value.requestId === 'string' && Number.isSafeInteger(value.auditId) && Array.isArray(value.lines) && value.lines.length > 0 && value.lines.length <= 50) {
        this.pending = value; this.sourceAuditId = value.auditId; this.formError = 'Có yêu cầu lập nháp chưa rõ kết quả. Chưa tự gửi lại; bấm gửi lại để đối chiếu.';
      }
    } catch { /* Ignore invalid local state; no mutation on restoration. */ }
  }
  private error(e: any, fallback: string): string { if (e?.status === 401 || e?.status === 403) return 'Bạn không có quyền thực hiện thao tác này.'; const body = e?.error; const text = typeof body === 'string' ? body : body?.detail || body?.message; return typeof text === 'string' && text.trim() ? text : fallback; }
  private refresh(): void { if (!this.destroyRef.destroyed) this.cdr.detectChanges(); }
}
