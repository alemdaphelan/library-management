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
  createdAt?: string;
  disciplineFlag?: boolean;
  overdueViolationCount?: number;
  violationAcademicYear?: string;
  reservationNoShowCount?: number;
  reservationViolationAcademicYear?: string;
  circulationLockedUntil?: string;
  circulationLockReason?: string;
}

export interface ReaderProfile {
  student: Student;
  card: {
    issued: boolean;
    cardNumber?: string;
    ownerName: string;
    department?: string;
    cardStatus?: string;
    issuedAt?: string;
    expiryDate?: string;
    depositBalance: number;
    totalDebt: number;
    circulationLockedUntil?: string;
    circulationLockReason?: string;
  };
  loans: Array<{
    ticketId: number;
    ticketType: string;
    status: string;
    createdAt?: string;
    nearestDueDate?: string;
    itemCount: number;
    overdueItemCount: number;
    totalLateFine: number;
  }>;
  ledger: Array<{
    transactionId: number;
    amount: number;
    paidAmount?: number;
    type: string;
    reason: string;
    status: string;
    receiptNumber?: string;
    createdAt?: string;
  }>;
  penaltyCases: Array<{
    caseId: number;
    barcode?: string;
    incidentType: string;
    description: string;
    totalAmount: number;
    paidAmount?: number;
    status: string;
    reportedAt?: string;
    settlementDueAt?: string;
  }>;
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

  getReaderProfile(mssv: string): Observable<ReaderProfile> {
    return this.http.get<ReaderProfile>(
      `${this.apiUrl}/students/${encodeURIComponent(mssv)}/profile`
    );
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
