import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { ImportsService } from './imports.service';

describe('ImportsService', () => {
  let service: ImportsService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ImportsService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ImportsService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('uses the backend inventory contract for receipts', () => {
    service.getImportReceipts().subscribe();

    const request = httpTesting.expectOne(`${environment.apiUrl}/inventory/receipts`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('posts a receipt to the backend inventory contract', () => {
    const payload = {
      supplierId: 1,
      lines: [{ isbn: '9780132350884', quantity: 2, unitPrice: 150000 }],
    };

    service.saveImport(payload).subscribe();

    const request = httpTesting.expectOne(`${environment.apiUrl}/inventory/receipts`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({ receiptCode: 'PN-001' });
  });

  it('looks up an exact catalog item by ISBN before adding a receipt line', () => {
    service.findBookByIsbn('978 1/2').subscribe();

    const request = httpTesting.expectOne(`${environment.apiUrl}/books/isbn/978%201%2F2`);
    expect(request.request.method).toBe('GET');
    request.flush({ bookId: 3, title: 'Clean Code' });
  });
});
