import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { ReportFileSaver } from '../../../services/report-file-saver';
import { ClassificationCoverage, ObligationFilters, StudentObligation, StudentObligationReport as Report } from '../../../services/student-obligation-report.models';
import { StudentObligationReportService } from '../../../services/student-obligation-report.service';
import { StudentObligationReport } from './student-obligation-report';

const student: StudentObligation = { userId: 'reader-1', mssv: '2001240001', fullName: 'Trần Thị B', rawDepartment: 'CNTT',
  facultyId: 'FIT', facultyName: 'Khoa Công nghệ Thông tin', majorName: null, mappingStatus: 'MAPPED',
  overdueItemCount: 1, earliestDueDate: '2026-10-28T09:00:00', overdueDays: 1, chargedAmount: 200000,
  paidOrDepositAmount: 70000, waivedAmount: 20000, adjustedAmount: 10000, outstandingAmount: 100000,
  walletDebt: 100000, walletDifference: 0, reconciliationDifference: 0 };
function report(filters: ObligationFilters = { mode: 'EITHER', facultyId: '', q: '' }, page = 0, size = 25): Report {
  return { snapshotAt: '2026-10-29T10:15:30', timezone: 'Asia/Ho_Chi_Minh', currency: 'VND',
    filter: { mode: filters.mode, facultyId: filters.facultyId || null, query: filters.q }, page, size, totalStudents: 26,
    students: [{ ...student }], overdueItems: [{ userId: student.userId, ticketId: 24, loanDetailId: 31, barcode: 'BC-024',
      bookTitle: 'Clean Architecture', ticketType: 'BORROW', dueDate: '2026-10-28T09:00:00', overdueDays: 1 }],
    ledgerEntries: [{ userId: student.userId, transactionId: 40, ticketId: 24, loanDetailId: 31, penaltyCaseId: 77,
      type: 'REPAIR_FEE', reason: 'Sửa tài liệu', status: 'PARTIALLY_WAIVED', amount: 200000, satisfiedAmount: 100000,
      outstandingAmount: 100000, paymentMethod: 'CASH', receiptNumber: 'RCPT-1', createdAt: '2026-10-28T10:00:00' }] };
}
const complete: ClassificationCoverage = { totalStudents: 138, classifiedStudents: 138, unclassifiedStudents: 0, complete: true, unmappedDepartments: [] };
const mime = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const response = () => new HttpResponse({ body: new Blob(['fixture'], { type: mime }), headers: new HttpHeaders({ 'Content-Type': mime, 'Content-Disposition': 'attachment; filename="thu-thu-either-20261029-101530.xlsx"' }) });

describe('Student obligation report UI', () => {
  let fixture: ComponentFixture<StudentObligationReport>, component: StudentObligationReport;
  let service: ReturnType<typeof stub>, saver: { save: ReturnType<typeof vi.fn> };
  function stub() { return {
    faculties: vi.fn(() => of([{ facultyId: 'FIT', facultyName: 'Khoa Công nghệ Thông tin' }, { facultyId: 'UNCLASSIFIED', facultyName: 'Chưa phân loại' }])),
    coverage: vi.fn(() => of({ ...complete })), preview: vi.fn((filters: ObligationFilters, page: number, size: number) => of(report(filters, page, size))),
    export: vi.fn((_filters: ObligationFilters) => of(response())),
  }; }
  beforeEach(async () => {
    service = stub(); saver = { save: vi.fn() };
    await TestBed.configureTestingModule({ imports: [StudentObligationReport], providers: [provideRouter([]),
      { provide: StudentObligationReportService, useValue: service }, { provide: ReportFileSaver, useValue: saver }],
    }).compileComponents();
    fixture = TestBed.createComponent(StudentObligationReport); component = fixture.componentInstance;
    fixture.detectChanges(); await fixture.whenStable();
  });
  afterEach(() => fixture.destroy());

  it('loads server data, snapshot/VND, separate monetary columns and coverage without recomputing debt', () => {
    const text = fixture.nativeElement.textContent;
    expect(service.preview).toHaveBeenCalledWith({ mode: 'EITHER', facultyId: '', q: '' }, 0, 25);
    expect(text).toContain('138 / 138'); expect(text).toContain('Trần Thị B'); expect(text).toContain('VND');
    expect(text).toContain('29/10/2026 10:15:30'); expect(text).toContain('Miễn giảm'); expect(text).toContain('Điều chỉnh giảm');
    expect(text).toContain('70,000'); expect(text).toContain('100,000'); expect(text).not.toContain('Cần đối soát');
    expect(fixture.nativeElement.querySelector('a').getAttribute('href')).toContain('referenceType=USER&referenceId=reader-1');
    expect(fixture.nativeElement.querySelector('option[value="UNCLASSIFIED"]')).toBeNull();
  });
  it('applies faculty/mode/search together at page zero and uses the same filters for export', () => {
    component.filters = { mode: 'DEBT', facultyId: 'FIT', q: '  Trần  ' }; component.pageSize = 10;
    component.applyFilters(); component.exportReport();
    expect(service.preview).toHaveBeenLastCalledWith({ mode: 'DEBT', facultyId: 'FIT', q: 'Trần' }, 0, 10);
    expect(service.export).toHaveBeenCalledWith({ mode: 'DEBT', facultyId: 'FIT', q: 'Trần' });
    expect(saver.save).toHaveBeenCalledOnce();
  });
  it('blocks exporting/paging unapplied filters so the downloaded dataset matches the visible report', () => {
    component.filters.facultyId = 'FIT'; fixture.detectChanges();
    component.exportReport(); component.changePage(1);
    expect(component.dirty).toBe(true); expect(service.export).not.toHaveBeenCalled(); expect(service.preview).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).toContain('Bộ lọc đã thay đổi');
    expect(fixture.nativeElement.querySelector('.report-heading button').disabled).toBe(true);
  });
  it('uses server pagination and resets mode/faculty/search/page size on reset', () => {
    component.changePage(1);
    expect(service.preview).toHaveBeenLastCalledWith({ mode: 'EITHER', facultyId: '', q: '' }, 1, 25);
    component.filters = { mode: 'OVERDUE', facultyId: 'FIT', q: '2001' }; component.pageSize = 10;
    component.applyFilters(); component.resetFilters();
    expect(service.preview).toHaveBeenLastCalledWith({ mode: 'EITHER', facultyId: '', q: '' }, 0, 25);
    expect(component.filters).toEqual({ mode: 'EITHER', facultyId: '', q: '' });
  });
  it('expands the exact reader details, emits the real ticket and links to case/counter', () => {
    const ticket = vi.fn(); component.ticketRequested.subscribe(ticket);
    component.selectStudent(component.report!.students[0]); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Clean Architecture'); expect(fixture.nativeElement.textContent).toContain('Sửa tài liệu');
    const detail: HTMLElement = fixture.nativeElement.querySelector('.student-detail');
    const ticketButton = [...detail.querySelectorAll('button')].find(button => button.textContent?.includes('Xem phiếu'))!;
    ticketButton.click(); expect(ticket).toHaveBeenCalledWith(24);
    const hrefs = [...detail.querySelectorAll('a')].map(anchor => anchor.getAttribute('href'));
    expect(hrefs.some(href => href?.includes('referenceId=24') && href.includes('mode=return'))).toBe(true);
    expect(hrefs.some(href => href?.includes('referenceType=PENALTY_CASE&referenceId=77'))).toBe(true);
  });
  it('distinguishes returned readers with debt, missing wallet and reconciliation issues', () => {
    const data = component.report!; data.students[0] = { ...student, overdueItemCount: 0, earliestDueDate: null, walletDebt: null, walletDifference: null, reconciliationDifference: 100 };
    data.overdueItems = []; component.selectStudent(data.students[0]); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Chưa có ví'); expect(fixture.nativeElement.textContent).toContain('Cần đối soát');
    expect(fixture.nativeElement.textContent).toContain('vẫn còn công nợ sau khi trả sách');
  });
  it('shows an empty result without inventing data and disables export', () => {
    service.preview.mockReturnValueOnce(of({ ...report(), totalStudents: 0, students: [], overdueItems: [], ledgerEntries: [] }));
    component.applyFilters(); fixture.detectChanges(); component.exportReport();
    expect(fixture.nativeElement.textContent).toContain('Không có sinh viên khớp bộ lọc');
    expect(fixture.nativeElement.querySelector('.report-table')).toBeNull(); expect(service.export).not.toHaveBeenCalled();
  });
  it('clears stale results on network failure and retries the last request successfully', async () => {
    service.preview.mockReturnValueOnce(throwError(() => ({ status: 0 })));
    component.applyFilters(); await fixture.whenStable(); fixture.detectChanges();
    expect(component.report).toBeNull(); expect(component.errorMessage).toContain('Không kết nối');
    expect(fixture.nativeElement.querySelector('.report-table')).toBeNull();
    component.retryPreview(); expect(component.report?.students[0].mssv).toBe(student.mssv);
  });
  it('does not claim classification complete when the audit API fails and permits retry', async () => {
    service.coverage.mockReturnValueOnce(throwError(() => ({ status: 500 })));
    component.loadCoverage(); await fixture.whenStable(); fixture.detectChanges();
    expect(component.coverage).toBeNull(); expect(fixture.nativeElement.textContent).not.toContain('138 / 138');
    expect(component.coverageError).toContain('Chưa kiểm tra'); component.loadCoverage(); expect(component.coverage?.complete).toBe(true);
  });
  it('exposes missing classification groups without mapping them to a fabricated faculty', () => {
    component.coverage = { totalStudents: 10, classifiedStudents: 8, unclassifiedStudents: 2, complete: false,
      unmappedDepartments: [{ rawDepartment: null, studentCount: 1 }, { rawDepartment: 'Nguồn chưa xác định', studentCount: 1 }] };
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('2 / 10');
    expect(fixture.nativeElement.textContent).toContain('Nguồn chưa xác định');
    expect(fixture.nativeElement.querySelector('option[value="UNCLASSIFIED"]')).not.toBeNull();
  });
  it('keeps the report usable if faculties fail and retries the faculty endpoint', async () => {
    service.faculties.mockReturnValueOnce(throwError(() => ({ status: 500 })));
    component.loadFaculties(); await fixture.whenStable(); expect(component.facultyError).toContain('Không tải được danh mục');
    expect(component.report).not.toBeNull(); component.loadFaculties(); expect(component.facultyError).toBe('');
  });
  it('locks duplicate exports and saves the exact Blob with the server filename', () => {
    const pending = new Subject<HttpResponse<Blob>>(); service.export.mockReturnValueOnce(pending);
    component.exportReport(); component.exportReport(); fixture.detectChanges();
    expect(component.exporting).toBe(true); expect(service.export).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.querySelector('fieldset').disabled).toBe(true);
    const excel = response(); pending.next(excel); pending.complete();
    expect(saver.save).toHaveBeenCalledWith(excel.body, 'thu-thu-either-20261029-101530.xlsx'); expect(component.exporting).toBe(false);
    expect(component.exportMessage).toContain('toàn bộ kết quả');
  });
  it('reports permission errors without downloading a file and can retry', async () => {
    service.export.mockReturnValueOnce(throwError(() => ({ status: 403, error: new Blob(['denied']) })));
    component.exportReport(); await fixture.whenStable();
    expect(component.exportError).toContain('không có quyền'); expect(saver.save).not.toHaveBeenCalled();
    component.exportReport(); expect(saver.save).toHaveBeenCalledOnce();
  });
  it('reads server error details from Blob responses instead of saving them as Excel', async () => {
    const problem = new Blob(['{"detail":"Máy chủ chưa xuất được báo cáo"}'], { type: 'application/problem+json' });
    Object.defineProperty(problem, 'text', { value: async () => '{"detail":"Máy chủ chưa xuất được báo cáo"}' });
    service.export.mockReturnValueOnce(throwError(() => ({ status: 500, error: problem })));
    component.exportReport(); await fixture.whenStable(); expect(component.exportError).toContain('Máy chủ chưa xuất');
    expect(saver.save).not.toHaveBeenCalled();
    service.export.mockReturnValueOnce(of(new HttpResponse({ body: problem, headers: new HttpHeaders({ 'Content-Type': 'application/problem+json' }) })));
    component.exportReport(); expect(component.exportError).toContain('không phải file Excel'); expect(saver.save).not.toHaveBeenCalled();
  });
  it('rejects an incomplete success response and overly long search terms without displaying false totals', () => {
    service.preview.mockReturnValueOnce(of({} as Report)); component.applyFilters(); fixture.detectChanges();
    expect(component.report).toBeNull(); expect(component.errorMessage).toContain('thiếu dữ liệu');
    const calls = service.preview.mock.calls.length; component.filters.q = 'x'.repeat(101); component.applyFilters();
    expect(service.preview).toHaveBeenCalledTimes(calls); expect(component.errorMessage).toContain('100 ký tự');
  });
});
