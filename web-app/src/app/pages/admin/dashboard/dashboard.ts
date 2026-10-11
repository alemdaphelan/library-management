import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DashboardService } from '../../../services/dashboard.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css']
})
export class Dashboard implements OnInit {
  stats: any = {};
  weeklyStats: any[] = [];
  recentActivities: any[] = [];
  trendingBooks: any[] = [];

  constructor(private dashboardService: DashboardService) {}

  ngOnInit() {
    this.dashboardService.getStats().subscribe({
      next: (data) => this.stats = data,
      error: (err) => console.error('Error fetching stats', err)
    });

    this.dashboardService.getWeeklyStats().subscribe({
      next: (data) => this.weeklyStats = data,
      error: (err) => console.error('Error fetching weekly stats', err)
    });

    this.dashboardService.getRecentActivities().subscribe({
      next: (data) => this.recentActivities = data,
      error: (err) => console.error('Error fetching recent activities', err)
    });

    this.dashboardService.getTrendingBooks().subscribe({
      next: (data) => this.trendingBooks = data,
      error: (err) => console.error('Error fetching trending books', err)
    });
  }
}
