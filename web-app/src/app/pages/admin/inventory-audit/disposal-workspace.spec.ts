import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { DisposalWorkspace } from './disposal-workspace';
import { DisposalService, DisposalCandidate, DisposalDetail, DisposalBatch, DisposalCreate, DisposalEdit, DisposalFilters } from '../../../services/disposal.service';
import { InventoryAuditService } from '../../../services/inventory-audit.service';
import { AuthService } from '../../../services/auth.service';
const candidate = (barcode = 'BC-1', eligible = true): DisposalCandidate => ({ fingerprint: 'a'.repeat(64), eligible, blockers: eligible ? [] : ['Bản sao đang được mượn'], activeProposalId: null,
  source: { auditId: 9, auditStatus: 'COMPLETED', auditUpdatedAt: '2026-10-29T09:00:00', detailId: `scan-${barcode}`, scanStatus: 'FOUND', scannedLocation: 'Tầng 1', scannedAt: '2026-10-29T08:00:00', detailUpdatedAt: '2026-10-29T08:00:00',
    barcode, bookId: 12, bookTitle: 'Giáo trình cũ', isbn: null, copyStatus: eligible ? 'AVAILABLE' : 'BORROWING', conditionNote: 'Bìa cũ', shelfId: 3, location: 'Tầng 1 / Kệ A / Ngăn 1', copyUpdatedAt: '2026-10-28T09:00:00', lockedByUser: null, lockedUntil: null } });
const detail = (): DisposalDetail => ({ item: { proposalId: 'proposal-1', requestId: 'request-1', barcode: 'BC-1', bookId: 12, bookTitle: 'Giáo trình cũ', auditId: 9, status: 'DRAFT', version: 0, proposedAction: 'REPAIR', createdBy: 'owner', createdByName: 'Thủ thư A', createdAt: '2026-10-29T10:00:00', updatedAt: '2026-10-29T10:00:00' },
  saved: { status: 'DRAFT', version: 0, observedCondition: 'Rách bìa', reasonCode: 'WORN', reason: 'Cần phục hồi', proposedAction: 'REPAIR', evidenceUrl: null, source: candidate().source }, current: candidate(), canEdit: true, canSubmit: true, canCancel: true, adminIntegrationStatus: 'WAITING_ADMIN_WORKFLOW', history: [] });
describe('Disposal workspace', () => {
  let fixture: ComponentFixture<DisposalWorkspace>, component: DisposalWorkspace, service: ReturnType<typeof stub>;
  function stub() { return { candidates: vi.fn((_id: number) => of([candidate(), candidate('BC-2'), candidate('BC-BLOCKED', false)])),
    list: vi.fn((_f: DisposalFilters, page: number, size: number) => of({ items: [detail().item], total: 26, page, size })),
    create: vi.fn((request: DisposalCreate) => of({ requestId: request.requestId, replay: false, proposals: [detail().item] })),
    get: vi.fn((_id: string) => of(detail())), edit: vi.fn((_id: string, _request: DisposalEdit) => of(detail())),
    submit: vi.fn((_id: string, _version: number) => of({ ...detail(), item: { ...detail().item, status: 'SUBMITTED' as const, version: 1 }, canEdit: false, canSubmit: false })),
    cancel: vi.fn((_id: string, _version: number) => of({ ...detail(), item: { ...detail().item, status: 'CANCELLED' as const, version: 1 }, canEdit: false, canSubmit: false, canCancel: false })) }; }
  beforeEach(async () => {
    sessionStorage.clear(); service = stub();
    await TestBed.configureTestingModule({ imports: [DisposalWorkspace], providers: [{ provide: DisposalService, useValue: service },
      { provide: InventoryAuditService, useValue: { getAudits: vi.fn(() => of([{ auditId: 9, status: 'COMPLETED' }])) } },
      { provide: AuthService, useValue: { getUserProfile: () => ({ userId: 'owner' }) } }] }).compileComponents();
    fixture = TestBed.createComponent(DisposalWorkspace); fixture.componentRef.setInput('auditId', 9); component = fixture.componentInstance;
    fixture.detectChanges(); await fixture.whenStable();
  });
  afterEach(() => { fixture.destroy(); sessionStorage.clear(); });
  function select(index = 0) { const row = component.rows[index]; row.selected = true; row.observedCondition = ' Rách bìa '; row.reason = ' Cần phục hồi '; return row; }
  it('loads actual source rows, blockers and list without performing any mutation', () => {
    expect(service.candidates).toHaveBeenCalledWith(9); expect(fixture.nativeElement.textContent).toContain('Bản sao đang được mượn');
    expect(fixture.nativeElement.querySelector('input[aria-label="Chọn BC-BLOCKED"]').disabled).toBe(true);
    expect(service.create).not.toHaveBeenCalled(); expect(service.submit).not.toHaveBeenCalled();
  });
  it('requires per-copy fields and a valid optional URL, rejecting blocked rows', () => {
    component.rows[0].selected = true; component.saveDrafts(); expect(component.formError).toContain('tình trạng');
    const row = select(); row.evidenceUrl = 'javascript:alert(1)'; component.saveDrafts(); expect(component.formError).toContain('http/https');
    row.selected = false; select(2); component.saveDrafts(); expect(service.create).not.toHaveBeenCalled();
  });
  it('creates selected copies only with exact source IDs and fingerprints, then reads persisted detail', () => {
    select(); const row = select(1); row.proposedAction = 'LIQUIDATE'; component.saveDrafts();
    const request = service.create.mock.calls[0][0]; expect(request.requestId).toMatch(/^[a-f0-9-]{36}$/); expect(request.lines).toHaveLength(2);
    expect(request.lines[0]).toMatchObject({ barcode: 'BC-1', sourceDetailId: 'scan-BC-1', expectedFingerprint: 'a'.repeat(64), observedCondition: 'Rách bìa', reason: 'Cần phục hồi' });
    expect(request.lines[1].proposedAction).toBe('LIQUIDATE'); expect(service.get).toHaveBeenCalledWith('proposal-1');
    expect(component.message).toContain('Chưa gửi'); expect(service.submit).not.toHaveBeenCalled(); expect(component.detail?.saved.source.location).toContain('Tầng 1');
  });
  it('cancels unsent forms without calling the API and requires reloading after source selection changes', () => {
    select(); component.cancelForm(); expect(component.rows).toHaveLength(0); expect(service.create).not.toHaveBeenCalled();
    component.loadSources(); select(); component.sourceAuditId = 10; component.saveDrafts(); expect(service.create).not.toHaveBeenCalled(); expect(component.formError).toContain('Tải nguồn');
  });
  it('prevents duplicate sends and preserves the same request/body after uncertain errors', () => {
    const waiting = new Subject<DisposalBatch>(); service.create.mockReturnValue(waiting); select(); component.saveDrafts(); component.saveDrafts();
    expect(service.create).toHaveBeenCalledOnce(); const request = service.create.mock.calls[0][0]; waiting.error({ status: 0 });
    expect(component.pending).toEqual(request); expect(JSON.parse(sessionStorage.getItem('huit_disposal_pending:owner')!)).toEqual(request);
    service.create.mockImplementation(r => of({ requestId: r.requestId, replay: true, proposals: [detail().item] })); component.sendPending();
    expect(service.create).toHaveBeenLastCalledWith(request); expect(component.pending).toBeNull(); expect(sessionStorage.getItem('huit_disposal_pending:owner')).toBeNull();
  });
  it('restores a user-scoped pending request without automatically submitting it', () => {
    const request: DisposalCreate = { requestId: crypto.randomUUID(), auditId: 9, lines: [{ ...candidate(), barcode: 'BC-1' } as any] };
    sessionStorage.setItem('huit_disposal_pending:owner', JSON.stringify(request));
    fixture.destroy(); fixture = TestBed.createComponent(DisposalWorkspace); component = fixture.componentInstance; fixture.detectChanges();
    expect(component.pending?.requestId).toBe(request.requestId); expect(service.create).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Gửi lại nguyên yêu cầu');
  });
  it('keeps form values on a definitive 409 and permits a source reload', () => {
    service.create.mockReturnValue(throwError(() => ({ status: 409, error: { detail: 'Nguồn đã đổi' } }))); select(); component.saveDrafts();
    expect(component.pending).toBeNull(); expect(component.rows[0].selected).toBe(true); expect(component.formError).toBe('Nguồn đã đổi');
    component.loadSources(); expect(component.rows[0].selected).toBe(false);
  });
  it('saves the current reviewed fingerprint/version, and blocks submission of unsaved changes', () => {
    component.openDetail('proposal-1'); component.editFields.reason = 'Đổi lý do'; component.transition(true);
    expect(service.submit).not.toHaveBeenCalled(); expect(component.detailError).toContain('Lưu thay đổi');
    component.saveEdit(); expect(service.edit).toHaveBeenCalledWith('proposal-1', expect.objectContaining({ version: 0, expectedFingerprint: 'a'.repeat(64), reason: 'Đổi lý do' }));
  });
  it('submits the saved proposal and never calls its status a completed liquidation', () => {
    component.openDetail('proposal-1'); component.transition(true); fixture.detectChanges();
    expect(service.submit).toHaveBeenCalledWith('proposal-1', 0); expect(component.detail?.item.status).toBe('SUBMITTED');
    expect(component.message).toContain('Chưa xử lý thực tế'); expect(fixture.nativeElement.textContent).toContain('Chưa ghi nhận kết quả');
  });
  it('allows cancellation using the actual version and preserves history view', () => {
    component.openDetail('proposal-1'); component.transition(false); expect(service.cancel).toHaveBeenCalledWith('proposal-1',0);
    expect(component.detail?.item.status).toBe('CANCELLED'); expect(component.message).toContain('lịch sử vẫn');
  });
  it('locks edits after uncertain mutation until detail is reloaded; failed reads can retry', () => {
    component.openDetail('proposal-1'); service.submit.mockReturnValue(throwError(() => ({ status: 500 }))); component.transition(true);
    expect(component.detailUncertain).toBe(true); component.transition(false); expect(service.cancel).not.toHaveBeenCalled();
    service.get.mockReturnValueOnce(throwError(() => ({ status: 503 }))); component.reloadDetail(); expect(component.detail).toBeNull();
    component.reloadDetail(); expect(component.detail?.item.proposalId).toBe('proposal-1'); expect(component.detailUncertain).toBe(false);
  });
  it('shows stale-source and permission limitations, and applies list filters before paging', () => {
    const d = detail(); d.canSubmit = false; service.get.mockReturnValue(of(d)); component.openDetail('proposal-1'); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nguồn kiểm kê/bản sao đã đổi');
    component.filters = { status: 'SUBMITTED', auditId: '9', barcode: ' BC-1 ' }; component.changePage(1); expect(service.list).toHaveBeenCalledTimes(1);
    component.applyFilters(); expect(service.list).toHaveBeenLastCalledWith({ status: 'SUBMITTED', auditId: '9', barcode: 'BC-1' }, 0, 25);
    service.get.mockReturnValue(throwError(() => ({ status: 403 }))); component.openDetail('proposal-1'); expect(component.detailError).toContain('không có quyền');
  });
});
