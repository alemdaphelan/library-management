import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
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
    getLoan: vi.fn(() => of(reservedTicket)),
    getBookCopyByBarcode: vi.fn(() => of({ barcode: 'BC-019', bookId: 9, status: 'RESERVED', location: 'Tầng 2 / Kệ A' })),
    checkout: vi.fn(() => of(operation)),
    returnBooks: vi.fn(() => of({ ...operation, operationType: 'RETURN' as const, status: 'RETURNED', message: 'Trả thành công 1 tài liệu' })),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    service.getBookCopyByBarcode.mockReturnValue(of({ barcode: 'BC-019', bookId: 9, status: 'RESERVED', location: 'Tầng 2 / Kệ A' }));
    await TestBed.configureTestingModule({
      imports: [Circulation],
      providers: [{ provide: CirculationService, useValue: service }],
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

  it('checks an expected barcode and submits checkout with ticketId', () => {
    component.ticketInput = '19';
    component.resolveTicket();
    component.barcodeInput = 'BC-019';
    component.scanBookBarcode();

    expect(service.getBookCopyByBarcode).toHaveBeenCalledWith('BC-019');
    expect(component.canSubmit).toBe(true);

    component.completeTransaction();

    expect(service.checkout).toHaveBeenCalledWith(19, ['BC-019']);
    expect(component.successMessage).toContain('Checkout thành công');
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

  it('submits a real return with its condition note', () => {
    component.switchTab('return');
    service.getBookCopyByBarcode.mockReturnValue(of({ barcode: 'BC-019', bookId: 9, status: 'BORROWING', location: '' }));
    component.barcodeInput = 'BC-019';
    component.scanBookBarcode();
    component.returnConditionNote = 'Bìa sách nguyên vẹn';
    component.completeTransaction();

    expect(service.returnBooks).toHaveBeenCalledWith(
      ['BC-019'],
      expect.any(String),
      'Bìa sách nguyên vẹn',
    );
    expect(component.successMessage).toContain('Trả thành công');
  });
});
