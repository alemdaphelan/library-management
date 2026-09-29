import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class BookService {
  private apiUrl = 'http://127.0.0.1:8080/books';

  constructor(private http: HttpClient) { }

  getBooks(query?: string): Observable<any[]> {
    const url = query ? `${this.apiUrl}?query=${query}` : this.apiUrl;
    return this.http.get<any[]>(url);
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
