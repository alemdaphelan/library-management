import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type LocationType = 'FLOOR' | 'BOOKSHELF' | 'SHELF';

export interface LocationNode {
  id: number;
  name: string;
  type: LocationType;
  parentId?: number | null;
  parentName?: string | null;
}

@Injectable({ providedIn: 'root' })
export class LocationService {
  private readonly apiUrl = `${environment.apiUrl}/locations`;

  constructor(private http: HttpClient) {}

  getFloors(): Observable<LocationNode[]> {
    return this.http.get<LocationNode[]>(`${this.apiUrl}/floors`);
  }

  createFloor(name: string): Observable<LocationNode> {
    return this.http.post<LocationNode>(`${this.apiUrl}/floors`, { name });
  }

  updateFloor(id: number, name: string): Observable<LocationNode> {
    return this.http.put<LocationNode>(`${this.apiUrl}/floors/${id}`, { name });
  }

  deleteFloor(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/floors/${id}`);
  }

  getBookshelves(floorId: number): Observable<LocationNode[]> {
    const params = new HttpParams().set('floorId', floorId);
    return this.http.get<LocationNode[]>(`${this.apiUrl}/bookshelves`, { params });
  }

  createBookshelf(name: string, floorId: number): Observable<LocationNode> {
    return this.http.post<LocationNode>(`${this.apiUrl}/bookshelves`, { name, parentId: floorId });
  }

  updateBookshelf(id: number, name: string, floorId: number): Observable<LocationNode> {
    return this.http.put<LocationNode>(`${this.apiUrl}/bookshelves/${id}`, { name, parentId: floorId });
  }

  deleteBookshelf(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/bookshelves/${id}`);
  }

  getShelves(bookshelfId: number): Observable<LocationNode[]> {
    const params = new HttpParams().set('bookshelfId', bookshelfId);
    return this.http.get<LocationNode[]>(`${this.apiUrl}/shelves`, { params });
  }

  createShelf(name: string, bookshelfId: number): Observable<LocationNode> {
    return this.http.post<LocationNode>(`${this.apiUrl}/shelves`, { name, parentId: bookshelfId });
  }

  updateShelf(id: number, name: string, bookshelfId: number): Observable<LocationNode> {
    return this.http.put<LocationNode>(`${this.apiUrl}/shelves/${id}`, { name, parentId: bookshelfId });
  }

  deleteShelf(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/shelves/${id}`);
  }
}
