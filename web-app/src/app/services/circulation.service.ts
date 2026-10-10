import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class CirculationService {
  private apiUrl = `${environment.apiUrl}/loans`;
  private bookCopyApiUrl = `${environment.apiUrl}/book-copies`;

  constructor(private http: HttpClient) { }

  checkout(studentId: string, bookBarcodes: string[]): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/checkout`, { studentId, bookBarcodes });
  }

  returnBooks(bookBarcodes: string[]): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/return`, { bookBarcodes });
  }
  
  getBookCopyByBarcode(barcode: string): Observable<any> {
    // Assuming backend has this, if not we will just map it
    return this.http.get<any>(`${this.bookCopyApiUrl}/${barcode}`);
  }
}
