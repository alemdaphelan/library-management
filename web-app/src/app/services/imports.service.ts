import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ImportsService {
  private apiUrl = `${environment.apiUrl}/imports`;

  constructor(private http: HttpClient) { }

  getStats(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/stats`);
  }

  getImportReceipts(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/receipts`);
  }

  getSuppliers(): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiUrl}/suppliers`);
  }

  saveImport(importData: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/receipts`, importData);
  }
}
