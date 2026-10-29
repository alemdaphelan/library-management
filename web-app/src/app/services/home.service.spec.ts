import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';

import { HomeService, RecommendationResponse } from './home.service';

const response: RecommendationResponse = {
  method: 'POPULARITY_BASELINE',
  dataSufficient: true,
  candidateCount: 30,
  generatedAt: '2026-10-19T10:00:00',
  items: []
};

describe('HomeService', () => {
  let service: HomeService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        HomeService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(HomeService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('uses the public popularity endpoint for anonymous visitors', () => {
    service.getRecommendedBooks(8).subscribe(result => {
      expect(result.method).toBe('POPULARITY_BASELINE');
    });

    const request = http.expectOne(req =>
      req.url.endsWith('/books/recommendations/popular') &&
      req.params.get('limit') === '8'
    );
    expect(request.request.method).toBe('GET');
    request.flush(response);
  });

  it('falls back to popularity when personalized recommendations fail', () => {
    localStorage.setItem('huit_lib_token', 'test-token');

    service.getRecommendedBooks(10).subscribe(result => {
      expect(result.method).toBe('POPULARITY_BASELINE');
    });

    const personalized = http.expectOne(req =>
      req.url.endsWith('/books/recommendations/personalized')
    );
    personalized.flush({ message: 'expired token' }, {
      status: 403,
      statusText: 'Forbidden'
    });

    const fallback = http.expectOne(req =>
      req.url.endsWith('/books/recommendations/popular')
    );
    fallback.flush(response);
  });
});
