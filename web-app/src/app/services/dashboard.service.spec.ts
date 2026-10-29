import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { DashboardService } from './dashboard.service';

describe('DashboardService', () => {
  let service: DashboardService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [DashboardService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DashboardService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads summary statistics from the dashboard API', () => {
    service.getStats().subscribe();

    const request = httpTesting.expectOne(`${environment.apiUrl}/dashboard/stats`);
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('loads all dashboard data sources from real endpoints', () => {
    service.getWeeklyStats().subscribe();
    service.getRecentActivities().subscribe();
    service.getTrendingBooks().subscribe();

    for (const path of ['weekly-stats', 'recent-activities', 'trending-books']) {
      const request = httpTesting.expectOne(`${environment.apiUrl}/dashboard/${path}`);
      expect(request.request.method).toBe('GET');
      request.flush([]);
    }
  });
});
