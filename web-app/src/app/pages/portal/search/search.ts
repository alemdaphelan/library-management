import { Component, OnInit, ChangeDetectorRef, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { BookService } from '../../../services/book.service';
import { CategoryService, Category } from '../../../services/category.service';
import { Subject, Subscription, of } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, catchError } from 'rxjs/operators';
import { Router } from '@angular/router';

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './search.html',
  styleUrls: ['./search.css']
})
export class Search implements OnInit, OnDestroy {
  viewMode: 'grid' | 'list' = 'grid';
  allBooks: any[] = [];
  books: any[] = [];

  // Filtering & Sorting State
  searchQuery: string = '';
  filterStatus: string = 'all'; // 'all', 'available', 'borrowed'
  availableCategories: string[] = [];
  selectedCategories: { [key: string]: boolean } = {};
  sortOption: string = 'newest';

  // Pagination State
  currentPage: number = 0;
  pageSize: number = 10;
  totalPages: number = 0;
  totalElements: number = 0;

  // Quick Search State
  searchSubject = new Subject<string>();
  quickSearchResults: any[] = [];
  showDropdown = false;
  searchSubscription!: Subscription;

  constructor(
    private bookService: BookService, 
    private categoryService: CategoryService,
    private cdr: ChangeDetectorRef,
    private router: Router
  ) {}

  ngOnInit() {
    this.fetchCategories();
    this.fetchBooks();

    this.searchSubscription = this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(query => {
        if (!query.trim()) {
          return of({ content: [] });
        }
        return this.bookService.getBooks(query, 0, 5).pipe(
          catchError(() => of({ content: [] }))
        );
      })
    ).subscribe(data => {
      if (data && data.content && data.content.length > 0) {
        this.quickSearchResults = data.content.map((b: any) => ({
          id: b.bookId || b.id,
          title: b.title,
          author: b.author || 'Đang cập nhật',
          cover: b.imageUrlS || b.imageUrlM || b.imageUrlL || b.cover || '/assets/images/cover_1.jpg'
        }));
        this.showDropdown = true;
      } else {
        this.quickSearchResults = [];
      }
      this.cdr.detectChanges();
    });
  }

  ngOnDestroy() {
    if (this.searchSubscription) {
      this.searchSubscription.unsubscribe();
    }
  }

  onSearchInput(value: string) {
    if (value.trim().length > 0) {
      this.showDropdown = true;
      this.searchSubject.next(value);
    } else {
      this.quickSearchResults = [];
      this.showDropdown = false;
    }
  }

  onFocus() {
    if (this.searchQuery.trim().length > 0 && this.quickSearchResults.length > 0) {
      this.showDropdown = true;
    }
  }

  onBlur() {
    setTimeout(() => {
      this.showDropdown = false;
      this.cdr.detectChanges();
    }, 150);
  }

  goToBook(id: string | number) {
    this.router.navigate(['/book', id]);
  }

  highlightMatch(text: string): string {
    if (!this.searchQuery || !text) return text;
    const query = this.searchQuery.trim().replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
    const regex = new RegExp(`(${query})`, 'gi');
    return text.replace(regex, '<strong>$1</strong>');
  }

  fetchCategories() {
    this.categoryService.getCategories().subscribe({
      next: (cats: Category[]) => {
        this.availableCategories = cats.map(c => c.name);
        this.availableCategories.forEach(cat => this.selectedCategories[cat] = false);
        this.cdr.detectChanges();
      },
      error: (err) => console.error('Error fetching categories', err)
    });
  }

  fetchBooks() {
    this.bookService.getBooks(this.searchQuery, this.currentPage, this.pageSize).subscribe({
      next: (data) => {
        if (data && data.content && data.content.length > 0) {
          this.allBooks = data.content.map((b: any) => ({
            id: b.bookId || b.id,
            title: b.title,
            author: b.author || 'Đang cập nhật',
            cover: b.imageUrlL || b.imageUrlM || b.imageUrlS || b.cover || '/assets/images/cover_1.jpg',
            category: b.category ? b.category.name : 'Khác',
            status: b.status || 'available'
          }));
          this.totalPages = data.totalPages;
          this.totalElements = data.totalElements;
        } else {
          this.allBooks = [];
          this.totalPages = 0;
          this.totalElements = 0;
        }
        this.applyFilters();
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error fetching books', err);
        this.allBooks = [];
        this.applyFilters();
        this.cdr.detectChanges();
      }
    });
  }

  goToPage(page: number) {
    if (page >= 0 && page < this.totalPages) {
      this.currentPage = page;
      this.fetchBooks();
    }
  }

  nextPage() {
    this.goToPage(this.currentPage + 1);
  }

  prevPage() {
    this.goToPage(this.currentPage - 1);
  }

  onSearch() {
    this.showDropdown = false;
    this.currentPage = 0;
    this.fetchBooks();
  }

  applyFilters() {
    let filtered = [...this.allBooks];

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
