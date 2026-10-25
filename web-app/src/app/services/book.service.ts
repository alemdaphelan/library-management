import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';

export interface BookReview {
  reviewId: number;
  reviewerName: string;
  rating: number;
  comment?: string;
  createdAt?: string;
  updatedAt?: string;
  mine: boolean;
}

export interface BookReviewSummary {
  bookId: number;
  averageRating: number;
  reviewCount: number;
  myReview?: BookReview;
  reviewEligible: boolean;
  reviewEligibilityMessage?: string;
  reviews: BookReview[];
}

export interface WishlistItem {
  wishlistId: string;
  bookId: number;
  title: string;
  author?: string;
  category?: string;
  cover?: string;
  availableCopies: number;
  addedAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class BookService {
  private apiUrl = `${environment.apiUrl}/books`;

  constructor(private http: HttpClient) { }

  getBooks(
    query?: string,
    page: number = 0,
    size: number = 10,
    categoryId?: number,
    availability?: 'AVAILABLE' | 'UNAVAILABLE'
  ): Observable<any> {
    const params: Record<string, string> = {
      page: page.toString(),
      size: size.toString()
    };
    if (query?.trim()) params['query'] = query.trim();
    if (categoryId) params['categoryId'] = categoryId.toString();
    if (availability) params['availability'] = availability;
    return this.http.get<any>(this.apiUrl, { params });
  }

  getBookById(id: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/${id}`);
  }

  getBookByIsbn(isbn: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/isbn/${encodeURIComponent(isbn.trim())}`);
  }

  createBook(book: any): Observable<any> {
    return this.http.post<any>(this.apiUrl, book);
  }

  updateBook(id: string, book: any): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/${id}`, book);
  }

  deleteBook(id: string): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/${id}`);
  }

  getReviews(id: number): Observable<BookReviewSummary> {
    return this.http.get<BookReviewSummary>(`${this.apiUrl}/${id}/reviews`);
  }

  saveReview(id: number, rating: number, comment: string): Observable<BookReviewSummary> {
    return this.http.post<BookReviewSummary>(`${this.apiUrl}/${id}/reviews`, { rating, comment });
  }

  deleteMyReview(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}/reviews/me`);
  }

  getWishlist(): Observable<WishlistItem[]> {
    return this.http.get<WishlistItem[]>(`${environment.apiUrl}/users/wishlist`);
  }

  getWishlistStatus(bookId: number): Observable<{ saved: boolean }> {
    return this.http.get<{ saved: boolean }>(`${environment.apiUrl}/users/wishlist/${bookId}/status`);
  }

  addToWishlist(bookId: number): Observable<WishlistItem> {
    return this.http.post<WishlistItem>(`${environment.apiUrl}/users/wishlist`, { bookId });
  }

  removeFromWishlist(bookId: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/users/wishlist/${bookId}`);
  }
}
