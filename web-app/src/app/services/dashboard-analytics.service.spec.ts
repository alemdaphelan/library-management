import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DashboardAnalyticsService } from './dashboard-analytics.service';
import { AnalyticsRange, Material } from './dashboard-analytics.models';
describe('DashboardAnalyticsService',()=>{
  let api:DashboardAnalyticsService;let http:HttpTestingController;
  const r:AnalyticsRange={from:'2026-01-01',until:'2026-04-01',period:'QUARTER'};
  beforeEach(()=>{TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]});api=TestBed.inject(DashboardAnalyticsService);http=TestBed.inject(HttpTestingController);});
  afterEach(()=>http.verify());
  it('sends half-open dates/period and server paging; overdue has no date filter',()=>{
    api.rankings(r,'NEVER_RECORDED',1).subscribe();let q=http.expectOne(req=>req.url.endsWith('/rankings'));expect(q.request.params.get('until')).toBe('2026-04-01');expect(q.request.params.get('page')).toBe('1');q.flush({});
    api.overdue(2).subscribe();q=http.expectOne(req=>req.url.endsWith('/overdue'));expect(q.request.params.has('from')).toBe(false);expect(q.request.params.get('page')).toBe('2');q.flush({});
  });
  it('uses the same literal search/faculty filter for totals and paged list',()=>{
    const filter={facultyId:'FIT',majorId:'',type:'ALL' as const,state:'ALL' as const,q:'C++ 100%'};
    api.holdings(filter).subscribe();api.materials(filter,1).subscribe();
    const requests=http.match(req=>req.url.endsWith('/holdings')||req.url.endsWith('/materials'));expect(requests).toHaveLength(2);
    for(const q of requests){expect(q.request.method).toBe('GET');expect(q.request.params.get('q')).toBe('C++ 100%');expect(q.request.params.has('majorId')).toBe(false);q.flush({});}
  });
  it('uses canonical material/receipt paths and read-only finance drill-down',()=>{
    api.assets({kind:'DIGITAL',id:4} as Material,1).subscribe();let q=http.expectOne(req=>req.url.endsWith('/materials/DIGITAL/4/digital-assets'));expect(q.request.params.get('page')).toBe('1');q.flush({});
    api.entries(r,'ADJUSTMENT',2).subscribe();q=http.expectOne(req=>req.url.endsWith('/finance/entries'));expect(q.request.params.get('kind')).toBe('ADJUSTMENT');expect(q.request.params.get('page')).toBe('2');q.flush({});
    api.allocations(9,0).subscribe();http.expectOne(req=>req.url.endsWith('/finance/payments/9/allocations')).flush({});
  });
});
