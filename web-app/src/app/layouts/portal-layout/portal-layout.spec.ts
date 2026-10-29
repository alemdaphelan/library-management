import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PortalLayout } from './portal-layout';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { NotificationService } from '../../services/notification.service';

describe('PortalLayout', () => {
  let component: PortalLayout;
  let fixture: ComponentFixture<PortalLayout>;
  const notificationService = {
    getMyNotifications: vi.fn(() => of([{
      notifId: 'notification-1',
      userId: 'reader-1',
      type: 'LOAN_OVERDUE',
      content: 'Tài liệu của bạn đã quá hạn.',
      isRead: false,
      sentAt: '2026-10-27T08:00:00',
    }])),
    markAsRead: vi.fn(),
  };

  beforeEach(async () => {
    localStorage.clear();
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [PortalLayout],
      providers: [
        provideRouter([]),
        { provide: NotificationService, useValue: notificationService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PortalLayout);
    component = fixture.componentInstance;
    await fixture.whenStable();
  }, 30_000);

  afterEach(() => localStorage.clear());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows the admin switch only for staff roles', () => {
    const isLoggedIn = vi.spyOn(component.authService, 'isLoggedIn').mockReturnValue(true);
    const getRole = vi.spyOn(component.authService, 'getRole').mockReturnValue('ADMIN');
    expect(component.canAccessAdmin).toBe(true);

    getRole.mockReturnValue('LIBRARIAN');
    expect(component.canAccessAdmin).toBe(true);

    getRole.mockReturnValue('STUDENT');
    expect(component.canAccessAdmin).toBe(false);

    getRole.mockReturnValue('ADMIN');
    isLoggedIn.mockReturnValue(false);
    expect(component.canAccessAdmin).toBe(false);
  });

  it('labels a missing card instead of showing an unknown status', () => {
    expect(component.cardStatusLabel(undefined)).toBe('Chưa được cấp thẻ');
  });

  it('renders persisted notifications as soon as the modal is opened', () => {
    component.openNotifications();
    fixture.detectChanges();

    expect(notificationService.getMyNotifications).toHaveBeenCalled();
    expect(component.notificationLoading).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Tài liệu của bạn đã quá hạn.');
  });
});
