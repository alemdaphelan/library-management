import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class DigitalAssetsService {
  private apiUrl = `${environment.apiUrl}/digital-assets`;

  constructor(private http: HttpClient) { }

  getAssets(): Observable<any[]> {
    return this.http.get<any[]>(this.apiUrl);
  }

  deleteAsset(id: string): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/${id}`);
  }

  uploadAsset(file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<any>(`${this.apiUrl}/upload`, formData, {
      reportProgress: true,
      observe: 'events'
    });
  }
}
