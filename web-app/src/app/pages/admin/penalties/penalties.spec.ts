import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { UserService } from '../../../services/user.service';
import { CirculationService } from '../../../services/circulation.service';
import { AuthService } from '../../../services/auth.service';
import { PenaltyCaseRequest, PenaltyService } from '../../../services/penalty.service';
import { Penalties } from './penalties';

describe('Penalties', () => {
  let fixture: ComponentFixture<Penalties>;
  let component: Penalties;
  const service = {
    getCases: vi.fn(() => of([{
      caseId: 9, userId: 'user-1', ticketId: 5, loanDetailId: 6, barcode: 'BC-LOST',
      incidentType: 'LOST', description: 'Báo mất', settlementMethod: 'CASH', repairFee: 0,
      compensationAmount: 500000, technicalFee: 50000, overdueFineSnapshot: 3000,
      totalAmount: 550000, paidAmount: 0, status: 'OUTSTANDING',
      reportedAt: '2026-10-18T09:00:00', settlementDueAt: '2026-11-17T09:00:00',
    }])),
    getAdjustments: vi.fn(() => of([])),
    createCase: vi.fn((_request: PenaltyCaseRequest) => of({})),
    recordPayment: vi.fn(() => of({ receiptNumber: 'RCPT-01', allocations: [], remainingDebt: 0 })),
    acceptReplacement: vi.fn(() => of({})),
    submitReview: vi.fn(() => of({})),
    reviewWaiver: vi.fn(() => of({})),
    createRate: vi.fn(() => of({})),
    requestAdjustment: vi.fn(() => of({})),
    reviewAdjustment: vi.fn(() => of({})),
  };
  const profile:any={student:{userId:'reader-1',mssv:'2001240001',fullName:'Sinh viên A'},card:{totalDebt:10000},loans:[{ticketId:71,status:'BORROWING'}]};
  const users={getReaderProfile:vi.fn(()=>of(profile))};
  const lossReport:any={ticketId:71,userId:'reader-1',mssv:'2001240001',readerName:'Sinh viên A',status:'BORROWING',items:[{detailId:81,ticketId:71,bookTitle:'Giáo trình A',barcode:'BC-A',itemStatus:'BORROWING',lossReportedAt:'2026-11-04T10:00:00',lossReportDescription:'Mất trên xe buýt'}]};
  const circulation={getLoan:vi.fn(()=>of({userId:'reader-1',items:[{detailId:81,ticketId:71,bookTitle:'Giáo trình A',barcode:'BC-A',itemStatus:'BORROWING'}]})),getLossReport:vi.fn(()=>of(lossReport))};
  const auth = { getRole: vi.fn(() => 'LIBRARIAN'), getUserProfile: vi.fn(() => null) };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [Penalties],
      providers: [
        { provide: PenaltyService, useValue: service },
        { provide: AuthService, useValue: auth },
        {provide:UserService,useValue:users},{provide:CirculationService,useValue:circulation},
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Penalties);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('renders server-backed penalty cases and the full fee breakdown', () => {
    const text = fixture.nativeElement.textContent;
    expect(service.getCases).toHaveBeenCalled();
    expect(text).toContain('BC-LOST');
    expect(text).toContain('550,000');
    expect(text).toContain('Còn nợ');
  });

  it('submits an outstanding case for external waiver review', () => {
    component.submitReview(9);
    expect(service.submitReview).toHaveBeenCalledWith(9);
  });

  it('drops previously entered fees when a lost book must be replaced', () => {
    component.caseMssv = '2001240001';
    component.lookupCaseReader();
    component.caseForm = { ...component.caseForm, loanDetailId: 81, description: 'Mất sách',
      stillInMarket: true, settlementMethod: 'CASH', coverPrice: -10000, repairFee: -10000 };

    component.createCase();

    const request = service.createCase.mock.lastCall?.[0];
    expect(request).toMatchObject({ loanDetailId: 81, settlementMethod: 'REPLACEMENT' });
    expect(request).not.toHaveProperty('coverPrice');
    expect(request).not.toHaveProperty('repairFee');
  });

  it('highlights the exact penalty case referenced by a notification', () => {
    (component as any).route = {
      snapshot: { queryParamMap: { get: (key: string) => key === 'referenceType' ? 'PENALTY_CASE' : key === 'referenceId' ? '9' : null } },
    };

    component.ngOnInit();
    fixture.detectChanges();

    expect(component.highlightedCaseId).toBe(9);
    expect(fixture.nativeElement.querySelector('#penalty-case-9')?.classList).toContain('highlighted-row');
  });

  it('opens a report read-only and prepares the exact owner and item without creating a case', () => {
    (component as any).route = { snapshot: { queryParamMap: { get: (key: string) => key === 'referenceType' ? 'LOAN_LOSS_REPORT' : key === 'referenceId' ? '81' : null } } };
    component.ngOnInit(); fixture.changeDetectorRef.markForCheck(); fixture.detectChanges();
    expect(circulation.getLossReport).toHaveBeenCalledWith(81);
    expect(fixture.nativeElement.textContent).toContain('Mất trên xe buýt');
    expect(fixture.nativeElement.textContent).toContain('Sinh viên A');
    expect(service.createCase).not.toHaveBeenCalled();
    component.prepareLossReport();
    expect(users.getReaderProfile).toHaveBeenLastCalledWith('2001240001');
    expect(component.caseForm.loanDetailId).toBe(81);
    expect(component.caseForm.description).toBe('Mất trên xe buýt');
    expect(component.caseForm.incidentType).toBe('LOST');
    expect(service.createCase).not.toHaveBeenCalled();
  });

  it('rejects a report handoff when the current profile does not match its owner', () => {
    component.lossReportId = 81; component.loadLossReport();
    users.getReaderProfile.mockReturnValueOnce(of({ ...profile, student: { ...profile.student, userId: 'another-reader' } }));
    component.prepareLossReport();
    expect(component.caseForm.loanDetailId).toBe(0);
    expect(component.caseLookupError).toContain('không còn khớp');
    expect(service.createCase).not.toHaveBeenCalled();
  });

  it('links an already processed report to the existing case rather than preparing a second one', () => {
    circulation.getLossReport.mockReturnValueOnce(of({ ...lossReport, ticketId: 5, items: [{ ...lossReport.items[0], detailId: 6, ticketId: 5, itemStatus: 'LOST' }] }));
    component.lossReportId = 6; component.loadLossReport();
    expect(component.canPrepareLossReport).toBe(false); expect(component.lossReportCase?.caseId).toBe(9);
    component.prepareLossReport(); expect(users.getReaderProfile).not.toHaveBeenCalled();
    expect(service.createCase).not.toHaveBeenCalled();
    component.openLossReportCase(); expect(component.highlightedCaseId).toBe(9);
  });

  it('looks up MSSV and submits only a selected active loan item',()=>{
    component.tab = 'create';
    component.caseMssv=' 2001240001 ';component.lookupCaseReader();
    expect(users.getReaderProfile).toHaveBeenLastCalledWith('2001240001');
    expect(component.caseItems.map(i=>i.detailId)).toEqual([81]);
    component.caseForm.description='Bị mất';component.caseForm.loanDetailId=999;component.createCase();
    expect(service.createCase).not.toHaveBeenCalled();
    component.caseForm.loanDetailId=81;component.createCase();
    expect(service.createCase).toHaveBeenCalledWith(expect.objectContaining({loanDetailId:81,description:'Bị mất'}));
    fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('MSSV người mượn');
    expect(fixture.nativeElement.querySelector('input[name="detailId"]')).toBeNull();
  });
  it('rejects stale profiles and clears an old selection when MSSV changes',()=>{
    const pending=new Subject<any>();users.getReaderProfile.mockReturnValueOnce(pending);
    component.caseMssv='OLD';component.lookupCaseReader();component.caseMssv='NEW';component.resetCaseReader();
    pending.next(profile);pending.complete();
    expect(component.caseReader).toBeUndefined();expect(component.caseItems).toEqual([]);expect(component.caseForm.loanDetailId).toBe(0);
  });
  it('never offers a returned item or a ticket belonging to another reader',()=>{
    circulation.getLoan.mockReturnValueOnce(of({userId:'someone-else',items:[{detailId:81}]} as any));
    component.caseMssv='2001240001';component.lookupCaseReader();expect(component.caseItems).toEqual([]);
    circulation.getLoan.mockReturnValueOnce(of({userId:'reader-1',items:[{detailId:81,itemStatus:'RETURNED',returnDate:'2026-11-01'}]} as any));
    component.lookupCaseReader();expect(component.caseItems).toEqual([]);expect(component.caseLookupLoading).toBe(false);
  });
  it('reports a lookup error and resolves payment identity through MSSV',()=>{
    users.getReaderProfile.mockReturnValueOnce(throwError(()=>new Error('offline')));
    component.caseMssv='BAD';component.lookupCaseReader();expect(component.caseLookupLoading).toBe(false);expect(component.caseLookupError).toBeTruthy();
    component.paymentForm.userId='unverified';component.paymentForm.amount=100;component.pay();expect(service.recordPayment).not.toHaveBeenCalled();
    component.paymentMssv='2001240001';component.lookupPaymentReader();component.pay();
    expect(service.recordPayment).toHaveBeenCalledWith(expect.objectContaining({userId:'reader-1',amount:10000}));
  });

});
