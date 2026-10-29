import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../environments/environment';
export type DisposalStatus = 'DRAFT' | 'SUBMITTED' | 'CANCELLED';
export type DisposalAction = 'REPAIR' | 'TRANSFER' | 'LIQUIDATE' | 'DESTROY';
export type DisposalReason = 'WORN' | 'OBSOLETE' | 'UNUSED' | 'DAMAGED';
export interface DisposalSource {
  auditId: number; auditStatus: string; auditUpdatedAt: string | null; detailId: string; scanStatus: string;
  scannedLocation: string | null; scannedAt: string | null; detailUpdatedAt: string | null; barcode: string;
  bookId: number | null; bookTitle: string | null; isbn: string | null; copyStatus: string | null; conditionNote: string | null;
  shelfId: number | null; location: string | null; copyUpdatedAt: string | null; lockedByUser: string | null; lockedUntil: string | null;
}
export interface DisposalCandidate { source: DisposalSource; fingerprint: string; eligible: boolean; blockers: string[]; activeProposalId: string | null; }
export interface DisposalFields { observedCondition: string; reasonCode: DisposalReason; reason: string; proposedAction: DisposalAction; evidenceUrl: string | null; }
export interface DisposalLine extends DisposalFields { barcode: string; sourceDetailId: string; expectedFingerprint: string; }
export interface DisposalCreate { requestId: string; auditId: number; lines: DisposalLine[]; }
export interface DisposalEdit extends DisposalFields { version: number; expectedFingerprint: string; }
export interface DisposalItem { proposalId: string; requestId: string; barcode: string; bookId: number; bookTitle: string; auditId: number; status: DisposalStatus;
  version: number; proposedAction: DisposalAction; createdBy: string; createdByName: string; createdAt: string; updatedAt: string; }
export interface DisposalSnapshot extends DisposalFields { status: DisposalStatus; version: number; source: DisposalSource; }
export interface DisposalHistory { eventId: string; version: number; eventType: string; actorId: string; actorName: string; at: string; before: DisposalSnapshot | null; after: DisposalSnapshot; }
export interface DisposalDetail { item: DisposalItem; saved: DisposalSnapshot; current: DisposalCandidate; canEdit: boolean; canSubmit: boolean; canCancel: boolean;
  adminIntegrationStatus: 'WAITING_ADMIN_WORKFLOW'; history: DisposalHistory[]; }
export interface DisposalList { items: DisposalItem[]; total: number; page: number; size: number; }
export interface DisposalFilters { status: DisposalStatus | ''; auditId: string; barcode: string; }
export interface DisposalBatch { requestId: string; replay: boolean; proposals: DisposalItem[]; }
@Injectable({ providedIn: 'root' })
export class DisposalService {
  private readonly api = `${environment.apiUrl}/inventory/disposals`;
  constructor(private http: HttpClient) {}
  candidates(auditId: number) { return this.http.get<DisposalCandidate[]>(`${this.api}/candidates`, { params: { auditId } }); }
  list(filters: DisposalFilters, page = 0, size = 25) {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.auditId) params = params.set('auditId', filters.auditId);
    if (filters.barcode.trim()) params = params.set('barcode', filters.barcode.trim());
    return this.http.get<DisposalList>(this.api, { params });
  }
  create(request: DisposalCreate) { return this.http.post<DisposalBatch>(this.api, request); }
  get(id: string) { return this.http.get<DisposalDetail>(`${this.api}/${encodeURIComponent(id)}`); }
  edit(id: string, request: DisposalEdit) { return this.http.put<DisposalDetail>(`${this.api}/${encodeURIComponent(id)}`, request); }
  submit(id: string, version: number) { return this.http.post<DisposalDetail>(`${this.api}/${encodeURIComponent(id)}/submit`, { version }); }
  cancel(id: string, version: number) { return this.http.post<DisposalDetail>(`${this.api}/${encodeURIComponent(id)}/cancel`, { version }); }
}
