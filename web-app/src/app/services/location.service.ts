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
  capacity?: number | null;
}

export interface ShelfBook {
  barcode: string;
  bookId: number | null;
  title: string;
  isbn?: string;
  status: string;
  catalogMatched?: boolean;
}
export interface ShelfBooksPage {
  items: ShelfBook[];
  total: number;
  page: number;
  size: number;
}

export interface ShelfSubject {
  subjectId: number;
  code: string;
  name: string;
  description?: string | null;
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
    return this.http.put<LocationNode>(`${this.apiUrl}/bookshelves/${id}`, {
      name,
      parentId: floorId,
    });
  }

  deleteBookshelf(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/bookshelves/${id}`);
  }

  getShelves(bookshelfId: number): Observable<LocationNode[]> {
    const params = new HttpParams().set('bookshelfId', bookshelfId);
    return this.http.get<LocationNode[]>(`${this.apiUrl}/shelves`, { params });
  }

  getShelfBooks(shelfId: number): Observable<ShelfBook[]> {
    return this.http.get<ShelfBook[]>(`${this.apiUrl}/shelves/${shelfId}/books`);
  }

  getShelfBooksPage(shelfId: number, page = 0): Observable<ShelfBooksPage> {
    return this.http.get<ShelfBooksPage>(`${this.apiUrl}/shelves/${shelfId}/books/page`, {
      params: { page, size: 50 },
    });
  }
  getUnplacedCopies(page = 0, targetShelf?: number): Observable<ShelfBooksPage> {
    const params = new HttpParams().set('page', page).set('size', 50);
    return this.http.get<ShelfBooksPage>(`${this.apiUrl}/unplaced-copies`, {
      params: targetShelf ? params.set('targetShelf', targetShelf) : params,
    });
  }
  placeCopy(barcode: string, shelfId: number): Observable<unknown> {
    return this.http.put(
      `${environment.apiUrl}/book-copies/${encodeURIComponent(barcode)}/location`,
      null,
      { params: { shelfId } },
    );
  }

  getAvailableSubjects(): Observable<ShelfSubject[]> {
    return this.http.get<ShelfSubject[]>(`${this.apiUrl}/subjects`);
  }

  getShelfSubjects(shelfId: number): Observable<ShelfSubject[]> {
    return this.http.get<ShelfSubject[]>(`${this.apiUrl}/shelves/${shelfId}/subjects`);
  }

  updateShelfSubjects(shelfId: number, subjectIds: number[]): Observable<ShelfSubject[]> {
    return this.http.put<ShelfSubject[]>(`${this.apiUrl}/shelves/${shelfId}/subjects`, {
      subjectIds,
    });
  }

  createShelf(name: string, bookshelfId: number): Observable<LocationNode> {
    return this.http.post<LocationNode>(`${this.apiUrl}/shelves`, { name, parentId: bookshelfId });
  }

  updateShelf(
    id: number,
    name: string,
    bookshelfId: number,
    capacity?: number,
  ): Observable<LocationNode> {
    return this.http.put<LocationNode>(`${this.apiUrl}/shelves/${id}`, {
      name,
      parentId: bookshelfId,
      capacity,
    });
  }

  deleteShelf(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/shelves/${id}`);
  }
}
