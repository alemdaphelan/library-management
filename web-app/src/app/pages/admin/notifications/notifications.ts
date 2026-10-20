import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
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

  constructor(private notificationService: NotificationService) {}

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
      },
      error: error => {
        this.isLoading = false;
        this.errorMessage = this.extractError(error, 'Không thể tải thông báo.');
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
      },
      error: error => {
        this.markingId = null;
        this.errorMessage = this.extractError(error, 'Không thể đánh dấu thông báo đã đọc.');
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
      },
      error: error => {
        this.markingAll = false;
        this.errorMessage = this.extractError(error, 'Không thể đánh dấu tất cả thông báo.');
        this.loadNotifications();
      },
    });
  }

  typeLabel(type: string): string {
    return ({
      LOAN_DUE_SOON: 'Sắp đến hạn',
      LOAN_OVERDUE: 'Quá hạn',
      RESERVATION_EXPIRED: 'Hết hạn giữ chỗ',
    } as Record<string, string>)[type] || 'Thông báo hệ thống';
  }

  typeClass(type: string): string {
    if (type === 'LOAN_OVERDUE') return 'overdue';
    if (type === 'LOAN_DUE_SOON') return 'reminder';
    return 'info';
  }

  private extractError(error: any, fallback: string): string {
    const message = error?.error?.message ?? error?.error;
    return typeof message === 'string' && message.trim() ? message : fallback;
  }
}
