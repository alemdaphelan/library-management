import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface DashboardStats {
  totalCopies: number;
  availableCopies: number;
  borrowedCurrent: number;
  reservedCopies: number;
  overdueBooks: number;
  fineCollected: number;
  asOf: string;
}

export interface WeeklyStat {
  date: string;
  day: string;
  count: number;
  percentage: number;
}

export interface RecentActivity {
  type: 'borrow' | 'return' | 'renew' | 'cancel';
  user: string;
  action: string;
  item: string;
  time: string;
}

export interface TrendingBook {
  bookId: number;
  title: string;
  author?: string;
  borrows: number;
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly apiUrl = `${environment.apiUrl}/dashboard`;

  constructor(private http: HttpClient) {}

  getStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.apiUrl}/stats`);
  }

  getWeeklyStats(): Observable<WeeklyStat[]> {
    return this.http.get<WeeklyStat[]>(`${this.apiUrl}/weekly-stats`);
  }

  getRecentActivities(): Observable<RecentActivity[]> {
    return this.http.get<RecentActivity[]>(`${this.apiUrl}/recent-activities`);
  }

  getTrendingBooks(): Observable<TrendingBook[]> {
    return this.http.get<TrendingBook[]>(`${this.apiUrl}/trending-books`);
  }
}
