import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css']
})
export class Dashboard {
  // Thống kê tổng quan
  stats = {
    totalBooks: '12,450',
    borrowedCurrent: '845',
    overdueBooks: '32',
    fineCollected: '1,250,000đ'
  };

  // Dữ liệu biểu đồ cột (Lượt mượn 7 ngày)
  weeklyStats = [
    { day: 'T2', count: 45, percentage: 45 },
    { day: 'T3', count: 60, percentage: 60 },
    { day: 'T4', count: 85, percentage: 85 },
    { day: 'T5', count: 40, percentage: 40 },
    { day: 'T6', count: 95, percentage: 95 },
    { day: 'T7', count: 120, percentage: 100 },
    { day: 'CN', count: 30, percentage: 30 }
  ];

  // Hoạt động gần đây
  recentActivities = [
    { user: 'Nguyễn Văn A', id: '20012111', action: 'Đã mượn', item: 'Clean Code', time: '10 phút trước', type: 'borrow' },
    { user: 'Trần Thị B', id: '20012122', action: 'Đã trả', item: 'Giải tích 1', time: '30 phút trước', type: 'return' },
    { user: 'Lê Minh C', id: '20012133', action: 'Gia hạn', item: 'Lịch sử Đảng', time: '1 giờ trước', type: 'renew' },
    { user: 'Phạm Văn D', id: '20012144', action: 'Bị phạt', item: 'Quá hạn 3 ngày', time: '2 giờ trước', type: 'fine' },
    { user: 'Vũ Thị E', id: '20012155', action: 'Đã mượn', item: 'Đắc nhân tâm', time: '3 giờ trước', type: 'borrow' }
  ];

  // Top sách thịnh hành
  trendingBooks = [
    { title: 'Cấu trúc dữ liệu và giải thuật', author: 'Nguyễn Đức Nghĩa', borrows: 156 },
    { title: 'Nhập môn lập trình', author: 'Trần Đan Thư', borrows: 142 },
    { title: 'Tiếng Anh giao tiếp', author: 'John Smith', borrows: 98 },
    { title: 'Triết học Mác - Lênin', author: 'Bộ GDĐT', borrows: 85 }
  ];
}
