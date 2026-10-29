import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type InventoryAuditStatus = 'IN_PROGRESS' | 'COMPLETED';
export type InventoryScanStatus = 'MATCHED' | 'MISPLACED' | 'FOUND' | 'UNKNOWN';

export interface InventoryScan {
  detailId: string;
  auditId: number;
  barcode: string;
  scanStatus: InventoryScanStatus;
  expectedLocation: string | null;
  scannedLocation: string | null;
  bookId: number | null;
  bookTitle: string | null;
  copyStatus: string | null;
  scannedAt: string;
}

export interface InventoryAudit {
  auditId: number;
  conductedBy: string;
  totalSystemBooks: number;
  totalActualBooks: number;
  status: InventoryAuditStatus;
  createdAt: string;
  updatedAt: string;
  details: InventoryScan[];
}

@Injectable({ providedIn: 'root' })
export class InventoryAuditService {
  private readonly apiUrl = `${environment.apiUrl}/inventory/audits`;

  constructor(private readonly http: HttpClient) {}

  getAudits(): Observable<InventoryAudit[]> {
    return this.http.get<InventoryAudit[]>(this.apiUrl);
  }

  getAudit(auditId: number): Observable<InventoryAudit> {
    return this.http.get<InventoryAudit>(`${this.apiUrl}/${auditId}`);
  }

  createAudit(): Observable<InventoryAudit> {
    return this.http.post<InventoryAudit>(this.apiUrl, {});
  }

  scan(auditId: number, barcode: string, scannedLocation?: string): Observable<InventoryScan> {
    return this.http.post<InventoryScan>(`${this.apiUrl}/${auditId}/scans`, {
      barcode,
      scannedLocation: scannedLocation || null,
    });
  }

  complete(auditId: number): Observable<InventoryAudit> {
    return this.http.post<InventoryAudit>(`${this.apiUrl}/${auditId}/complete`, {});
  }
}
