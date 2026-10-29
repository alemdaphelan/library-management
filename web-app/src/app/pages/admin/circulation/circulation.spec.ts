import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { provideRouter } from '@angular/router';
import { CirculationService, LoanTicketDetail } from '../../../services/circulation.service';
import { Circulation } from './circulation';

describe('Circulation counter', () => {
  let component: Circulation;
  let fixture: ComponentFixture<Circulation>;

  const reservedTicket: LoanTicketDetail = {
    ticketId: 19,
    userId: 'user-19',
    mssv: '2001190019',
    readerName: 'Nguyễn Văn A',
    ticketType: 'BORROW',
    status: 'RESERVED',
    items: [{
      ticketId: 19,
      detailId: 29,
      bookId: 9,
      bookTitle: 'Clean Code',
      author: 'Robert C. Martin',
      barcode: 'BC-019',
      ticketType: 'BORROW',
      status: 'RESERVED',
      renewalCount: 0,
      lateFineAccrued: 0,
    }],
  };
  const operation = {
    operationId: 'operation-1',
    operationType: 'CHECKOUT' as const,
    ticketIds: [19],
    barcodes: ['BC-019'],
    status: 'BORROWING',
    message: 'Checkout thành công 1 tài liệu',
    processedAt: '2026-10-18T08:00:00',
    replayed: false,
  };
  const service = {
    getStaffLoans: vi.fn(() => of([])),
    approveReservation: vi.fn(() => of({ ticketId: 1, status: 'RESERVED', message: 'Đã duyệt' })),
    rejectReservation: vi.fn(() => of({ ticketId: 1, status: 'REJECTED', message: 'Đã từ chối' })),
    getLoan: vi.fn(() => of(reservedTicket)),
    getBookCopyByBarcode: vi.fn(() => of({ barcode: 'BC-019', bookId: 9, status: 'RESERVED', location: 'Tầng 2 / Kệ A' })),
    getActiveLoanItemByBarcode: vi.fn(() => of(reservedTicket.items[0])),
    checkout: vi.fn(() => of(operation)),
    manualCheckout: vi.fn(() => of(operation)),
    returnBooks: vi.fn(() => of({ ...operation, operationType: 'RETURN' as const, status: 'RETURNED', message: 'Trả thành công 1 tài liệu' })),
    resolveQrPayload: vi.fn(() => of({ type: 'LOAN_TICKET', ticketId: 19, allowedActions: ['CHECKOUT'], items: [] })),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    service.getLoan.mockReturnValue(of(reservedTicket));
    service.approveReservation.mockReturnValue(of({ ticketId: 19, status: 'RESERVED', message: 'Đã giữ chỗ' }));
    service.getBookCopyByBarcode.mockReturnValue(of({ barcode: 'BC-019', bookId: 9, status: 'RESERVED', location: 'Tầng 2 / Kệ A' }));
    service.getActiveLoanItemByBarcode.mockReturnValue(of(reservedTicket.items[0]));
    await TestBed.configureTestingModule({
      imports: [Circulation],
      providers: [provideRouter([]), { provide: CirculationService, useValue: service }],
    }).compileComponents();

    fixture = TestBed.createComponent(Circulation);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('resolves the existing QR payload to the real ticket id', () => {
    component.ticketInput = 'HUIT-PM-19';
    component.resolveTicket();

    expect(service.getLoan).toHaveBeenCalledWith(19);
    expect(component.selectedTicket?.ticketId).toBe(19);
  });

  it('loads the exact ticket referenced by an admin notification', () => {
    (component as any).route = {
      snapshot: {
        queryParamMap: {
          get: (key: string) => key === 'referenceType' ? 'LOAN_TICKET' : key === 'referenceId' ? '19' : null,
        },
      },
    };

    component.ngOnInit();

    expect(service.getLoan).toHaveBeenCalledWith(19);
    expect(component.selectedTicket?.ticketId).toBe(19);
  });

  it('checks an expected barcode and submits checkout with ticketId', () => {
    component.ticketInput = '19';
    component.resolveTicket();
    component.barcodeInput = 'BC-019';
    component.scanBookBarcode();

    expect(service.getBookCopyByBarcode).toHaveBeenCalledWith('BC-019');
    expect(component.canSubmit).toBe(true);

    component.completeTransaction();

    expect(service.checkout).toHaveBeenCalledWith(19, ['BC-019']);
    expect(component.successMessage).toBe('');
  });

  it('rejects a barcode outside the selected ticket without creating fallback data', () => {
    component.ticketInput = '19';
    component.resolveTicket();
    component.barcodeInput = 'BC-OTHER';
    component.scanBookBarcode();

    expect(service.getBookCopyByBarcode).not.toHaveBeenCalled();
    expect(component.scannedBooks).toHaveLength(0);
    expect(component.errorMessage).toContain('không thuộc phiếu #19');
  });

  it('stages a scanned return for inspection without mutating circulation', () => {
    component.switchTab('return');
    service.getActiveLoanItemByBarcode.mockReturnValue(of({ ...reservedTicket.items[0], status: 'BORROWING', itemStatus: 'BORROWING' }));
    component.barcodeInput = 'BC-019';
    component.scanBookBarcode();
    expect(component.returnDesk?.drafts).toHaveLength(1);
    expect(service.returnBooks).not.toHaveBeenCalled();
    component.returnDesk?.cancelDraft();
    expect(component.returnDesk?.drafts).toHaveLength(0);
    expect(service.returnBooks).not.toHaveBeenCalled();
  });

  it('forwards a camera return barcode to the inspection form', () => {
    component.switchTab('return');
    service.getActiveLoanItemByBarcode.mockReturnValue(of({ ...reservedTicket.items[0], status: 'BORROWING', itemStatus: 'BORROWING' }));
    (component as any).handleScannedValue('barcode', 'BC-019');
    expect(service.getActiveLoanItemByBarcode).toHaveBeenCalledWith('BC-019');
    expect(component.returnDesk?.drafts[0].loan.detailId).toBe(29);
    expect(service.returnBooks).not.toHaveBeenCalled();
  });

  it('preserves an unsent inspection draft when switching tabs', () => {
    component.switchTab('return');
    service.getActiveLoanItemByBarcode.mockReturnValue(of({ ...reservedTicket.items[0], status: 'BORROWING', itemStatus: 'BORROWING' }));
    component.returnDesk?.addBarcode('BC-019');
    component.switchTab('borrow');
    component.switchTab('return');
    expect(component.returnDesk?.drafts).toHaveLength(1);
  });

  it('creates and checks out a walk-in loan by MSSV', () => {
    component.switchBorrowMode('manual');
    component.manualMssv = '2001190019';
    component.manualTicketType = 'ONSITE';
    component.manualNote = 'Sách nguyên vẹn';
    service.getBookCopyByBarcode.mockReturnValue(of({ barcode: 'BC-019', bookId: 9, status: 'AVAILABLE', location: '' }));
    component.barcodeInput = 'BC-019';
    component.scanBookBarcode();

    expect(component.canSubmit).toBe(true);
    component.completeTransaction();

    expect(service.manualCheckout).toHaveBeenCalledWith(
      '2001190019',
      'ONSITE',
      ['BC-019'],
      'Sách nguyên vẹn',
    );
  });

  it('resolves a signed ticket QR before loading the approved ticket', () => {
    (component as any).handleScannedValue('ticket', 'HUITQR1.claims.signature');

    expect(service.resolveQrPayload).toHaveBeenCalledWith('HUITQR1.claims.signature');
    expect(service.getLoan).toHaveBeenCalledWith(19);
    expect(component.selectedTicket?.ticketId).toBe(19);
  });

  it('does not offer an approval queue or approve newly reserved tickets', () => {
    component.ticketInput = '19';
    component.resolveTicket();
    fixture.detectChanges();
    expect(service.approveReservation).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).not.toContain('CHỜ DUYỆT');
    expect(fixture.nativeElement.textContent).not.toContain('Duyệt yêu cầu');
    expect(fixture.nativeElement.textContent).toContain('Phiếu trực tuyến đã giữ chỗ');
  });

  it('automatically revalidates a legacy pending ticket before accepting it at the counter', () => {
    service.getLoan.mockReturnValueOnce(of({ ...reservedTicket, status: 'PENDING' }));
    component.ticketInput = '19';
    component.resolveTicket();
    expect(service.approveReservation).toHaveBeenCalledWith(19);
    expect(service.getLoan).toHaveBeenCalledTimes(2);
    expect(component.selectedTicket?.status).toBe('RESERVED');
    expect(component.canSubmit).toBe(false);
    expect(service.checkout).not.toHaveBeenCalled();
  });

  it('does not allow checkout when legacy validation fails', () => {
    service.getLoan.mockReturnValueOnce(of({ ...reservedTicket, status: 'PENDING' }));
    service.approveReservation.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Giữ chỗ đã hết hạn' } })));
    component.ticketInput = '19';
    component.resolveTicket();
    expect(component.selectedTicket).toBeNull();
    expect(component.isResolvingTicket).toBe(false);
    expect(component.canSubmit).toBe(false);
    expect(component.errorMessage).toContain('hết hạn');
    expect(service.checkout).not.toHaveBeenCalled();
  });
});
