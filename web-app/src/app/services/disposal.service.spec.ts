import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DisposalService } from './disposal.service';
describe('DisposalService', () => {
  let service: DisposalService, http: HttpTestingController;
  beforeEach(() => { TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }); service = TestBed.inject(DisposalService); http = TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());
  it('reads real audit candidates and server-filtered proposal pages', () => {
    service.candidates(9).subscribe(); const c = http.expectOne(r => r.url.endsWith('/disposals/candidates')); expect(c.request.params.get('auditId')).toBe('9'); c.flush([]);
    service.list({ status: 'DRAFT', auditId: '9', barcode: ' BC-1 ' }, 2, 25).subscribe();
    const l = http.expectOne(r => r.url.endsWith('/disposals')); expect(l.request.params.get('status')).toBe('DRAFT'); expect(l.request.params.get('barcode')).toBe('BC-1'); expect(l.request.params.get('page')).toBe('2'); l.flush({});
  });
  it('preserves the idempotency key, source fingerprint and per-copy form body', () => {
    const request = { requestId: 'id', auditId: 9, lines: [{ barcode: 'BC-1', sourceDetailId: 'scan', expectedFingerprint: 'a'.repeat(64), observedCondition: 'Rách', reasonCode: 'WORN' as const, reason: 'Cũ', proposedAction: 'REPAIR' as const, evidenceUrl: null }] };
    service.create(request).subscribe(); const r = http.expectOne(x => x.url.endsWith('/disposals')); expect(r.request.method).toBe('POST'); expect(r.request.body).toEqual(request); r.flush({});
  });
  it('uses the exact proposal/version for editing, submission and cancellation', () => {
    service.get('id').subscribe(); http.expectOne(r => r.url.endsWith('/disposals/id')).flush({});
    service.edit('id', { version: 2, expectedFingerprint: 'b'.repeat(64), observedCondition: 'Cũ', reasonCode: 'WORN', reason: 'Cũ', proposedAction: 'TRANSFER', evidenceUrl: null }).subscribe();
    const e = http.expectOne(r => r.url.endsWith('/disposals/id')); expect(e.request.method).toBe('PUT'); expect(e.request.body.version).toBe(2); e.flush({});
    service.submit('id', 3).subscribe(); const s = http.expectOne(r => r.url.endsWith('/id/submit')); expect(s.request.body).toEqual({ version: 3 }); s.flush({});
    service.cancel('id', 4).subscribe(); const c = http.expectOne(r => r.url.endsWith('/id/cancel')); expect(c.request.body).toEqual({ version: 4 }); c.flush({});
  });
});
