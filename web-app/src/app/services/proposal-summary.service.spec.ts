import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProposalSummaryService } from './proposal-summary.service';

describe('ProposalSummaryService', () => {
  let service: ProposalSummaryService, http: HttpTestingController;
  beforeEach(() => { TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }); service = TestBed.inject(ProposalSummaryService); http = TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());
  it('loads group pages with the complete filter and exposes the faculty endpoint', () => {
    service.preview({ facultyId: 'FIT', from: '2026-10-01', to: '2026-10-29', status: 'PENDING' }, 2, 10).subscribe();
    const req = http.expectOne(r => r.url.endsWith('/proposals/admin/summary'));
    expect(req.request.params.get('facultyId')).toBe('FIT'); expect(req.request.params.get('from')).toBe('2026-10-01');
    expect(req.request.params.get('to')).toBe('2026-10-29'); expect(req.request.params.get('status')).toBe('PENDING');
    expect(req.request.params.get('page')).toBe('2'); expect(req.request.params.get('size')).toBe('10'); req.flush({});
    service.faculties().subscribe(); http.expectOne(r => r.url.endsWith('/summary/faculties')).flush([]);
  });
  it('exports a Blob and headers without any page limit or blank optional filters', () => {
    service.export({ facultyId: '', from: '', to: '', status: 'ALL' }).subscribe();
    const req = http.expectOne(r => r.url.endsWith('/summary/export'));
    expect(req.request.responseType).toBe('blob'); expect(req.request.params.keys()).toEqual(['status']);
    req.flush(new Blob(['xlsx']));
  });
});
