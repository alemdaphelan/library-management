import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { Dashboard } from './dashboard';
import { DashboardService } from '../../../services/dashboard.service';

describe('Dashboard', () => {
  let component: Dashboard;
  let fixture: ComponentFixture<Dashboard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [{
        provide: DashboardService,
        useValue: {
          getStats: () => of({
            totalCopies: 20,
            availableCopies: 12,
            borrowedCurrent: 5,
            reservedCopies: 3,
            overdueBooks: 2,
            fineCollected: 15000,
            asOf: '2026-10-17T10:00:00',
          }),
          getWeeklyStats: () => of([
            { date: '2026-10-17', day: 'T7', count: 4, percentage: 100 },
          ]),
          getRecentActivities: () => of([
            { type: 'borrow', user: 'Nguyễn Văn A', action: 'đã đặt/mượn', item: 'Clean Code', time: '2026-10-17T09:00:00' },
          ]),
          getTrendingBooks: () => of([
            { bookId: 1, title: 'Clean Code', author: 'Robert C. Martin', borrows: 4 },
          ]),
        },
      }],
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders values returned by the dashboard API', () => {
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;

    expect(text).toContain('20');
    expect(text).toMatch(/15[,.]000 đ/);
    expect(text).toContain('Clean Code');
    expect(text).not.toContain('Tháng 9, 2026');
  });
});
