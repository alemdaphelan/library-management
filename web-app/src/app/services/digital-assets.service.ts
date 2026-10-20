import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class DigitalAssetsService {
  private apiUrl = `${environment.apiUrl}/digital-assets`;

  constructor(private http: HttpClient) { }

  getAssets(): Observable<DigitalAsset[]> {
    return this.http.get<DigitalAsset[]>(this.apiUrl);
  }

  deleteAsset(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  uploadAsset(file: File, bookId?: number | null, accessLevel = 'AUTHENTICATED'): Observable<HttpEvent<DigitalAsset>> {
    const formData = new FormData();
    formData.append('file', file);
    if (bookId) formData.append('bookId', String(bookId));
    formData.append('accessLevel', accessLevel);
    return this.http.post<DigitalAsset>(`${this.apiUrl}/upload`, formData, {
      reportProgress: true,
      observe: 'events'
    });
  }

  updateAsset(id: number, request: DigitalAssetUpdate): Observable<DigitalAsset> {
    return this.http.put<DigitalAsset>(`${this.apiUrl}/${id}`, request);
  }

  downloadAsset(id: number): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.apiUrl}/${id}/download`, {
      observe: 'response',
      responseType: 'blob'
    });
  }
}

export interface DigitalAsset {
  id: number;
  bookId?: number | null;
  bookTitle?: string | null;
  name: string;
  type: string;
  contentType?: string;
  size: number;
  accessLevel: 'AUTHENTICATED' | 'STAFF_ONLY';
  uploadedBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DigitalAssetUpdate {
  bookId?: number | null;
  displayName: string;
  accessLevel: 'AUTHENTICATED' | 'STAFF_ONLY';
}
