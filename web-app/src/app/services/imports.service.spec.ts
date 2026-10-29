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
      lines: [{ bookId: 3, isbn: '9780132350884', quantity: 2, unitPrice: 150000, shelfId: 15, floorId: 1, bookshelfId: 2 }],
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
  it('loads persisted receipt lines and identifies metadata without sending a shelf guess', () => {
    service.getReceipt(10).subscribe(); service.identify('isbn', ' 0-13-235088-2 ').subscribe(); service.identify('bookId', ' 3 ').subscribe();
    httpTesting.expectOne(`${environment.apiUrl}/inventory/receipts/10`).flush({ receiptId: 10, lines: [] });
    const isbn = httpTesting.expectOne(request => request.url.endsWith('/inventory/catalog-identification') && request.params.has('isbn'));
    expect(isbn.request.params.get('isbn')).toBe('0-13-235088-2'); expect(isbn.request.params.has('shelfId')).toBe(false); isbn.flush({});
    const book = httpTesting.expectOne(request => request.url.endsWith('/inventory/catalog-identification') && request.params.has('bookId'));
    expect(book.request.params.get('bookId')).toBe('3'); book.flush({});
  });
});
