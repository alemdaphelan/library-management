import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BookService } from '../../../services/book.service';

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
  
  // Pagination State
  currentPage: number = 0;
  pageSize: number = 10;
  totalPages: number = 0;
  totalElements: number = 0;

  constructor(private bookService: BookService) {}

  ngOnInit() {
    this.loadBooks();
  }

  loadBooks() {
    this.bookService.getBooks(undefined, this.currentPage, this.pageSize).subscribe({
      next: (data) => {
        this.books = data.content;
        this.totalPages = data.totalPages;
        this.totalElements = data.totalElements;
      },
      error: (err) => console.error('Failed to load books', err)
    });
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
      this.currentBook = { ...book, imgError: false };
    } else {
      this.currentBook = { imgError: false };
    }
  }

  closeDrawer() {
    this.isDrawerOpen = false;
  }

  saveBook() {
    if (this.drawerMode === 'add') {
      this.bookService.createBook(this.currentBook).subscribe({
        next: () => {
          this.loadBooks();
          this.closeDrawer();
        },
        error: (err) => {
          console.error('Failed to create book', err);
          alert('Thêm sách thất bại');
        }
      });
    } else if (this.drawerMode === 'edit') {
      this.bookService.updateBook(this.currentBook.id || this.currentBook.isbn, this.currentBook).subscribe({
        next: () => {
          this.loadBooks();
          this.closeDrawer();
        },
        error: (err) => {
          console.error('Failed to update book', err);
          alert('Cập nhật sách thất bại');
        }
      });
    }
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
}
