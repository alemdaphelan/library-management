import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-books',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './books.html',
  styleUrls: ['./books.css']
})
export class Books {
  isDrawerOpen = false;
  drawerMode: 'add' | 'edit' = 'add';
  currentBook: any = {};

  // Mock data phong phú
  books = [
    {
      id: 'B001',
      title: 'Cấu trúc Dữ liệu và Giải thuật',
      author: 'Nguyễn Đức Nghĩa',
      isbn: '978-604-0-12345-6',
      category: 'Công nghệ thông tin',
      location: 'Tầng 3 - Kệ IT-204',
      copies: 15,
      status: 'available',
      coverUrl: 'https://placehold.co/60x80/0284c7/ffffff?text=CTDL'
    },
    {
      id: 'B002',
      title: 'Kế toán quản trị',
      author: 'Huỳnh Lợi',
      isbn: '978-604-0-98765-1',
      category: 'Kinh tế, Kế toán',
      location: 'Tầng 3 - Kệ ECO-102',
      copies: 0,
      status: 'out_of_stock',
      coverUrl: 'https://via.placeholder.com/60x80/f59e0b/ffffff?text=KT'
    },
    {
      id: 'B003',
      title: 'Giáo trình Tiếng Anh Giao tiếp',
      author: 'John Smith',
      isbn: '978-123-4-56789-0',
      category: 'Ngôn ngữ',
      location: 'Tầng 4 - Kệ LANG-05',
      copies: 4,
      status: 'available',
      coverUrl: 'https://via.placeholder.com/60x80/10b981/ffffff?text=ENG'
    },
    {
      id: 'B004',
      title: 'Nghiên cứu Khoa học Kỹ thuật Ô tô',
      author: 'Trần Văn B',
      isbn: '978-321-0-11111-2',
      category: 'Khoa học kỹ thuật',
      location: 'Tầng 4 - Kệ TECH-99',
      copies: 1,
      status: 'borrowed',
      coverUrl: 'https://via.placeholder.com/60x80/6366f1/ffffff?text=OTO'
    }
  ];

  openDrawer(mode: 'add' | 'edit', book?: any) {
    this.drawerMode = mode;
    this.isDrawerOpen = true;
    if (mode === 'edit' && book) {
      this.currentBook = { ...book };
    } else {
      this.currentBook = { coverUrl: 'https://via.placeholder.com/120x160/e2e8f0/64748b?text=Upload' };
    }
  }

  closeDrawer() {
    this.isDrawerOpen = false;
  }

  saveBook() {
    // Demo save
    this.closeDrawer();
  }
}
