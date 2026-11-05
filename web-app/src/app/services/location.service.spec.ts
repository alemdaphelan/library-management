import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { LocationService } from './location.service';

describe('LocationService', () => {
  let service: LocationService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [LocationService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(LocationService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads floors from the location contract', () => {
    service.getFloors().subscribe();

    const request = httpTesting.expectOne(`${environment.apiUrl}/locations/floors`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('loads bookshelves for the selected floor', () => {
    service.getBookshelves(7).subscribe();

    const request = httpTesting.expectOne(
      request => request.url === `${environment.apiUrl}/locations/bookshelves`
        && request.params.get('floorId') === '7'
    );
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('loads shelves for the selected bookshelf', () => {
    service.getShelves(9).subscribe();

    const request = httpTesting.expectOne(
      request => request.url === `${environment.apiUrl}/locations/shelves`
        && request.params.get('bookshelfId') === '9'
    );
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('creates a shelf with the selected bookshelf as parent', () => {
    service.createShelf('Ngăn 01', 9).subscribe();

    const request = httpTesting.expectOne(`${environment.apiUrl}/locations/shelves`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Ngăn 01', parentId: 9 });
    request.flush({ id: 11, name: 'Ngăn 01', type: 'SHELF', parentId: 9 });
  });

  it('loads and updates subject rules for a shelf', () => {
    service.getAvailableSubjects().subscribe();
    const subjectsRequest = httpTesting.expectOne(`${environment.apiUrl}/locations/subjects`);
    expect(subjectsRequest.request.method).toBe('GET');
    subjectsRequest.flush([]);

    service.getShelfSubjects(9).subscribe();
    const rulesRequest = httpTesting.expectOne(`${environment.apiUrl}/locations/shelves/9/subjects`);
    expect(rulesRequest.request.method).toBe('GET');
    rulesRequest.flush([]);

    service.updateShelfSubjects(9, [2, 5]).subscribe();
    const updateRequest = httpTesting.expectOne(`${environment.apiUrl}/locations/shelves/9/subjects`);
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body).toEqual({ subjectIds: [2, 5] });
    updateRequest.flush([]);
  });
  it('uses the paged shelf and unplaced read contracts and preserves unmatched identifiers',()=>{
    service.getShelfBooksPage(3,2).subscribe(page=>expect(page.items[0].bookId).toBeNull());
    const page=httpTesting.expectOne(r=>r.url.endsWith('/shelves/3/books/page'));
    expect(page.request.method).toBe('GET');expect(page.request.params.get('page')).toBe('2');expect(page.request.params.get('size')).toBe('50');
    page.flush({items:[{barcode:'NO-ID',bookId:null,catalogMatched:false,title:'Chưa đối chiếu đầu sách',status:'AVAILABLE'}],total:101,page:2,size:50});
    service.getUnplacedCopies(1).subscribe();const free=httpTesting.expectOne(r=>r.url.endsWith('/unplaced-copies'));
    expect(free.request.method).toBe('GET');expect(free.request.params.get('page')).toBe('1');expect(free.request.params.get('size')).toBe('50');free.flush({items:[],total:0,page:1,size:50});
  });
});
