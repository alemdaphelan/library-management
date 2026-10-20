import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export interface RecommendationItem {
  bookId: number;
  title: string;
  author: string;
  publisher?: string | null;
  categoryName?: string | null;
  imageUrl?: string | null;
  score: number;
  contentScore: number;
  departmentScore: number;
  popularityScore: number;
  borrowCount: number;
  reason: string;
}

export interface RecommendationResponse {
  method: 'HYBRID_PERSONALIZED' | 'HYBRID_COLD_START' | 'POPULARITY_BASELINE' | string;
  dataSufficient: boolean;
  candidateCount: number;
  generatedAt: string;
  items: RecommendationItem[];
}

@Injectable({
  providedIn: 'root'
})
export class HomeService {
  private booksUrl = `${environment.apiUrl}/books`;

  constructor(private http: HttpClient) { }

  getPopularRecommendations(limit: number = 10): Observable<RecommendationResponse> {
    return this.http.get<RecommendationResponse>(`${this.booksUrl}/recommendations/popular`, {
      params: { limit: limit.toString() }
    });
  }

  getRecommendedBooks(limit: number = 10): Observable<RecommendationResponse> {
    const hasToken = !!localStorage.getItem('huit_lib_token');
    if (!hasToken) {
      return this.getPopularRecommendations(limit);
    }

    return this.http.get<RecommendationResponse>(`${this.booksUrl}/recommendations/personalized`, {
      params: { limit: limit.toString() }
    }).pipe(
      catchError(() => this.getPopularRecommendations(limit))
    );
  }
}
