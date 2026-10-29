import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface LibraryNotification {
  notifId: string;
  userId: string;
  type: string;
  content: string;
  isRead: boolean;
  sentAt?: string;
  referenceType?: string;
  referenceId?: string;
  createdAt?: string;
}

export interface NotificationPreferences {
  userId?: string;
  emailEnabled: boolean;
  pushEnabled: boolean;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly apiUrl = `${environment.apiUrl}/api/v1/notifications`;

  constructor(private http: HttpClient) {}

  getMyNotifications(): Observable<LibraryNotification[]> {
    return this.http.get<LibraryNotification[]>(this.apiUrl);
  }

  markAsRead(notificationId: string): Observable<LibraryNotification> {
    return this.http.patch<LibraryNotification>(`${this.apiUrl}/${notificationId}/read`, {});
  }

  getPreferences(): Observable<NotificationPreferences> {
    return this.http.get<NotificationPreferences>(`${this.apiUrl}/preferences`);
  }

  updatePreferences(preferences: NotificationPreferences): Observable<NotificationPreferences> {
    return this.http.patch<NotificationPreferences>(`${this.apiUrl}/preferences`, {
      emailEnabled: preferences.emailEnabled,
      pushEnabled: preferences.pushEnabled,
    });
  }
}
