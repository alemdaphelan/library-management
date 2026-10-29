import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BookService, WishlistItem } from '../../../services/book.service';

@Component({
  selector: 'app-saved-books',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './saved-books.html',
  styleUrls: ['./saved-books.css'],
})
export class SavedBooks implements OnInit {
  books: WishlistItem[] = [];
  loading = true;
  errorMessage = '';

  constructor(private bookService: BookService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.loadBooks();
  }

  remove(bookId: number): void {
    this.bookService.removeFromWishlist(bookId).subscribe({
      next: () => {
        this.books = this.books.filter(book => book.bookId !== bookId);
        this.cdr.detectChanges();
      },
      error: () => {
        this.errorMessage = 'Không thể bỏ tài liệu khỏi tủ sách. Vui lòng thử lại.';
        this.cdr.detectChanges();
      },
    });
  }

  private loadBooks(): void {
    this.bookService.getWishlist().subscribe({
      next: books => {
        this.books = books;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Không thể tải tủ sách cá nhân.';
        this.cdr.detectChanges();
      },
    });
  }
}
