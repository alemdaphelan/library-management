import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { BookService } from '../../../services/book.service';

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './search.html',
  styleUrls: ['./search.css']
})
export class Search implements OnInit {
  viewMode: 'grid' | 'list' = 'grid';
  allBooks: any[] = [];
  books: any[] = [];

  // Filtering & Sorting State
  searchQuery: string = '';
  filterStatus: string = 'all'; // 'all', 'available', 'borrowed'
  availableCategories = ['Khoa học máy tính', 'Hệ thống thông tin', 'Đồ họa', 'Lập trình Web', 'Chưa phân loại'];
  selectedCategories: { [key: string]: boolean } = {};
  sortOption: string = 'newest';

  private mockBooks = [
    {
      id: 1,
      title: 'Học Máy & Trí Tuệ Nhân Tạo Hiện Đại: Lý Thuyết và Thực Hành Ứng Dụng',
      author: 'Nguyễn Văn A',
      cover: '/assets/images/cover_1.jpg',
      category: 'Khoa học máy tính',
      status: 'available'
    },
    {
      id: 2,
      title: 'Flutter for Beginners',
      author: 'John Doe',
      cover: '/assets/images/cover_2.jpg',
      category: 'Lập trình Web',
      status: 'borrowed'
    },
    {
      id: 3,
      title: 'Pro ASP.NET Core 6',
      author: 'Adam Freeman',
      cover: '/assets/images/cover_3.jpg',
      category: 'Hệ thống thông tin',
      status: 'available'
    },
    {
      id: 4,
      title: 'Head First Design Patterns',
      author: 'Eric Freeman',
      cover: '/assets/images/cover_1.jpg',
      category: 'Khoa học máy tính',
      status: 'available'
    }
  ];

  constructor(private bookService: BookService, private cdr: ChangeDetectorRef) {
    this.availableCategories.forEach(cat => this.selectedCategories[cat] = false);
  }

  ngOnInit() {
    this.fetchBooks();
  }

  fetchBooks() {
    this.bookService.getBooks().subscribe({
      next: (data) => {
        if (data && data.length > 0) {
          this.allBooks = data.map(b => ({
            id: b.bookId || b.id,
            title: b.title,
            author: b.author || 'Đang cập nhật',
            cover: b.coverUrl || b.cover || '/assets/images/cover_1.jpg',
            category: b.category || 'Chưa phân loại',
            status: b.status || 'available'
          }));
        } else {
          this.allBooks = [...this.mockBooks];
        }
        this.applyFilters();
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error fetching books', err);
        this.allBooks = [...this.mockBooks];
        this.applyFilters();
        this.cdr.detectChanges();
      }
    });
  }

  applyFilters() {
    let filtered = [...this.allBooks];

    // Search query
    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase();
      filtered = filtered.filter(b => 
        b.title?.toLowerCase().includes(q) || 
        b.author?.toLowerCase().includes(q) || 
        b.category?.toLowerCase().includes(q)
      );
    }

    // Status filter
    if (this.filterStatus === 'available') {
      filtered = filtered.filter(b => b.status === 'available');
    } else if (this.filterStatus === 'borrowed') {
      filtered = filtered.filter(b => b.status === 'borrowed');
    }

    // Category filter
    const activeCategories = Object.keys(this.selectedCategories).filter(c => this.selectedCategories[c]);
    if (activeCategories.length > 0) {
      filtered = filtered.filter(b => activeCategories.includes(b.category));
    }

    // Sorting
    if (this.sortOption === 'newest') {
      filtered.sort((a, b) => b.id - a.id);
    } else if (this.sortOption === 'az') {
      filtered.sort((a, b) => a.title.localeCompare(b.title));
    } else if (this.sortOption === 'za') {
      filtered.sort((a, b) => b.title.localeCompare(a.title));
    }

    this.books = filtered;
  }

  clearFilters() {
    this.searchQuery = '';
    this.filterStatus = 'all'; 
    
    // Re-assign object to trigger change detection for ngModel bindings
    const clearedCategories: { [key: string]: boolean } = {};
    this.availableCategories.forEach(c => clearedCategories[c] = false);
    this.selectedCategories = clearedCategories;
    
    this.sortOption = 'newest';
    this.applyFilters();
    this.cdr.detectChanges(); // Force UI to update immediately
  }

  setViewMode(mode: 'grid' | 'list') {
    this.viewMode = mode;
  }
}
