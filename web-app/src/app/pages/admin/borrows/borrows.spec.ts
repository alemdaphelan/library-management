import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CirculationService, LoanTicketDetail, LoanTicketSummary } from '../../../services/circulation.service';
import { Borrows } from './borrows';

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
      providers: [{ provide: CirculationService, useValue: service }],
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

  it('approves a pending ticket using its real transition endpoint', () => {
    component.selectedLoan = { ...detail, status: 'PENDING' };
    component.approveSelected();

    expect(service.approveReservation).toHaveBeenCalledWith(24);
    expect(service.getStaffLoans).toHaveBeenCalledTimes(2);
    expect(component.successMessage).toBe('Đã duyệt');
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
});
