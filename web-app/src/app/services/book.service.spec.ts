import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { BookService } from './book.service';

describe('BookService interactions', () => {
  let service: BookService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [BookService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BookService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads and saves real book reviews', () => {
    service.getReviews(7).subscribe();
    service.saveReview(7, 5, 'Hữu ích').subscribe();

    const requests = http.match(`${environment.apiUrl}/books/7/reviews`);
    expect(requests).toHaveLength(2);
    const getRequest = requests.find(request => request.request.method === 'GET')!;
    expect(getRequest.request.method).toBe('GET');
    getRequest.flush({ bookId: 7, averageRating: 0, reviewCount: 0, reviews: [] });

    const saveRequest = requests.find(request => request.request.method === 'POST')!;
    expect(saveRequest.request.method).toBe('POST');
    expect(saveRequest.request.body).toEqual({ rating: 5, comment: 'Hữu ích' });
    saveRequest.flush({ bookId: 7, averageRating: 5, reviewCount: 1, reviews: [] });
  });

  it('uses authenticated wishlist endpoints', () => {
    service.getWishlist().subscribe();
    service.addToWishlist(9).subscribe();
    service.removeFromWishlist(9).subscribe();

    const requests = http.match(`${environment.apiUrl}/users/wishlist`);
    expect(requests).toHaveLength(2);
    const listRequest = requests.find(request => request.request.method === 'GET')!;
    expect(listRequest.request.method).toBe('GET');
    listRequest.flush([]);

    const addRequest = requests.find(request => request.request.method === 'POST')!;
    expect(addRequest.request.method).toBe('POST');
    expect(addRequest.request.body).toEqual({ bookId: 9 });
    addRequest.flush({ bookId: 9 });

    const deleteRequest = http.expectOne(`${environment.apiUrl}/users/wishlist/9`);
    expect(deleteRequest.request.method).toBe('DELETE');
    deleteRequest.flush(null);
  });
});
