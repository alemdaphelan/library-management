import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface PenaltyCase {
  caseId: number;
  userId: string;
  ticketId: number;
  loanDetailId: number;
  barcode: string;
  incidentType: 'LOST' | 'DAMAGED';
  damageSeverity?: 'LIGHT' | 'HEAVY';
  description: string;
  settlementMethod: 'REPAIR' | 'REPLACEMENT' | 'CASH';
  repairFee: number;
  compensationAmount: number;
  technicalFee: number;
  overdueFineSnapshot: number;
  totalAmount: number;
  paidAmount: number;
  status: string;
  reportedAt: string;
  settlementDueAt: string;
  replacementBarcode?: string;
}

export interface PenaltyCaseRequest {
  loanDetailId: number;
  incidentType: string;
  damageSeverity?: string;
  pagesAffected?: number;
  description: string;
  evidenceUrl?: string;
  materialType: string;
  languageCategory: string;
  stillInMarket: boolean;
  settlementMethod: string;
  coverPrice?: number;
  originalCurrency?: string;
  fxRate?: number;
  fxSource?: string;
  repairFee?: number;
}

export interface PaymentReceipt {
  paymentTransactionId: number;
  receiptNumber: string;
  userId: string;
  totalAmount: number;
  paymentMethod: string;
  remainingDebt: number;
  allocations: Array<{
    debtTransactionId: number;
    feeType: string;
    reason: string;
    amount: number;
  }>;
}

export interface DebtEntry {
  transactionId: number;
  amount: number;
  paidAmount?: number;
  type: string;
  reason: string;
  status: string;
  createdAt?: string;
}

@Injectable({ providedIn: 'root' })
export class PenaltyService {
  private penaltyUrl = `${environment.apiUrl}/api/v1/penalty-cases`;
  private transactionUrl = `${environment.apiUrl}/api/v1/transactions`;

  constructor(private http: HttpClient) {}

  getCases(): Observable<PenaltyCase[]> {
    return this.http.get<PenaltyCase[]>(this.penaltyUrl);
  }

  getMyCases(): Observable<PenaltyCase[]> {
    return this.http.get<PenaltyCase[]>(`${this.penaltyUrl}/my`);
  }

  getMyLedger(): Observable<DebtEntry[]> {
    return this.http.get<DebtEntry[]>(`${this.transactionUrl}/my-ledger`);
  }

  createCase(payload: PenaltyCaseRequest): Observable<PenaltyCase> {
    return this.http.post<PenaltyCase>(this.penaltyUrl, payload);
  }

  acceptReplacement(caseId: number, replacementBarcode: string, verificationNote: string): Observable<PenaltyCase> {
    return this.http.patch<PenaltyCase>(`${this.penaltyUrl}/${caseId}/replacement-acceptance`, {
      replacementBarcode,
      verificationNote,
    });
  }

  submitReview(caseId: number): Observable<PenaltyCase> {
    return this.http.post<PenaltyCase>(`${this.penaltyUrl}/${caseId}/review-request`, {});
  }

  reviewWaiver(caseId: number, payload: object): Observable<PenaltyCase> {
    return this.http.patch<PenaltyCase>(`${this.penaltyUrl}/${caseId}/review`, payload);
  }

  createRate(payload: object): Observable<object> {
    return this.http.post<object>(`${this.penaltyUrl}/rates`, payload);
  }

  recordPayment(payload: object): Observable<PaymentReceipt> {
    return this.http.post<PaymentReceipt>(`${this.transactionUrl}/payments`, payload);
  }

  requestAdjustment(payload: object): Observable<object> {
    return this.http.post<object>(`${this.transactionUrl}/adjustments`, payload);
  }

  getAdjustments(): Observable<any[]> {
    return this.http.get<any[]>(`${this.transactionUrl}/adjustments`);
  }

  reviewAdjustment(adjustmentId: number, action: 'APPROVE' | 'REJECT', note?: string): Observable<object> {
    return this.http.patch<object>(`${this.transactionUrl}/adjustments/${adjustmentId}`, { action, note });
  }
}
