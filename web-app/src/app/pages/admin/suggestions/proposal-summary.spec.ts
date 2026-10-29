import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { ReportFileSaver } from '../../../services/report-file-saver';
import { ProposalSummaryService, ProposalSummaryReport, ProposalSummaryFilters } from '../../../services/proposal-summary.service';
import { ProposalSummary } from './proposal-summary';

const mime = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
function report(f: ProposalSummaryFilters = { facultyId: '', from: '', to: '', status: 'ALL' }, page = 0, size = 25): ProposalSummaryReport {
  return { snapshotAt: '2026-10-29T12:34:56', timezone: 'Asia/Ho_Chi_Minh', filter: { ...f, facultyId: f.facultyId || null, from: f.from || null, to: f.to || null },
    page, size, totalGroups: 26, totalProposals: 28, totalPeople: 2, groups: [{ groupKey: 'g1', title: 'Clean Code', authorName: 'Robert', publisher: 'NXB',
      canonicalIsbn: '9780132350884', identityType: 'ISBN', publicationYear: 2008, edition: '1', proposalCount: 3, peopleCount: 2,
      faculties: [{ facultyId: 'FIT', facultyName: 'Khoa CNTT', proposalCount: 2, peopleCount: 1 }, { facultyId: 'OTHER', facultyName: 'Khoa khác', proposalCount: 1, peopleCount: 1 }],
      warnings: ['Có người gửi chưa xác định khoa'], sources: [{ proposalId: 7, userId: 'u1', userName: 'Sinh viên A', mssv: 'SV1', rawDepartment: 'CNTT',
        facultyId: 'FIT', facultyName: 'Khoa CNTT', title: 'Clean Code', authorName: 'Robert', publisher: 'NXB', isbn: '0-13-235088-2', publicationYear: 2008,
        edition: '1', status: 'PENDING', createdAt: '2026-10-01T09:00:00' }] }] };
}
const response = () => new HttpResponse({ body: new Blob(['xlsx'], { type: mime }), headers: new HttpHeaders({ 'Content-Type': mime, 'Content-Disposition': 'attachment; filename="de-xuat-mua-test.xlsx"' }) });
describe('Proposal summary UI', () => {
  let fixture: ComponentFixture<ProposalSummary>, component: ProposalSummary, service: ReturnType<typeof stub>, saver: { save: ReturnType<typeof vi.fn> };
  function stub() { return { faculties: vi.fn(() => of([{ facultyId: 'FIT', facultyName: 'Khoa CNTT' }, { facultyId: 'UNCLASSIFIED', facultyName: 'Chưa phân loại' }])),
    preview: vi.fn((f: ProposalSummaryFilters, page: number, size: number) => of(report(f, page, size))), export: vi.fn((_f: ProposalSummaryFilters) => of(response())) }; }
  beforeEach(async () => {
    service = stub(); saver = { save: vi.fn() };
    await TestBed.configureTestingModule({ imports: [ProposalSummary], providers: [{ provide: ProposalSummaryService, useValue: service }, { provide: ReportFileSaver, useValue: saver }] }).compileComponents();
    fixture = TestBed.createComponent(ProposalSummary); component = fixture.componentInstance; fixture.detectChanges(); await fixture.whenStable();
  });
  afterEach(() => fixture.destroy());
  it('shows server counts, exact original references, ISBN and faculty distribution', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('26 nhóm · 28 lượt · 2 người'); expect(text).toContain('3 lượt'); expect(text).toContain('2 người');
    expect(text).toContain('9780132350884'); expect(text).toContain('Khoa CNTT: 2 lượt / 1 người'); expect(text).toContain('Có người gửi chưa xác định khoa');
    const emit = vi.fn(); component.openProposal.subscribe(emit);
    fixture.nativeElement.querySelector('.source button').click(); expect(emit).toHaveBeenCalledWith(7);
  });
  it('applies faculty/date/status together then exports those exact filters', () => {
    component.filters = { facultyId: 'FIT', from: '2026-10-01', to: '2026-10-29', status: 'PENDING' }; component.pageSize = 10;
    component.applyFilters(); component.exportReport();
    expect(service.preview).toHaveBeenLastCalledWith(component.filters, 0, 10); expect(service.export).toHaveBeenCalledWith(component.filters);
    expect(saver.save).toHaveBeenCalledWith(expect.any(Blob), 'de-xuat-mua-test.xlsx');
  });
  it('blocks export and page changes while filters differ from the shown snapshot', () => {
    component.filters.to = '2026-10-01'; fixture.detectChanges(); component.exportReport(); component.changePage(1);
    expect(service.export).not.toHaveBeenCalled(); expect(service.preview).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelector('.export-button').disabled).toBe(true); expect(fixture.nativeElement.textContent).toContain('Bộ lọc đã đổi');
  });
  it('uses group pagination and resets all independent filters', () => {
    component.changePage(1); expect(service.preview).toHaveBeenLastCalledWith(component.filters, 1, 25);
    component.filters.status = 'REJECTED'; component.resetFilters(); expect(component.filters).toEqual({ facultyId: '', from: '', to: '', status: 'ALL' });
    expect(service.preview).toHaveBeenLastCalledWith(component.filters, 0, 25);
  });
  it('rejects reversed dates without querying or exporting an old view', () => {
    component.filters.from = '2026-10-29'; component.filters.to = '2026-10-01'; component.applyFilters(); component.exportReport();
    expect(service.preview).toHaveBeenCalledTimes(1); expect(service.export).not.toHaveBeenCalled(); expect(component.error).toContain('Ngày bắt đầu');
  });
  it('locks fields and prevents duplicate export while waiting for the file', () => {
    const pending = new Subject<HttpResponse<Blob>>(); service.export.mockReturnValue(pending);
    component.exportReport(); component.exportReport(); component.resetFilters(); fixture.detectChanges();
    expect(service.export).toHaveBeenCalledOnce(); expect(fixture.nativeElement.querySelector('fieldset').disabled).toBe(true);
    pending.next(response()); pending.complete(); expect(component.exporting).toBe(false); expect(saver.save).toHaveBeenCalledOnce();
  });
  it('displays empty results and permits a valid header-only workbook', () => {
    const empty = { ...report(), groups: [], totalGroups: 0, totalPeople: 0, totalProposals: 0 }; service.preview.mockReturnValue(of(empty));
    component.applyFilters(); fixture.detectChanges(); component.exportReport();
    expect(fixture.nativeElement.textContent).toContain('Không có đề xuất phù hợp'); expect(saver.save).toHaveBeenCalledOnce();
  });
  it('handles 403 and retrying faculty/report requests without fabricated zero totals', () => {
    service.preview.mockReturnValue(throwError(() => ({ status: 403 }))); service.faculties.mockReturnValue(throwError(() => ({})));
    component.applyFilters(); component.loadFaculties();
    return fixture.whenStable().then(() => {
      fixture.detectChanges(); expect(component.report).toBeNull(); expect(component.error).toContain('không có quyền'); expect(component.facultyError).toContain('Không tải');
      service.preview.mockImplementation((f, p, s) => of(report(f, p, s))); component.applyFilters(); expect(component.report?.totalGroups).toBe(26);
    });
  });
  it('rejects JSON/empty responses and releases the exporting state on download failure', () => {
    service.export.mockReturnValue(of(new HttpResponse({ body: new Blob(['{}'], { type: 'application/json' }) })));
    component.exportReport(); expect(saver.save).not.toHaveBeenCalled(); expect(component.exportError).toContain('không phải file Excel');
    service.export.mockReturnValue(of(response())); saver.save.mockImplementation(() => { throw new Error('Bị chặn tải'); });
    component.exportReport(); expect(component.exportError).toBe('Bị chặn tải'); expect(component.exporting).toBe(false);
  });
  it('renders source text containing formula or markup as ordinary escaped text', () => {
    component.report!.groups[0].title = '=HYPERLINK("bad") <img src=x onerror=alert(1)>'; fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.summary-scroll img')).toBeNull(); expect(fixture.nativeElement.textContent).toContain('=HYPERLINK');
  });
});
