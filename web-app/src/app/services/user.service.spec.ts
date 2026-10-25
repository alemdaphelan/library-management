import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { UserService } from './user.service';

describe('UserService', () => {
  let service: UserService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [UserService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UserService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the complete staff reader profile by MSSV', () => {
    service.getReaderProfile('2001/23').subscribe();

    const request = http.expectOne(`${environment.apiUrl}/users/students/2001%2F23/profile`);
    expect(request.request.method).toBe('GET');
    request.flush({ student: { mssv: '2001/23' }, card: {}, loans: [], ledger: [], penaltyCases: [] });
  });
});
