import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';

export interface Student {
  userId: string;
  fullName: string;
  mssv: string;
  department?: string;
  email: string;
  phone?: string;
  cardStatus?: string;
  cardExpiryDate?: string;
}

export interface StudentUpsert {
  mssv: string;
  fullName: string;
  email: string;
  department?: string;
  phone?: string;
  cardStatus: 'ACTIVE' | 'LOCKED' | 'EXPIRED';
  cardExpiryDate?: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private apiUrl = `${environment.apiUrl}/users`;

  constructor(private http: HttpClient) { }

  getStudents(): Observable<Student[]> {
    return this.http.get<Student[]>(`${this.apiUrl}/students`);
  }

  getStudentById(id: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/students/${id}`);
  }

  createStudent(student: StudentUpsert): Observable<Student> {
    return this.http.post<Student>(`${this.apiUrl}/students`, student);
  }

  updateStudent(mssv: string, student: StudentUpsert): Observable<Student> {
    return this.http.put<Student>(`${this.apiUrl}/students/${encodeURIComponent(mssv)}`, student);
  }

  deleteStudent(mssv: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/students/${encodeURIComponent(mssv)}`);
  }
}
