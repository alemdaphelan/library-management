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

  constructor(private bookService: BookService) {}

  ngOnInit() {
    this.loadBooks();
  }

  loadBooks() {
    this.bookService.getBooks().subscribe({
      next: (data) => {
        this.books = data;
      },
      error: (err) => console.error('Failed to load books', err)
    });
  }

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
