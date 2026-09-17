import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-admin-suggestions',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './suggestions.html',
  styleUrls: ['./suggestions.css']
})
export class AdminSuggestions {
  activeTab = 'pending'; // 'all', 'pending', 'approved', 'rejected'
  selectedSuggestion: any = null;
  isDrawerOpen = false;

  suggestions = [
    { id: 'REQ-001', title: 'Clean Architecture: A Craftsman\'s Guide', author: 'Robert C. Martin', student: 'Nguyễn Văn A (20012341)', date: '15/09/2026', votes: 45, status: 'pending', reason: 'Sách rất cần thiết cho môn Phân tích thiết kế phần mềm.' },
    { id: 'REQ-002', title: 'Design Patterns: Elements of Reusable Object-Oriented Software', author: 'Erich Gamma', student: 'Trần Thị B (20012342)', date: '14/09/2026', votes: 32, status: 'approved', reason: 'Tài liệu tham khảo bắt buộc.' },
    { id: 'REQ-003', title: 'Cracking the Coding Interview', author: 'Gayle Laakmann McDowell', student: 'Lê Văn C (20012343)', date: '12/09/2026', votes: 89, status: 'pending', reason: 'Giúp sinh viên ôn thi phỏng vấn thực tập tốt hơn.' },
    { id: 'REQ-004', title: 'Truyện tranh Doraemon Tập 1', author: 'Fujiko F. Fujio', student: 'Phạm Thị D (20012344)', date: '10/09/2026', votes: 5, status: 'rejected', reason: 'Thư viện trường đại học không ưu tiên nhập truyện tranh thiếu nhi.' },
    { id: 'REQ-005', title: 'Grokking Algorithms', author: 'Aditya Bhargava', student: 'Hoàng Văn E (20012345)', date: '08/09/2026', votes: 20, status: 'pending', reason: 'Sách dễ hiểu, phù hợp người mới học thuật toán.' }
  ];

  get filteredSuggestions() {
    if (this.activeTab === 'all') return this.suggestions;
    return this.suggestions.filter(s => s.status === this.activeTab);
  }

  setTab(tab: string) {
    this.activeTab = tab;
  }

  openDrawer(suggestion: any) {
    this.selectedSuggestion = suggestion;
    this.isDrawerOpen = true;
    document.body.style.overflow = 'hidden';
  }

  closeDrawer() {
    this.isDrawerOpen = false;
    document.body.style.overflow = '';
    setTimeout(() => this.selectedSuggestion = null, 300);
  }

  approveSuggestion() {
    if (this.selectedSuggestion) {
      this.selectedSuggestion.status = 'approved';
      this.closeDrawer();
    }
  }

  rejectSuggestion() {
    if (this.selectedSuggestion) {
      this.selectedSuggestion.status = 'rejected';
      this.closeDrawer();
    }
  }
}
