import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import type { LocationNode } from './location.service';

export interface ImportLineDraft {
  mode: 'isbn' | 'bookId';
  identifier: string;
  title: string;
  qty: number;
  price: number;
  bookId?: number;
  lookupState?: 'idle' | 'loading' | 'found' | 'missing';
  lookupMessage?: string;
  identification?: CatalogIdentification;
  requestVersion: number;
  floorId?: number; bookshelfId?: number; shelfId?: number;
  bookshelves: LocationNode[]; shelves: LocationNode[];
  locationLoading: boolean; locationError: string;
}
export interface CatalogIdentification {
  bookId: number; title: string; isbn: string | null; canonicalIsbn: string | null;
  author: string | null; publisher: string | null; publishYear: number | null; defaultPrice: number | null;
  categoryId: number | null; categoryName: string | null; ddcCallNumber: string | null;
  subjects: { code: string; name: string; confidence: number; source: string; evidence: string | null; primary: boolean }[];
  classificationStatus: 'MISSING' | 'AVAILABLE'; placementStatus: 'WAITING_ADMIN_RULES'; suggestedShelfId: null; placementMessage: string;
}
export interface ImportReceiptRequest {
  supplierId: number;
  lines: { bookId: number; isbn?: string; quantity: number; unitPrice: number; shelfId: number; floorId?: number; bookshelfId?: number }[];
}
export interface ReceiptLine {
  detailId: number; bookId: number; title: string | null; isbn: string | null; quantity: number; unitPrice: number;
  shelfId: number | null; locationPath: string | null; barcodes: string[];
}
export interface ImportReceipt {
  receiptId: number; receiptCode: string; createdAt: string; supplierId: number; supplierName: string;
  quantity: number; totalAmount: number; status: string; lines?: ReceiptLine[] | null;
}
export interface ImportStats { totalImports: number; booksReceived: number; totalSpent: number; pending: number; }

@Injectable({
  providedIn: 'root'
})
export class ImportsService {
  private apiUrl = `${environment.apiUrl}/inventory`;

  constructor(private http: HttpClient) { }

  getStats(): Observable<ImportStats> {
    return this.http.get<ImportStats>(`${this.apiUrl}/stats`);
  }

  getImportReceipts(): Observable<ImportReceipt[]> {
    return this.http.get<ImportReceipt[]>(`${this.apiUrl}/receipts`);
  }

  getSuppliers(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/suppliers`);
  }

  saveImport(importData: ImportReceiptRequest): Observable<ImportReceipt> {
    return this.http.post<ImportReceipt>(`${this.apiUrl}/receipts`, importData);
  }
  getReceipt(id: number): Observable<ImportReceipt> { return this.http.get<ImportReceipt>(`${this.apiUrl}/receipts/${id}`); }
  identify(mode: 'isbn' | 'bookId', value: string): Observable<CatalogIdentification> {
    return this.http.get<CatalogIdentification>(`${this.apiUrl}/catalog-identification`, { params: { [mode]: value.trim() } });
  }

  findBookByIsbn(isbn: string): Observable<any> {
    return this.http.get<any>(`${environment.apiUrl}/books/isbn/${encodeURIComponent(isbn.trim())}`);
  }
}
