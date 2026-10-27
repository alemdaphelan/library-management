import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LibraryNotification, NotificationService } from '../../../services/notification.service';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './notifications.html',
  styleUrls: ['./notifications.css'],
})
export class Notifications implements OnInit {
  notifications: LibraryNotification[] = [];
  searchTerm = '';
  readFilter: 'ALL' | 'UNREAD' | 'READ' = 'ALL';
  isLoading = false;
  markingAll = false;
  markingId: string | null = null;
  errorMessage = '';
  successMessage = '';

  constructor(
    private notificationService: NotificationService,
    private router: Router,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadNotifications();
  }

  get unreadCount(): number {
    return this.notifications.filter(notification => !notification.isRead).length;
  }

  get overdueCount(): number {
    return this.notifications.filter(notification => notification.type === 'LOAN_OVERDUE').length;
  }

  get filteredNotifications(): LibraryNotification[] {
    const query = this.searchTerm.trim().toLocaleLowerCase('vi');
    return this.notifications.filter(notification => {
      const matchesRead = this.readFilter === 'ALL'
        || (this.readFilter === 'READ' && notification.isRead)
        || (this.readFilter === 'UNREAD' && !notification.isRead);
      const searchable = [notification.content, notification.type, notification.referenceType, notification.referenceId]
        .filter(Boolean)
        .join(' ')
        .toLocaleLowerCase('vi');
      return matchesRead && (!query || searchable.includes(query));
    });
  }

  loadNotifications(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.notificationService.getMyNotifications().subscribe({
      next: notifications => {
        this.notifications = notifications || [];
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: error => {
        this.isLoading = false;
        this.errorMessage = this.extractError(error, 'Không thể tải thông báo.');
        this.cdr.detectChanges();
      },
    });
  }

  markAsRead(notification: LibraryNotification): void {
    if (notification.isRead || this.markingId) return;
    this.markingId = notification.notifId;
    this.errorMessage = '';
    this.notificationService.markAsRead(notification.notifId).subscribe({
      next: updated => {
        this.notifications = this.notifications.map(item => item.notifId === updated.notifId ? updated : item);
        this.markingId = null;
        this.cdr.detectChanges();
      },
      error: error => {
        this.markingId = null;
        this.errorMessage = this.extractError(error, 'Không thể đánh dấu thông báo đã đọc.');
        this.cdr.detectChanges();
      },
    });
  }

  openNotification(notification: LibraryNotification): void {
    const target = this.notificationTarget(notification);
    const navigate = () => this.router.navigate([target.path], {
      queryParams: target.queryParams,
    });
    if (notification.isRead) {
      void navigate();
      return;
    }
    this.markingId = notification.notifId;
    this.notificationService.markAsRead(notification.notifId).subscribe({
      next: updated => {
        this.notifications = this.notifications.map(item => item.notifId === updated.notifId ? updated : item);
        this.markingId = null;
        this.cdr.detectChanges();
        void navigate();
      },
      error: error => {
        this.markingId = null;
        this.errorMessage = this.extractError(error, 'Không thể mở thông báo.');
        this.cdr.detectChanges();
      },
    });
  }

  markAllAsRead(): void {
    const unread = this.notifications.filter(notification => !notification.isRead);
    if (!unread.length || this.markingAll) return;
    this.markingAll = true;
    this.errorMessage = '';
    forkJoin(unread.map(notification => this.notificationService.markAsRead(notification.notifId))).subscribe({
      next: updatedNotifications => {
        const updatedById = new Map(updatedNotifications.map(notification => [notification.notifId, notification]));
        this.notifications = this.notifications.map(notification => updatedById.get(notification.notifId) || notification);
        this.markingAll = false;
        this.successMessage = `Đã đánh dấu ${updatedNotifications.length} thông báo là đã đọc.`;
        this.cdr.detectChanges();
      },
      error: error => {
        this.markingAll = false;
        this.errorMessage = this.extractError(error, 'Không thể đánh dấu tất cả thông báo.');
        this.cdr.detectChanges();
        this.loadNotifications();
      },
    });
  }

  typeLabel(type: string): string {
    return ({
      LOAN_DUE_SOON: 'Sắp đến hạn',
      LOAN_OVERDUE: 'Quá hạn',
      LOAN_APPROVED: 'Yêu cầu mượn đã được duyệt',
      LOAN_REJECTED: 'Yêu cầu mượn bị từ chối',
      RESERVATION_EXPIRED: 'Hết hạn giữ chỗ',
      RESERVATION_NO_SHOW: 'Không đến nhận sách',
      LOAN_REQUEST_PENDING: 'Yêu cầu mượn mới',
      BOOK_PROPOSAL_PENDING: 'Đề xuất sách mới',
      IMPORT_COMPLETED: 'Đã nhập kho',
      PROPOSAL_APPROVED: 'Đề xuất được duyệt',
      PROPOSAL_REJECTED: 'Đề xuất bị từ chối',
      PENALTY_CASE_CREATED: 'Hồ sơ phạt mới',
      PENALTY_CASE_DUE: 'Sắp hết hạn đền bù',
      DEBT_PAYMENT: 'Đã thu tiền',
      CIRCULATION_LOCKED: 'Đã khóa quyền mượn',
      CIRCULATION_UNLOCKED: 'Đã mở quyền mượn',
      WAIVER_DECISION: 'Đã có quyết định miễn giảm',
      WAITLIST_AVAILABLE: 'Sách trong hàng đợi đã sẵn sàng',
      WAITLIST_EXPIRED: 'Lượt hàng đợi đã hết hạn',
    } as Record<string, string>)[type] || 'Thông báo hệ thống';
  }

  typeClass(type: string): string {
    if (type === 'LOAN_OVERDUE') return 'overdue';
    if (type === 'LOAN_DUE_SOON') return 'reminder';
    return 'info';
  }

  actionLabel(notification: LibraryNotification): string {
    const referenceType = (notification.referenceType || '').toUpperCase();
    return ({
      BOOK_PROPOSAL: 'Mở đề xuất',
      LOAN_TICKET: 'Mở phiếu mượn',
      LOAN_DETAIL: 'Mở quản lý mượn',
      WAITLIST: 'Mở quản lý mượn',
      GOODS_RECEIPT: 'Mở phiếu nhập',
      PENALTY_CASE: 'Mở hồ sơ phạt',
      TRANSACTION: 'Mở thu phạt',
      BOOK: 'Mở danh mục sách',
      USER: 'Mở hồ sơ bạn đọc',
    } as Record<string, string>)[referenceType] || 'Xem chi tiết';
  }

  private notificationTarget(notification: LibraryNotification): { path: string; queryParams?: Record<string, string> } {
    const type = (notification.referenceType || '').toUpperCase();
    const referenceId = notification.referenceId;
    const target = ({
      BOOK_PROPOSAL: '/admin/suggestions',
      LOAN_TICKET: '/admin/circulation',
      LOAN_DETAIL: '/admin/borrows',
      WAITLIST: '/admin/circulation',
      GOODS_RECEIPT: '/admin/imports',
      PENALTY_CASE: '/admin/penalties',
      TRANSACTION: '/admin/penalties',
      BOOK: '/admin/books',
      USER: '/admin/accounts',
    } as Record<string, string>)[type] || '/admin';
    return {
      path: target,
      queryParams: referenceId ? { referenceType: type, referenceId } : undefined,
    };
  }

  private extractError(error: any, fallback: string): string {
    const message = error?.error?.message ?? error?.error;
    return typeof message === 'string' && message.trim() ? message : fallback;
  }
}
