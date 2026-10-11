import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class BookService {
  private apiUrl = `${environment.apiUrl}/books`;

  constructor(private http: HttpClient) { }

  getBooks(query?: string, page: number = 0, size: number = 10): Observable<any> {
    const url = query 
      ? `${this.apiUrl}?query=${query}&page=${page}&size=${size}` 
      : `${this.apiUrl}?page=${page}&size=${size}`;
    return this.http.get<any>(url);
  }

  getBookById(id: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/${id}`);
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
}
