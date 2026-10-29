import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  DashboardService,
  DashboardStats,
  RecentActivity,
  TrendingBook,
  WeeklyStat,
} from '../../../services/dashboard.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css']
})
export class Dashboard implements OnInit {
  stats?: DashboardStats;
  weeklyStats: WeeklyStat[] = [];
  recentActivities: RecentActivity[] = [];
  trendingBooks: TrendingBook[] = [];
  loadingRequests = 0;
  errorMessage = '';

  constructor(private dashboardService: DashboardService, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.loadingRequests = 4;
    this.dashboardService.getStats().subscribe({
      next: data => {
        this.stats = data;
        this.finishRequest();
      },
      error: err => this.handleError('Không thể tải thống kê tổng quan.', err)
    });

    this.dashboardService.getWeeklyStats().subscribe({
      next: data => {
        this.weeklyStats = data;
        this.finishRequest();
      },
      error: err => this.handleError('Không thể tải thống kê 7 ngày.', err)
    });

    this.dashboardService.getRecentActivities().subscribe({
      next: data => {
        this.recentActivities = data;
        this.finishRequest();
      },
      error: err => this.handleError('Không thể tải hoạt động gần đây.', err)
    });

    this.dashboardService.getTrendingBooks().subscribe({
      next: data => {
        this.trendingBooks = data;
        this.finishRequest();
      },
      error: err => this.handleError('Không thể tải tài liệu được quan tâm.', err)
    });
  }

  get isLoading(): boolean {
    return this.loadingRequests > 0;
  }

  copyPercentage(count: number): number {
    if (!this.stats?.totalCopies) return 0;
    return count * 100 / this.stats.totalCopies;
  }

  private finishRequest(): void {
    this.loadingRequests = Math.max(0, this.loadingRequests - 1);
    this.cdr.detectChanges();
  }

  private handleError(message: string, error: unknown): void {
    console.error(message, error);
    this.errorMessage ||= message;
    this.finishRequest();
  }
}
