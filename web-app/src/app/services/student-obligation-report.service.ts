import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AcademicFaculty, ClassificationCoverage, ObligationFilters, StudentObligationReport } from './student-obligation-report.models';

@Injectable({ providedIn: 'root' })
export class StudentObligationReportService {
  private readonly api = `${environment.apiUrl}/api/v1/librarian-reports/student-obligations`;
  constructor(private http: HttpClient) {}
  faculties(): Observable<AcademicFaculty[]> { return this.http.get<AcademicFaculty[]>(`${this.api}/faculties`); }
  coverage(): Observable<ClassificationCoverage> { return this.http.get<ClassificationCoverage>(`${this.api}/classification-coverage`); }
  preview(filters: ObligationFilters, page: number, size: number): Observable<StudentObligationReport> {
    return this.http.get<StudentObligationReport>(this.api, { params: this.params(filters).set('page', page).set('size', size) });
  }
  export(filters: ObligationFilters): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.api}/export`, { params: this.params(filters), observe: 'response', responseType: 'blob' });
  }
  private params(filters: ObligationFilters): HttpParams {
    let params = new HttpParams().set('mode', filters.mode).set('q', filters.q.trim());
    if (filters.facultyId) params = params.set('facultyId', filters.facultyId);
    return params;
  }
}
