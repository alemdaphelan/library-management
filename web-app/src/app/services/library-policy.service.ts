import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface LibraryPolicy {
  maximumOpenItems: number;
  homeLoanDays: number;
  renewalDays: number;
  maximumRenewalCount: number;
  waitlistHoldHours: number;
  homeLateFinePerDay: number;
  onsiteLateFinePerDay: number;
  technicalProcessingFee: number;
  compensationSettlementDays: number;
  updatedBy?: string | null;
  updatedAt?: string | null;
}

export interface LibraryPolicyHistory extends LibraryPolicy {
  historyId: number;
  changedBy?: string | null;
  changedByName: string;
  changedByEmail?: string | null;
  changedAt: string;
}

@Injectable({ providedIn: 'root' })
export class LibraryPolicyService {
  private readonly apiUrl = `${environment.apiUrl}/admin/library-policy`;
  private readonly publicApiUrl = `${environment.apiUrl}/library-policy`;

  constructor(private http: HttpClient) {}

  getPolicy(): Observable<LibraryPolicy> {
    return this.http.get<LibraryPolicy>(this.apiUrl);
  }

  getPublicPolicy(): Observable<LibraryPolicy> {
    return this.http.get<LibraryPolicy>(this.publicApiUrl);
  }

  getHistory(): Observable<LibraryPolicyHistory[]> {
    return this.http.get<LibraryPolicyHistory[]>(`${this.apiUrl}/history`);
  }

  updatePolicy(policy: LibraryPolicy): Observable<LibraryPolicy> {
    return this.http.put<LibraryPolicy>(this.apiUrl, policy);
  }
}
