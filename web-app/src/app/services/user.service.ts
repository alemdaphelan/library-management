import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private apiUrl = `${environment.apiUrl}/users`;

  constructor(private http: HttpClient) { }

  getStudents(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/students`);
  }

  getStudentById(id: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/students/${id}`);
  }

  createStudent(student: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/students`, student);
  }

  updateStudent(id: string, student: any): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/students/${id}`, student);
  }

  deleteStudent(id: string): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/students/${id}`);
  }
}
