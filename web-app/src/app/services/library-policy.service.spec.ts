import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { LibraryPolicy, LibraryPolicyService } from './library-policy.service';
import { environment } from '../../environments/environment';

describe('LibraryPolicyService', () => {
  let service: LibraryPolicyService;
  let http: HttpTestingController;
  const policy: LibraryPolicy = {
    maximumOpenItems: 3,
    homeLoanDays: 10,
    renewalDays: 10,
    maximumRenewalCount: 1,
    waitlistHoldHours: 48,
    homeLateFinePerDay: 1000,
    onsiteLateFinePerDay: 5000,
    technicalProcessingFee: 50000,
    compensationSettlementDays: 30,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(LibraryPolicyService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the active policy', () => {
    service.getPolicy().subscribe(value => expect(value.maximumOpenItems).toBe(3));
    const request = http.expectOne(`${environment.apiUrl}/admin/library-policy`);
    expect(request.request.method).toBe('GET');
    request.flush(policy);
  });

  it('loads the public policy without an admin endpoint', () => {
    service.getPublicPolicy().subscribe(value => expect(value.maximumRenewalCount).toBe(1));
    const request = http.expectOne(`${environment.apiUrl}/library-policy`);
    expect(request.request.method).toBe('GET');
    request.flush(policy);
  });

  it('updates the active policy', () => {
    service.updatePolicy(policy).subscribe(value => expect(value.homeLoanDays).toBe(10));
    const request = http.expectOne(`${environment.apiUrl}/admin/library-policy`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(policy);
    request.flush(policy);
  });

  it('loads policy change history from the admin endpoint', () => {
    service.getHistory().subscribe(value => expect(value[0].changedByName).toBe('Admin Thư viện'));
    const request = http.expectOne(`${environment.apiUrl}/admin/library-policy/history`);
    expect(request.request.method).toBe('GET');
    request.flush([{ ...policy, historyId: 1, changedByName: 'Admin Thư viện', changedAt: '2026-10-29T08:00:00' }]);
  });
});
