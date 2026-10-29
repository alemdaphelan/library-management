import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { provideRouter, Router } from '@angular/router';
import { LibraryNotification, NotificationService } from '../../../services/notification.service';
import { Notifications } from './notifications';

describe('Notifications', () => {
  let component: Notifications;
  let fixture: ComponentFixture<Notifications>;

  const unread: LibraryNotification = {
    notifId: 'notif-1',
    userId: 'user-1',
    type: 'LOAN_OVERDUE',
    content: 'Tài liệu BC-001 của phiếu #19 đã quá hạn.',
    isRead: false,
    sentAt: '2026-10-18T08:00:00',
    referenceType: 'LOAN_DETAIL',
    referenceId: '29',
  };
  const service = {
    getMyNotifications: vi.fn(() => of([unread])),
    markAsRead: vi.fn((id: string) => of({ ...unread, notifId: id, isRead: true })),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [Notifications],
      providers: [{ provide: NotificationService, useValue: service }, provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(Notifications);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads and renders persisted notifications instead of sample records', () => {
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;

    expect(service.getMyNotifications).toHaveBeenCalled();
    expect(text).toContain('Tài liệu BC-001');
    expect(text).toContain('Quá hạn');
    expect(text).not.toContain('NTF-001');
  });

  it('marks an owned notification as read through the API', () => {
    component.markAsRead(unread);

    expect(service.markAsRead).toHaveBeenCalledWith('notif-1');
    expect(component.unreadCount).toBe(0);
    expect(component.notifications[0].isRead).toBe(true);
  });

  it('filters by read state and content', () => {
    component.searchTerm = 'BC-001';
    component.readFilter = 'UNREAD';
    expect(component.filteredNotifications).toEqual([unread]);

    component.searchTerm = 'không tồn tại';
    expect(component.filteredNotifications).toHaveLength(0);
  });

  it('marks a notification as read and navigates to its related workflow', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.openNotification(unread);

    expect(service.markAsRead).toHaveBeenCalledWith('notif-1');
    expect(navigate).toHaveBeenCalledWith(['/admin/borrows'], {
      queryParams: { referenceType: 'LOAN_DETAIL', referenceId: '29' },
    });
  });
});
