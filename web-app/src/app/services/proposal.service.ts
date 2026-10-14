import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

export interface BookProposal {
  proposalId?: number;
  userId?: string;
  userName?: string;
  userMssv?: string;
  userEmail?: string;
  title: string;
  authorName?: string;
  publisher?: string;
  reason?: string;
  note?: string;
  adminNote?: string;
  status?: 'PENDING' | 'APPROVED' | 'REJECTED';
  createdAt?: string;
  updatedAt?: string;
}

@Injectable({
  providedIn: 'root'
})
export class ProposalService {
  private apiUrl = `${environment.apiUrl}/proposals`;

  constructor(private http: HttpClient, private authService: AuthService) {}

  private getAuthHeaders() {
    const token = this.authService.getToken();
    return {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    };
  }

  submitProposal(proposal: Partial<BookProposal>): Observable<BookProposal> {
    return this.http.post<BookProposal>(this.apiUrl, proposal, this.getAuthHeaders());
  }

  getMyProposals(): Observable<BookProposal[]> {
    return this.http.get<BookProposal[]>(`${this.apiUrl}/my`, this.getAuthHeaders());
  }

  getAllProposalsForAdmin(): Observable<BookProposal[]> {
    return this.http.get<BookProposal[]>(`${this.apiUrl}/admin`, this.getAuthHeaders());
  }

  updateProposalStatus(id: number, status: 'APPROVED' | 'REJECTED', adminNote?: string): Observable<BookProposal> {
    return this.http.put<BookProposal>(`${this.apiUrl}/admin/${id}/status`, { status, adminNote }, this.getAuthHeaders());
  }
}
