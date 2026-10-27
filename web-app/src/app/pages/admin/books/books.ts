import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BookService } from '../../../services/book.service';
import { Category, CategoryService } from '../../../services/category.service';
import { DialogService } from '../../../services/dialog.service';

@Component({
  selector: 'app-books',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './books.html',
  styleUrls: ['./books.css']
})
export class Books implements OnInit {
  isDrawerOpen = false;
  drawerMode: 'add' | 'edit' = 'add';
  currentBook: any = {};

  books: any[] = [];
  categories: Category[] = [];
  searchQuery = '';
  selectedCategoryId?: number;
  selectedAvailability: '' | 'AVAILABLE' | 'UNAVAILABLE' = '';
  isLoading = false;
  errorMessage = '';
  successMessage = '';
  
  // Pagination State
  currentPage: number = 0;
  pageSize: number = 10;
  totalPages: number = 0;
  totalElements: number = 0;

  constructor(
    private bookService: BookService,
    private categoryService: CategoryService,
    private dialog: DialogService,
  ) {}

  ngOnInit() {
    this.categoryService.getCategories().subscribe({
      next: categories => this.categories = categories,
      error: err => console.error('Failed to load categories', err)
    });
    this.loadBooks();
  }

  loadBooks() {
    this.isLoading = true;
    this.errorMessage = '';
    this.bookService.getBooks(
      this.searchQuery,
      this.currentPage,
      this.pageSize,
      this.selectedCategoryId,
      this.selectedAvailability || undefined
    ).subscribe({
      next: (data) => {
        this.books = data.content;
        this.totalPages = data.totalPages;
        this.totalElements = data.totalElements;
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Failed to load books', err);
        this.isLoading = false;
        this.errorMessage = this.extractError(err, 'Không thể tải danh mục sách.');
      }
    });
  }

  applyFilters() {
    this.currentPage = 0;
    this.loadBooks();
  }

  goToPage(page: number) {
    if (page >= 0 && page < this.totalPages) {
      this.currentPage = page;
      this.loadBooks();
    }
  }

  nextPage() {
    this.goToPage(this.currentPage + 1);
  }

  prevPage() {
    this.goToPage(this.currentPage - 1);
  }

  openDrawer(mode: 'add' | 'edit', book?: any) {
    this.drawerMode = mode;
    this.isDrawerOpen = true;
    if (mode === 'edit' && book) {
      this.currentBook = {
        ...book,
        categoryId: book.category?.categoryId,
        imgError: false
      };
    } else {
      this.currentBook = { imgError: false };
    }
  }

  closeDrawer() {
    this.isDrawerOpen = false;
  }

  saveBook() {
    this.errorMessage = '';
    this.successMessage = '';
    if (!this.currentBook.title?.trim() || !this.currentBook.author?.trim()) {
      this.dialog.show({
        title: 'Thiếu thông tin',
        message: 'Vui lòng nhập tên sách và tác giả.',
        type: 'warning',
      });
      return;
    }
    const { bookId, categoryId, copies, availableCopies, location, status, imgError, ...editableFields } = this.currentBook;
    const payload = {
      ...editableFields,
      category: categoryId ? { categoryId: Number(categoryId) } : null
    };
    if (this.drawerMode === 'add') {
      this.bookService.createBook(payload).subscribe({
        next: () => {
          this.successMessage = 'Đã thêm đầu sách vào danh mục.';
          this.loadBooks();
          this.closeDrawer();
        },
        error: (err) => {
          console.error('Failed to create book', err);
          this.errorMessage = this.extractError(err, 'Thêm sách thất bại.');
        }
      });
    } else if (this.drawerMode === 'edit') {
      this.bookService.updateBook(String(bookId), payload).subscribe({
        next: () => {
          this.successMessage = 'Đã cập nhật thông tin đầu sách.';
          this.loadBooks();
          this.closeDrawer();
        },
        error: (err) => {
          console.error('Failed to update book', err);
          this.errorMessage = this.extractError(err, 'Cập nhật sách thất bại.');
        }
      });
    }
  }

  async deleteBook(book: any) {
    const confirmed = await this.dialog.confirm({
      title: 'Xóa đầu sách',
      message: `Bạn có chắc muốn xóa “${book.title}”?`,
      type: 'warning',
      confirmText: 'Xóa sách',
    });
    if (!confirmed) return;
    this.bookService.deleteBook(String(book.bookId)).subscribe({
      next: () => {
        this.successMessage = 'Đã xóa đầu sách khỏi danh mục.';
        if (this.books.length === 1 && this.currentPage > 0) this.currentPage--;
        this.loadBooks();
      },
      error: err => {
        console.error('Failed to delete book', err);
        this.errorMessage = this.extractError(
          err,
          'Không thể xóa sách. Hãy kiểm tra các bản sao hoặc giao dịch đang liên kết.'
        );
      }
    });
  }

  get visiblePages(): (number | string)[] {
    const total = this.totalPages;
    if (total === 0) return [];
    
    const current = this.currentPage + 1;
    const delta = 2;

    let pages: (number | string)[] = [];

    if (total <= 7) {
      for (let i = 1; i <= total; i++) {
        pages.push(i);
      }
    } else {
      pages.push(1);
      
      let left = Math.max(2, current - delta);
      let right = Math.min(total - 1, current + delta);
      
      if (left > 2) {
        pages.push('...');
      }
      
      for (let i = left; i <= right; i++) {
        pages.push(i);
      }
      
      if (right < total - 1) {
        pages.push('...');
      }
      
      pages.push(total);
    }
    
    return pages;
  }
  
  onPageClick(p: number | string) {
    if (typeof p === 'number') {
      this.goToPage(p - 1);
    }
  }

  private extractError(error: any, fallback: string): string {
    const message = error?.error?.message ?? error?.error?.detail ?? error?.error;
    return typeof message === 'string' && message.trim() ? message : fallback;
  }
}
