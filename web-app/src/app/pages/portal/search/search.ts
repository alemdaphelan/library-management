import { Component, OnInit, ChangeDetectorRef, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { BookService } from '../../../services/book.service';
import { CategoryService, Category } from '../../../services/category.service';
import { Subject, Subscription, of } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, catchError } from 'rxjs/operators';
import { ActivatedRoute, Router } from '@angular/router';
import { HomeService, RecommendationResponse } from '../../../services/home.service';

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
  sourceMode: 'catalog' | 'for-you' = 'catalog';
  recommendationLoading = false;

  // Filtering & Sorting State
  searchQuery: string = '';
  filterStatus: string = 'all'; // 'all', 'available', 'borrowed'
  availableCategories: string[] = [];
  selectedCategories: { [key: string]: boolean } = {};
  sortOption: string = 'newest';

  // Pagination State
  currentPage: number = 0;
  pageSize: number = 12;
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
    private router: Router,
    private route: ActivatedRoute,
    private homeService: HomeService
  ) {}

  ngOnInit() {
    this.searchQuery = this.route.snapshot.queryParamMap.get('query')?.trim() || '';
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
      this.cdr.markForCheck();
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
      this.cdr.markForCheck();
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
        this.cdr.markForCheck();
      },
      error: (err) => console.error('Error fetching categories', err)
    });
  }

  fetchBooks() {
    if (this.sourceMode === 'for-you') {
      this.fetchRecommendations();
      return;
    }
    const availability = this.filterStatus === 'available'
      ? 'AVAILABLE'
      : this.filterStatus === 'borrowed' ? 'UNAVAILABLE' : undefined;
    this.bookService.getBooks(
      this.searchQuery,
      this.currentPage,
      this.pageSize,
      undefined,
      availability
    ).subscribe({
      next: (data) => {
        if (data && data.content && data.content.length > 0) {
          this.allBooks = data.content.map((b: any) => ({
            id: b.bookId || b.id,
            title: b.title,
            author: b.author || 'Đang cập nhật',
            cover: b.imageUrlL || b.imageUrlM || b.imageUrlS || b.cover || '/assets/images/cover_1.jpg',
            category: b.category ? b.category.name : 'Khác',
            status: b.status || 'out_of_stock',
            availableCopies: Number(b.availableCopies || 0),
            copies: Number(b.copies || 0)
          }));
          this.totalPages = data.totalPages;
          this.totalElements = data.totalElements;
        } else {
          this.allBooks = [];
          this.totalPages = 0;
          this.totalElements = 0;
        }
        this.applyFilters();
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Error fetching books', err);
        this.allBooks = [];
        this.applyFilters();
        this.cdr.markForCheck();
      }
    });
  }

  fetchRecommendations(): void {
    this.recommendationLoading = true;
    this.homeService.getRecommendedBooks(50).subscribe({
      next: (response: RecommendationResponse) => {
        this.allBooks = (response.items ?? []).map((book, index) => ({
          id: book.bookId,
          title: book.title,
          author: book.author || 'Đang cập nhật',
          cover: book.imageUrl || '/assets/images/cover_1.jpg',
          category: book.categoryName || 'Khác',
          status: book.status || 'out_of_stock',
          availableCopies: Number(book.availableCopies || 0),
          copies: Number(book.copies || 0),
          reason: book.reason,
          recommendationOrder: index
        }));
        this.recommendationLoading = false;
        this.currentPage = 0;
        this.applyFilters();
        this.cdr.markForCheck();
      },
      error: () => {
        this.allBooks = [];
        this.recommendationLoading = false;
        this.applyFilters();
        this.cdr.markForCheck();
      }
    });
  }

  setSourceMode(mode: 'catalog' | 'for-you'): void {
    if (this.sourceMode === mode) return;
    this.sourceMode = mode;
    this.currentPage = 0;
    this.fetchBooks();
  }

  goToPage(page: number) {
    if (page >= 0 && page < this.totalPages) {
      this.currentPage = page;
      if (this.sourceMode === 'catalog') this.fetchBooks();
      else this.applyFilters();
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
    if (this.sourceMode === 'catalog') this.fetchBooks();
    else this.applyFilters();
  }

  onStatusChange(): void {
    this.currentPage = 0;
    if (this.sourceMode === 'catalog') this.fetchBooks();
    else this.applyFilters();
  }

  applyFilters() {
    let filtered = [...this.allBooks];

    if (this.sourceMode === 'for-you') {
      const query = this.searchQuery.trim().toLocaleLowerCase('vi');
      if (query) {
        filtered = filtered.filter(book => [book.title, book.author, book.category, book.reason]
          .some(value => String(value || '').toLocaleLowerCase('vi').includes(query)));
      }
      if (this.filterStatus === 'available') filtered = filtered.filter(book => book.status === 'available');
      if (this.filterStatus === 'borrowed') filtered = filtered.filter(book => book.status !== 'available');
    }

    // Category filter
    const activeCategories = Object.keys(this.selectedCategories).filter(c => this.selectedCategories[c]);
    if (activeCategories.length > 0) {
      filtered = filtered.filter(b => activeCategories.includes(b.category));
    }

    // Sorting
    if (this.sourceMode === 'for-you' && this.sortOption === 'newest') {
      filtered.sort((a, b) => a.recommendationOrder - b.recommendationOrder);
    } else if (this.sortOption === 'newest') {
      filtered.sort((a, b) => b.id - a.id);
    } else if (this.sortOption === 'az') {
      filtered.sort((a, b) => a.title.localeCompare(b.title));
    } else if (this.sortOption === 'za') {
      filtered.sort((a, b) => b.title.localeCompare(a.title));
    }

    if (this.sourceMode === 'for-you') {
      this.totalElements = filtered.length;
      this.totalPages = Math.ceil(filtered.length / this.pageSize);
      const start = this.currentPage * this.pageSize;
      this.books = filtered.slice(start, start + this.pageSize);
    } else {
      this.books = filtered;
    }
  }

  clearFilters() {
    this.searchQuery = '';
    this.filterStatus = 'all'; 
    
    // Re-assign object to trigger change detection for ngModel bindings
    const clearedCategories: { [key: string]: boolean } = {};
    this.availableCategories.forEach(c => clearedCategories[c] = false);
    this.selectedCategories = clearedCategories;
    
    this.sortOption = 'newest';
    this.currentPage = 0;
    this.fetchBooks();
    this.cdr.markForCheck();
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
