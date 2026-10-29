import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AcademicFaculty } from './student-obligation-report.models';

export type ProposalStatusFilter = 'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED';
export interface ProposalSummaryFilters { facultyId: string; from: string; to: string; status: ProposalStatusFilter; }
export interface ProposalSource {
  proposalId: number; userId: string | null; userName: string | null; mssv: string | null; rawDepartment: string | null;
  facultyId: string; facultyName: string; title: string; authorName: string | null; publisher: string | null;
  isbn: string | null; publicationYear: number | null; edition: string | null; status: string; createdAt: string | null;
}
export interface ProposalGroup {
  groupKey: string; identityType: string; canonicalIsbn: string | null; title: string; authorName: string | null;
  publisher: string | null; publicationYear: number | null; edition: string | null; proposalCount: number; peopleCount: number;
  faculties: { facultyId: string; facultyName: string; proposalCount: number; peopleCount: number }[];
  warnings: string[]; sources: ProposalSource[];
}
export interface ProposalSummaryReport {
  snapshotAt: string; timezone: string; filter: { facultyId: string | null; from: string | null; to: string | null; status: ProposalStatusFilter };
  page: number; size: number; totalGroups: number; totalProposals: number; totalPeople: number; groups: ProposalGroup[];
}
@Injectable({ providedIn: 'root' })
export class ProposalSummaryService {
  private readonly api = `${environment.apiUrl}/proposals/admin/summary`;
  constructor(private http: HttpClient) {}
  faculties(): Observable<AcademicFaculty[]> { return this.http.get<AcademicFaculty[]>(`${this.api}/faculties`); }
  preview(filters: ProposalSummaryFilters, page: number, size: number): Observable<ProposalSummaryReport> {
    return this.http.get<ProposalSummaryReport>(this.api, { params: this.params(filters).set('page', page).set('size', size) });
  }
  export(filters: ProposalSummaryFilters): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.api}/export`, { params: this.params(filters), observe: 'response', responseType: 'blob' });
  }
  private params(filters: ProposalSummaryFilters): HttpParams {
    let params = new HttpParams().set('status', filters.status);
    if (filters.facultyId) params = params.set('facultyId', filters.facultyId);
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    return params;
  }
}
