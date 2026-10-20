import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { InventoryAuditService } from './inventory-audit.service';

describe('InventoryAuditService', () => {
  let service: InventoryAuditService;
  let httpTesting: HttpTestingController;
  const apiUrl = `${environment.apiUrl}/inventory/audits`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [InventoryAuditService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(InventoryAuditService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads audit history and one audit detail', () => {
    service.getAudits().subscribe();
    service.getAudit(9).subscribe();

    const history = httpTesting.expectOne(apiUrl);
    expect(history.request.method).toBe('GET');
    history.flush([]);
    const detail = httpTesting.expectOne(`${apiUrl}/9`);
    expect(detail.request.method).toBe('GET');
    detail.flush({});
  });

  it('creates and completes an audit through explicit workflow endpoints', () => {
    service.createAudit().subscribe();
    service.complete(9).subscribe();

    const create = httpTesting.expectOne(apiUrl);
    expect(create.request.method).toBe('POST');
    create.flush({});
    const complete = httpTesting.expectOne(`${apiUrl}/9/complete`);
    expect(complete.request.method).toBe('POST');
    complete.flush({});
  });

  it('submits barcode and scanned location', () => {
    service.scan(9, 'BC-001', 'Tầng 1 / Dãy A / Ngăn 01').subscribe();

    const request = httpTesting.expectOne(`${apiUrl}/9/scans`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      barcode: 'BC-001',
      scannedLocation: 'Tầng 1 / Dãy A / Ngăn 01',
    });
    request.flush({});
  });
});
