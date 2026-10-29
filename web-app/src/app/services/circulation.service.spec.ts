import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { CirculationService } from './circulation.service';

describe('CirculationService', () => {
  let service: CirculationService;
  let httpTesting: HttpTestingController;
  const apiUrl = `${environment.apiUrl}/loans`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CirculationService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CirculationService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads a ticket detail for circulation review', () => {
    service.getLoan(19).subscribe();

    const request = httpTesting.expectOne(`${apiUrl}/19`);
    expect(request.request.method).toBe('GET');
    request.flush({ ticketId: 19, status: 'PENDING', items: [] });
  });

  it('loads the real staff loan list', () => {
    service.getStaffLoans().subscribe();

    const request = httpTesting.expectOne(`${apiUrl}/staff`);
    expect(request.request.method).toBe('GET');
    request.flush([{ ticketId: 19, status: 'OVERDUE', itemCount: 1, overdueItemCount: 1, totalLateFine: 3000 }]);
  });

  it('loads the current reader borrowing limit from the backend', () => {
    service.getMyBorrowEligibility().subscribe(response => {
      expect(response.remainingSlots).toBe(1);
    });

    const request = httpTesting.expectOne(`${apiUrl}/my-eligibility`);
    expect(request.request.method).toBe('GET');
    request.flush({ maximumItems: 3, openItems: 2, remainingSlots: 1, eligible: true, message: 'Còn 1' });
  });

  it('treats the plain-text cancellation response as a successful request', () => {
    service.cancelReservation(19).subscribe(response => {
      expect(response).toContain('thành công');
    });

    const request = httpTesting.expectOne(`${apiUrl}/cancel/19`);
    expect(request.request.method).toBe('POST');
    expect(request.request.responseType).toBe('text');
    request.flush('Đã hủy phiếu mượn/đặt giữ thành công');
  });

  it('creates one reservation ticket for all books in the borrow cart', () => {
    service.reserveBooks({
      bookIds: [1, 2, 3],
      ticketType: 'BORROW',
      pickupDate: '2026-10-24',
      note: 'Nhận tại quầy',
    }).subscribe();

    const request = httpTesting.expectOne(`${apiUrl}/reserve-batch`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      bookIds: [1, 2, 3],
      ticketType: 'BORROW',
      pickupDate: '2026-10-24',
      note: 'Nhận tại quầy',
    });
    request.flush({ ticketId: 25, status: 'PENDING', bookIds: [1, 2, 3] });
  });

  it('loads the active loan item so one return can include books from different tickets', () => {
    service.getActiveLoanItemByBarcode('BC/019').subscribe();

    const request = httpTesting.expectOne(`${apiUrl}/active-item/BC%2F019`);
    expect(request.request.method).toBe('GET');
    request.flush({ ticketId: 19, detailId: 29, barcode: 'BC/019', status: 'BORROWING' });
  });

  it('uses explicit approve and reject transition endpoints', () => {
    service.approveReservation(19).subscribe();
    service.rejectReservation(20, 'Thẻ thư viện hết hạn').subscribe();

    const approve = httpTesting.expectOne(`${apiUrl}/19/approve`);
    expect(approve.request.method).toBe('POST');
    expect(approve.request.body).toEqual({});
    approve.flush({});

    const reject = httpTesting.expectOne(`${apiUrl}/20/reject`);
    expect(reject.request.method).toBe('POST');
    expect(reject.request.body).toEqual({ reason: 'Thẻ thư viện hết hạn' });
    reject.flush({});
  });

  it('sends the fixed checkout and return contracts', () => {
    service.checkout(19, ['BC-001', 'BC-002'], 'checkout-19').subscribe();
    service.returnBooks(['BC-001'], 'return-19', 'Bìa sách bình thường').subscribe();

    const checkout = httpTesting.expectOne(`${apiUrl}/checkout`);
    expect(checkout.request.method).toBe('POST');
    expect(checkout.request.body).toEqual({
      idempotencyKey: 'checkout-19',
      ticketId: 19,
      barcodes: ['BC-001', 'BC-002'],
    });
    checkout.flush({});

    const returnRequest = httpTesting.expectOne(`${apiUrl}/return`);
    expect(returnRequest.request.method).toBe('POST');
    expect(returnRequest.request.body).toEqual({
      idempotencyKey: 'return-19',
      barcodes: ['BC-001'],
      returnConditionNote: 'Bìa sách bình thường',
    });
    returnRequest.flush({});
  });

  it('submits the complete inspection once with the caller-owned retry key', () => {
    const payload = {
      idempotencyKey: 'inspection-key',
      items: [{ loanDetailId: 29, condition: 'NORMAL' as const, returnConditionNote: 'Nguyên vẹn' }],
    };
    service.inspectReturn(payload).subscribe();
    const request = httpTesting.expectOne(`${apiUrl}/return-inspection`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    httpTesting.expectNone(`${apiUrl}/return`);
    request.flush({ operationId: 'receipt-1', items: [], tickets: [], balances: [] });
  });

  it('encodes a physical copy barcode before lookup', () => {
    service.getBookCopyByBarcode('BC/001').subscribe();

    const request = httpTesting.expectOne(`${environment.apiUrl}/book-copies/BC%2F001`);
    expect(request.request.method).toBe('GET');
    request.flush({ barcode: 'BC/001', bookId: 1, status: 'AVAILABLE' });
  });

  it('requests a signed, expiring QR payload from the backend', () => {
    service.getTicketQrPayload(19).subscribe(response => {
      expect(response.payload).toContain('HUITQR1.');
    });

    const request = httpTesting.expectOne(`${environment.apiUrl}/api/v1/qr/tickets/19`);
    expect(request.request.method).toBe('GET');
    request.flush({ payload: 'HUITQR1.claims.signature', expiresAt: '2026-10-18T08:10:00Z' });
  });

  it('creates a manual checkout for a walk-in reader', () => {
    service.manualCheckout(' 2001234567 ', 'ONSITE', ['BC-001'], 'Nguyên vẹn', 'manual-1').subscribe();

    const request = httpTesting.expectOne(`${apiUrl}/manual-checkout`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      idempotencyKey: 'manual-1',
      mssv: '2001234567',
      ticketType: 'ONSITE',
      barcodes: ['BC-001'],
      note: 'Nguyên vẹn',
    });
    request.flush({ operationId: 'operation-2', status: 'BORROWING' });
  });

  it('resolves a signed QR at the librarian counter', () => {
    service.resolveQrPayload('HUITQR1.claims.signature').subscribe();

    const request = httpTesting.expectOne(`${environment.apiUrl}/api/v1/qr/resolve`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ payload: 'HUITQR1.claims.signature' });
    request.flush({ type: 'LOAN_TICKET', ticketId: 19, allowedActions: ['CHECKOUT'], items: [] });
  });
});
