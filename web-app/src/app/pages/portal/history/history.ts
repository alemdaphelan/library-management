import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-history',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './history.html',
  styleUrls: ['./history.css']
})
export class History {
  activeBorrows = [
    {
      id: 1,
      title: 'Học Máy & Trí Tuệ Nhân Tạo Hiện Đại: Lý Thuyết và Thực Hành Ứng Dụng',
      cover: '/assets/images/cover_1.jpg',
      borrowDate: '10/09/2026',
      dueDate: '24/09/2026',
      daysLeft: 8,
      progress: 57, // (14-8)/14 * 100
      status: 'warning' // warning = sắp đến hạn, normal = còn nhiều, overdue = quá hạn
    },
    {
      id: 2,
      title: 'Flutter for Beginners',
      cover: '/assets/images/cover_2.jpg',
      borrowDate: '15/09/2026',
      dueDate: '29/09/2026',
      daysLeft: 13,
      progress: 7,
      status: 'normal'
    }
  ];

  pastBorrows = [
    {
      id: 3,
      title: 'Pro ASP.NET Core 6',
      cover: '/assets/images/cover_3.jpg',
      borrowDate: '01/08/2026',
      returnDate: '12/08/2026',
      status: 'returned'
    },
    {
      id: 4,
      title: 'Head First Design Patterns',
      cover: '/assets/images/cover_1.jpg',
      borrowDate: '10/06/2026',
      returnDate: '26/06/2026',
      status: 'late_returned' // Trả trễ
    }
  ];
}
