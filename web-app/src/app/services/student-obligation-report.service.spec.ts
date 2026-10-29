import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { StudentObligationReportService } from './student-obligation-report.service';

describe('StudentObligationReportService', () => {
  let service: StudentObligationReportService;
  let http: HttpTestingController;
  const base = `${environment.apiUrl}/api/v1/librarian-reports/student-obligations`;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(StudentObligationReportService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('loads authoritative faculties and classification coverage from separate endpoints', () => {
    service.faculties().subscribe(); service.coverage().subscribe();
    http.expectOne(`${base}/faculties`).flush([{ facultyId: 'FIT', facultyName: 'Khoa CNTT' }]);
    http.expectOne(`${base}/classification-coverage`).flush({ totalStudents: 10, classifiedStudents: 10, complete: true });
  });
  it('passes the exact faculty/mode/query with server pagination and no loan-list date filters', () => {
    service.preview({ mode: 'DEBT', facultyId: 'FIT', q: ' Nguyễn %_ & A ' }, 2, 10).subscribe();
    const request = http.expectOne(req => req.url === base);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys().sort()).toEqual(['facultyId', 'mode', 'page', 'q', 'size']);
    expect(request.request.params.get('q')).toBe('Nguyễn %_ & A');
    expect(request.request.params.get('facultyId')).toBe('FIT'); expect(request.request.params.get('mode')).toBe('DEBT');
    expect(request.request.params.get('page')).toBe('2'); expect(request.request.params.get('size')).toBe('10');
    request.flush({});
  });
  it('omits facultyId for all faculties and preserves UNCLASSIFIED as an explicit diagnostic filter', () => {
    service.preview({ mode: 'OVERDUE', facultyId: '', q: '' }, 0, 25).subscribe();
    const all = http.expectOne(req => req.url === base);
    expect(all.request.params.has('facultyId')).toBe(false); all.flush({});
    service.preview({ mode: 'EITHER', facultyId: 'UNCLASSIFIED', q: '' }, 0, 25).subscribe();
    const missing = http.expectOne(req => req.url === base);
    expect(missing.request.params.get('facultyId')).toBe('UNCLASSIFIED'); missing.flush({});
  });
  it('requests a Blob with response headers and exports all filtered results without page or size', () => {
    const blob = new Blob(['fixture'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    service.export({ mode: 'EITHER', facultyId: 'FIT', q: '2001' }).subscribe(response => {
      expect(response.body).toBe(blob); expect(response.headers.get('Content-Disposition')).toContain('report.xlsx');
    });
    const request = http.expectOne(req => req.url === `${base}/export`);
    expect(request.request.responseType).toBe('blob'); expect(request.request.params.keys().sort()).toEqual(['facultyId', 'mode', 'q']);
    request.flush(blob, { headers: { 'Content-Disposition': 'attachment; filename="report.xlsx"' } });
  });
});
