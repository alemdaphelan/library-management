import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { PenaltyService } from './penalty.service';

describe('PenaltyService', () => {
  let service: PenaltyService;
  let http: HttpTestingController;
  const penaltyUrl = `${environment.apiUrl}/api/v1/penalty-cases`;
  const transactionUrl = `${environment.apiUrl}/api/v1/transactions`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PenaltyService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PenaltyService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads and creates penalty cases through week 9 endpoints', () => {
    service.getCases().subscribe();
    service.createCase({
      loanDetailId: 3, incidentType: 'LOST', description: 'Mất sách',
      materialType: 'BOOK', languageCategory: 'VIETNAMESE', stillInMarket: true,
      settlementMethod: 'REPLACEMENT',
    }).subscribe();

    const list = http.expectOne(request => request.url === penaltyUrl && request.method === 'GET');
    expect(list.request.method).toBe('GET');
    list.flush([]);
    const create = http.expectOne(request => request.url === penaltyUrl && request.method === 'POST');
    expect(create.request.method).toBe('POST');
    expect(create.request.body.loanDetailId).toBe(3);
    create.flush({});
  });

  it('records payment with idempotency and accepts a replacement barcode', () => {
    service.recordPayment({ userId: 'user-1', amount: 50000, method: 'CASH', idempotencyKey: 'key-1' }).subscribe();
    service.acceptReplacement(7, 'BC-NEW', 'Đã kiểm tra đúng tài liệu').subscribe();

    const payment = http.expectOne(`${transactionUrl}/payments`);
    expect(payment.request.method).toBe('POST');
    expect(payment.request.body.idempotencyKey).toBe('key-1');
    payment.flush({});
    const replacement = http.expectOne(`${penaltyUrl}/7/replacement-acceptance`);
    expect(replacement.request.method).toBe('PATCH');
    expect(replacement.request.body.replacementBarcode).toBe('BC-NEW');
    replacement.flush({});
  });
});
