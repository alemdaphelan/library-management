import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { BookService } from '../../../services/book.service';

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './search.html',
  styleUrls: ['./search.css']
})
export class Search implements OnInit {
  viewMode: 'grid' | 'list' = 'grid';
  books: any[] = [];

  constructor(private bookService: BookService) {}

  ngOnInit() {
    this.bookService.getBooks().subscribe({
      next: (data) => this.books = data,
      error: (err) => console.error('Error fetching books', err)
    });
  }

  setViewMode(mode: 'grid' | 'list') {
    this.viewMode = mode;
  }
}
