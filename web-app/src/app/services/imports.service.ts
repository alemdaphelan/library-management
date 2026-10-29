import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ImportLineDraft {
  isbn: string;
  title: string;
  qty: number;
  price: number;
  bookId?: number;
  lookupState?: 'idle' | 'loading' | 'found' | 'missing';
  lookupMessage?: string;
}

@Injectable({
  providedIn: 'root'
})
export class ImportsService {
  private apiUrl = `${environment.apiUrl}/inventory`;

  constructor(private http: HttpClient) { }

  getStats(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/stats`);
  }

  getImportReceipts(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/receipts`);
  }

  getSuppliers(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/suppliers`);
  }

  saveImport(importData: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/receipts`, importData);
  }

  findBookByIsbn(isbn: string): Observable<any> {
    return this.http.get<any>(`${environment.apiUrl}/books/isbn/${encodeURIComponent(isbn.trim())}`);
  }
}
