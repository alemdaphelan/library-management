import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notifications.html',
  styleUrls: ['./notifications.css']
})
export class Notifications {
  // Thống kê nhanh
  stats = {
    totalSent: 1254,
    overdueWarnings: 342,
    systemNotices: 45
  };

  // Lịch sử thông báo
  history = [
    { id: 'NTF-001', type: 'overdue', title: 'Cảnh báo: Sách quá hạn chưa trả', target: '25 Sinh viên (Quá hạn > 3 ngày)', date: 'Hôm nay, 08:30 AM', status: 'Sent' },
    { id: 'NTF-002', type: 'reminder', title: 'Nhắc nhở: Sắp đến hạn trả sách', target: '112 Sinh viên (Còn 2 ngày)', date: 'Hôm qua, 09:00 AM', status: 'Sent' },
    { id: 'NTF-003', type: 'info', title: 'Thông báo: Thư viện nghỉ lễ 2/9', target: 'Tất cả sinh viên', date: '30/08/2026, 14:00 PM', status: 'Sent' },
    { id: 'NTF-004', type: 'info', title: 'Cập nhật tài nguyên: Đã có sách mới', target: 'Khoa Công nghệ Thông tin', date: '25/08/2026, 10:15 AM', status: 'Sent' },
    { id: 'NTF-005', type: 'overdue', title: 'Cảnh báo mức 2: Sách quá hạn', target: '10 Sinh viên (Quá hạn > 7 ngày)', date: '20/08/2026, 08:00 AM', status: 'Sent' }
  ];

  isSending = false;

  sendNotification() {
    this.isSending = true;
    setTimeout(() => {
      this.isSending = false;
      alert('Đã gửi thông báo thành công tới các sinh viên!');
      // Thêm một record ảo vào lịch sử
      this.history.unshift({
        id: 'NTF-' + Math.floor(Math.random() * 1000),
        type: 'info',
        title: 'Thông báo mới (Vừa tạo)',
        target: 'Người nhận vừa chọn',
        date: 'Vừa xong',
        status: 'Sent'
      });
      this.stats.totalSent++;
    }, 1500);
  }
}
