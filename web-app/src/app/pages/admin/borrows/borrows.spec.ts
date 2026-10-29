import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { CirculationService, LoanTicketDetail, LoanTicketSummary } from '../../../services/circulation.service';
import { Borrows } from './borrows';
import { StudentObligationReport } from './student-obligation-report';
import { StudentObligationReportService } from '../../../services/student-obligation-report.service';

describe('Borrows', () => {
  let component: Borrows;
  let fixture: ComponentFixture<Borrows>;

  const today = new Date().toISOString().slice(0, 10);
  const summary: LoanTicketSummary = {
    ticketId: 24, userId: 'user-1', mssv: '2001240001', readerName: 'Trần Thị B',
    department: 'Công nghệ thông tin', cardStatus: 'ACTIVE', ticketType: 'BORROW', status: 'OVERDUE',
    createdAt: `${today}T09:00:00`, nearestDueDate: '2026-10-14T09:00:00',
    itemCount: 1, overdueItemCount: 1, totalLateFine: 3000,
  };
  const detail: LoanTicketDetail = {
    ticketId: 24, userId: 'user-1', mssv: '2001240001', readerName: 'Trần Thị B',
    ticketType: 'BORROW', status: 'OVERDUE', createdAt: `${today}T09:00:00`,
    items: [{ ticketId: 24, detailId: 31, bookTitle: 'Clean Architecture', barcode: 'BC-024',
      ticketType: 'BORROW', status: 'OVERDUE', renewalCount: 0, lateFineAccrued: 3000 }],
  };
  const service = {
    getStaffLoans: vi.fn(() => of([summary])),
    getLoan: vi.fn(() => of(detail)),
    approveReservation: vi.fn(() => of({ ticketId: 24, previousStatus: 'PENDING', status: 'RESERVED', message: 'Đã duyệt', changedAt: '2026-10-18T09:00:00' })),
    rejectReservation: vi.fn(() => of({ ticketId: 24, previousStatus: 'PENDING', status: 'REJECTED', message: 'Đã từ chối', changedAt: '2026-10-18T09:00:00' })),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [Borrows],
      providers: [provideRouter([]), { provide: CirculationService, useValue: service }, {
        provide: StudentObligationReportService, useValue: {
          faculties: () => of([]), coverage: () => of({ totalStudents: 0, classifiedStudents: 0, unclassifiedStudents: 0, complete: true, unmappedDepartments: [] }),
          preview: () => of({ snapshotAt: '2026-10-29T10:00:00', timezone: 'Asia/Ho_Chi_Minh', currency: 'VND',
            filter: { mode: 'EITHER', facultyId: null, query: '' }, page: 0, size: 25, totalStudents: 0, students: [], overdueItems: [], ledgerEntries: [] }),
        },
      }],
    }).compileComponents();

    fixture = TestBed.createComponent(Borrows);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    component.fromDate = '';
    component.toDate = '';
    fixture.detectChanges();
  });

  it('loads and renders server-backed loan summaries', () => {
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(service.getStaffLoans).toHaveBeenCalled();
    expect(text).toContain('Trần Thị B');
    expect(text).toContain('2001240001');
    expect(text).toContain('Quá hạn');
    expect(text).toMatch(/3[,.]000 VNĐ/);
    expect(text).not.toContain('28,450');
  });

  it('filters the real list by reader and status', () => {
    component.searchTerm = 'không tồn tại';
    expect(component.filteredLoans).toHaveLength(0);
    component.searchTerm = 'Trần Thị';
    component.statusFilter = 'OVERDUE';
    expect(component.filteredLoans).toEqual([summary]);
    component.statusFilter = 'RETURNED';
    expect(component.filteredLoans).toHaveLength(0);
  });

  it('loads ticket details using the ticket id', () => {
    component.viewDetails(24);
    fixture.detectChanges();
    expect(service.getLoan).toHaveBeenCalledWith(24);
    expect(fixture.nativeElement.textContent).toContain('Clean Architecture');
  });

  it('opens the exact ticket referenced by a notification', () => {
    (component as any).route = {
      snapshot: { queryParamMap: { get: (key: string) => key === 'referenceType' ? 'LOAN_TICKET' : key === 'referenceId' ? '24' : null } },
    };

    component.ngOnInit();

    expect(service.getLoan).toHaveBeenCalledWith(24);
    expect(component.selectedLoan?.ticketId).toBe(24);
  });

  it('routes a legacy ticket to the counter without offering a manual approval action', () => {
    component.selectedLoan = { ...detail, status: 'PENDING' };
    fixture.detectChanges();
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('a');
    expect(link.textContent).toContain('Mở phiếu tại quầy');
    expect(link.getAttribute('href')).toContain('/admin/circulation');
    expect(link.getAttribute('href')).toContain('referenceId=24');
    expect(fixture.nativeElement.textContent).not.toContain('Duyệt yêu cầu');
    expect(service.approveReservation).not.toHaveBeenCalled();
  });

  it('requires and submits a rejection reason', () => {
    component.selectedLoan = { ...detail, status: 'PENDING' };
    component.rejectSelected();
    expect(service.rejectReservation).not.toHaveBeenCalled();
    expect(component.errorMessage).toContain('Cần nhập lý do');

    component.rejectionReason = 'Thẻ thư viện hết hạn';
    component.rejectSelected();
    expect(service.rejectReservation).toHaveBeenCalledWith(24, 'Thẻ thư viện hết hạn');
  });

  it('loads the report lazily and retains its filters when switching to the loan list and back', async () => {
    expect(fixture.debugElement.query(By.directive(StudentObligationReport))).toBeNull();
    fixture.nativeElement.querySelectorAll('.view-tabs button')[1].click(); await fixture.whenStable(); fixture.detectChanges();
    const report = fixture.debugElement.query(By.directive(StudentObligationReport)).componentInstance as StudentObligationReport;
    const search: HTMLInputElement = fixture.nativeElement.querySelector('input[name="obligation-query"]')
      || fixture.nativeElement.querySelector('app-student-obligation-report input[type="search"]');
    search.value = '2001'; search.dispatchEvent(new Event('input', { bubbles: true })); await fixture.whenStable();
    fixture.nativeElement.querySelectorAll('.view-tabs button')[0].click(); await fixture.whenStable();
    fixture.nativeElement.querySelectorAll('.view-tabs button')[1].click(); await fixture.whenStable(); fixture.detectChanges();
    expect(fixture.debugElement.query(By.directive(StudentObligationReport)).componentInstance).toBe(report);
    expect(report.filters.q).toBe('2001'); expect(component.loans).toEqual([summary]);
  });

  it('opens a ticket from the report in the existing detail panel without losing the report view', async () => {
    component.switchView('report'); fixture.detectChanges(); await fixture.whenStable();
    const report = fixture.debugElement.query(By.directive(StudentObligationReport)).componentInstance as StudentObligationReport;
    report.ticketRequested.emit(24); fixture.detectChanges();
    expect(service.getLoan).toHaveBeenCalledWith(24); expect(component.activeView).toBe('report');
    expect(fixture.nativeElement.querySelector('.detail-panel').textContent).toContain('Clean Architecture');
    component.closeDetails(); expect(component.activeView).toBe('report');
  });

  it('opens the dashboard deep link directly in the filtered overdue report', async () => {
    const route=TestBed.inject(ActivatedRoute);
    vi.spyOn(route.snapshot,'queryParamMap','get').mockReturnValue(convertToParamMap({view:'report',q:'2001240001',reportMode:'OVERDUE'}));
    component.ngOnInit();fixture.detectChanges();await fixture.whenStable();fixture.detectChanges();
    const report=fixture.debugElement.query(By.directive(StudentObligationReport)).componentInstance as StudentObligationReport;
    expect(component.activeView).toBe('report');expect(report.filters.q).toBe('2001240001');expect(report.filters.mode).toBe('OVERDUE');
  });

  it('shows ticket lookup errors while the report tab is active', () => {
    service.getLoan.mockReturnValueOnce(throwError(() => ({ error: { message: 'Phiếu không tồn tại' } })));
    component.switchView('report'); component.viewDetails(999); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Phiếu không tồn tại');
  });
});
