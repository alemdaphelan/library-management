import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { DigitalAssetsService } from './digital-assets.service';

describe('DigitalAssetsService', () => {
  let service: DigitalAssetsService;
  let http: HttpTestingController;
  const url = `${environment.apiUrl}/digital-assets`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [DigitalAssetsService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(DigitalAssetsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uploads file with book and access metadata', () => {
    service.uploadAsset(new File(['%PDF-test'], 'lecture.pdf'), 9, 'STAFF_ONLY').subscribe();

    const request = http.expectOne(`${url}/upload`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.get('bookId')).toBe('9');
    expect(request.request.body.get('accessLevel')).toBe('STAFF_ONLY');
    request.flush({ id: 1 });
  });

  it('uses real endpoints for metadata update and binary download', () => {
    service.updateAsset(4, { displayName: 'Giáo trình', bookId: 2, accessLevel: 'AUTHENTICATED' }).subscribe();
    service.downloadAsset(4).subscribe();

    const update = http.expectOne(`${url}/4`);
    expect(update.request.method).toBe('PUT');
    expect(update.request.body.bookId).toBe(2);
    update.flush({});
    const download = http.expectOne(`${url}/4/download`);
    expect(download.request.method).toBe('GET');
    expect(download.request.responseType).toBe('blob');
    download.flush(new Blob(['file']));
  });
});
