import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface OrientationPerson { userId: string; fullName: string; mssv?: string; email?: string; }
export interface OrientationEnrollment { enrollmentId: number; student: OrientationPerson; status: string; note?: string; }
export interface OrientationClass {
  classId: number; classCode: string; title: string; location: string; startAt: string; endAt: string;
  capacity: number; facilitatorId?: string; facilitatorName?: string; status: string;
  enrolledCount: number; enrollments: OrientationEnrollment[];
}

@Injectable({ providedIn: 'root' })
export class OrientationService {
  private api = `${environment.apiUrl}/orientation-classes`;
  constructor(private http: HttpClient) {}
  list(): Observable<OrientationClass[]> { return this.http.get<OrientationClass[]>(this.api); }
  create(request: any): Observable<OrientationClass> { return this.http.post<OrientationClass>(this.api, request); }
  update(id: number, request: any): Observable<OrientationClass> { return this.http.put<OrientationClass>(`${this.api}/${id}`, request); }
  delete(id: number): Observable<void> { return this.http.delete<void>(`${this.api}/${id}`); }
  availableStudents(id: number): Observable<OrientationPerson[]> { return this.http.get<OrientationPerson[]>(`${this.api}/${id}/available-students`); }
  facilitators(startAt: string, endAt: string): Observable<OrientationPerson[]> { return this.http.get<OrientationPerson[]>(`${this.api}/available-facilitators`, { params: { startAt, endAt } }); }
  assign(id: number, userIds: string[], randomCount = 0): Observable<OrientationClass> { return this.http.post<OrientationClass>(`${this.api}/${id}/assign`, { userIds, randomCount }); }
  attendance(id: number, items: any[]): Observable<OrientationClass> { return this.http.post<OrientationClass>(`${this.api}/${id}/attendance`, { items }); }
  template(id: number): Observable<HttpResponse<Blob>> { return this.http.get(`${this.api}/${id}/attendance-template`, { observe: 'response', responseType: 'blob' }); }
  importAttendance(id: number, file: File): Observable<OrientationClass> { const form = new FormData(); form.append('file', file); return this.http.post<OrientationClass>(`${this.api}/${id}/attendance-import`, form); }
}
