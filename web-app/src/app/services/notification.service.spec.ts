import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;
  let httpTesting: HttpTestingController;
  const apiUrl = `${environment.apiUrl}/api/v1/notifications`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [NotificationService, provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(NotificationService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads notifications of the authenticated user', () => {
    service.getMyNotifications().subscribe();
    const request = httpTesting.expectOne(apiUrl);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('marks one owned notification as read', () => {
    service.markAsRead('notif-1').subscribe();
    const request = httpTesting.expectOne(`${apiUrl}/notif-1/read`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({});
    request.flush({ notifId: 'notif-1', isRead: true });
  });

  it('loads and persists notification delivery preferences', () => {
    service.getPreferences().subscribe();
    const getRequest = httpTesting.expectOne(`${apiUrl}/preferences`);
    expect(getRequest.request.method).toBe('GET');
    getRequest.flush({ emailEnabled: true, pushEnabled: true });

    service.updatePreferences({ emailEnabled: false, pushEnabled: true }).subscribe();
    const patchRequest = httpTesting.expectOne(`${apiUrl}/preferences`);
    expect(patchRequest.request.method).toBe('PATCH');
    expect(patchRequest.request.body).toEqual({ emailEnabled: false, pushEnabled: true });
    patchRequest.flush({ emailEnabled: false, pushEnabled: true });
  });
});
