import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { Chatbot } from '../../components/chatbot/chatbot';
import { AuthService, LibraryCard } from '../../services/auth.service';
import { BorrowCartService } from '../../services/borrow-cart.service';
import { LibraryNotification, NotificationService } from '../../services/notification.service';

@Component({
  selector: 'app-portal-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Chatbot, CommonModule],
  templateUrl: './portal-layout.html',
  styleUrls: ['./portal-layout.css', './portal-modals.css'],
})
export class PortalLayout {
  isUserMenuOpen = false;
  isNotificationModalOpen = false;
  isCardModalOpen = false;
  userAvatar: string | null = null;
  notifications: LibraryNotification[] = [];
  libraryCard: LibraryCard | null = null;
  notificationLoading = false;
  cardLoading = false;
  modalError = '';

  constructor(
    public authService: AuthService,
    public borrowCartService: BorrowCartService,
    private notificationService: NotificationService,
    private router: Router
  ) {}

  ngOnInit() {
    if (this.authService.isLoggedIn()) {
      this.authService.fetchMyProfile().subscribe({
        next: (user: any) => {
          if (user && user.avatarUrl) {
            this.userAvatar = user.avatarUrl;
          }
        },
        error: () => {}
      });
      this.loadNotifications();
    }
  }

  toggleUserMenu() {
    this.isUserMenuOpen = !this.isUserMenuOpen;
  }

  get unreadNotificationCount(): number {
    return this.notifications.filter(item => !item.isRead).length;
  }

  openNotifications(): void {
    this.closeOverlays();
    this.isNotificationModalOpen = true;
    this.loadNotifications();
  }

  openLibraryCard(): void {
    this.closeOverlays();
    this.isCardModalOpen = true;
    this.cardLoading = true;
    this.modalError = '';
    this.authService.fetchLibraryCard().subscribe({
      next: card => {
        this.libraryCard = card;
        this.cardLoading = false;
      },
      error: () => {
        this.cardLoading = false;
        this.modalError = 'Không thể tải thông tin thẻ thư viện.';
      },
    });
  }

  closeOverlays(): void {
    this.isUserMenuOpen = false;
    this.isNotificationModalOpen = false;
    this.isCardModalOpen = false;
    this.modalError = '';
  }

  loadNotifications(): void {
    this.notificationLoading = true;
    this.notificationService.getMyNotifications().subscribe({
      next: notifications => {
        this.notifications = notifications || [];
        this.notificationLoading = false;
      },
      error: () => {
        this.notificationLoading = false;
        this.modalError = 'Không thể tải thông báo.';
      },
    });
  }

  markNotificationRead(notification: LibraryNotification): void {
    if (notification.isRead) return;
    this.notificationService.markAsRead(notification.notifId).subscribe({
      next: updated => {
        this.notifications = this.notifications.map(item =>
          item.notifId === updated.notifId ? updated : item);
      },
      error: () => this.modalError = 'Không thể cập nhật thông báo.',
    });
  }

  markAllNotificationsRead(): void {
    this.notifications.filter(item => !item.isRead)
      .forEach(item => this.markNotificationRead(item));
  }

  notificationLabel(type: string): string {
    return ({
      LOAN_DUE_SOON: 'Sắp đến hạn',
      DUE_SOON: 'Sắp đến hạn',
      LOAN_OVERDUE: 'Quá hạn',
      OVERDUE: 'Quá hạn',
      PROPOSAL_APPROVED: 'Đề xuất được duyệt',
      PROPOSAL_REJECTED: 'Đề xuất bị từ chối',
      WAITLIST_READY: 'Sách sẵn sàng',
      ACCOUNT_LOCKED: 'Tài khoản bị khóa',
      ACCOUNT_UNLOCKED: 'Tài khoản mở lại',
      CIRCULATION_LOCKED: 'Quyền mượn bị khóa',
      CIRCULATION_UNLOCKED: 'Quyền mượn mở lại',
      RESERVATION_NO_SHOW: 'Cảnh báo nhận sách',
    } as Record<string, string>)[type] || 'Thông báo thư viện';
  }

  isCirculationLocked(lockedUntil?: string): boolean {
    return !!lockedUntil && new Date(lockedUntil).getTime() > Date.now();
  }

  cardStatusLabel(status?: string, lockedUntil?: string): string {
    if (this.isCirculationLocked(lockedUntil)) return 'Tạm khóa quyền mượn';
    if (status === 'ACTIVE') return 'Đang hoạt động';
    if (status === 'LOCKED') return 'Đang bị khóa';
    if (status === 'EXPIRED') return 'Đã hết hạn';
    return 'Chưa xác định';
  }

  logout() {
    this.authService.logout();
    this.isUserMenuOpen = false;
    this.router.navigate(['/login']);
  }
}

