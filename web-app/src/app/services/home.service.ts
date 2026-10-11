import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class HomeService {
  private apiUrl = `${environment.apiUrl}/home`;

  constructor(private http: HttpClient) { }

  getFeaturedBooks(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/featured-books`);
  }

  getRecommendedBooks(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/recommended-books`);
  }
}
