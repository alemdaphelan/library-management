import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

export type Role = 'ADMIN' | 'LIBRARIAN' | 'STUDENT' | 'ACCOUNTANT' | 'TREASURER' | null;

export interface LoginResponse {
  tokenType?: string;
  accessToken: string;
  refreshToken: string;
  role: string;
  userId?: string;
  fullName?: string;
  email?: string;
  mssv?: string;
  isFirstLogin?: boolean;
  avatarUrl?: string;
  cardStatus?: string;
}

export interface LibraryCard {
  cardNumber: string;
  ownerName: string;
  department?: string;
  cardStatus: string;
  issuedAt?: string;
  expiryDate?: string;
  depositBalance: number;
  totalDebt: number;
  circulationLockedUntil?: string;
  circulationLockReason?: string;
}

import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private roleKey = 'huit_lib_role';
  private userKey = 'huit_lib_user';
  private tokenKey = 'huit_lib_token';
  private profileKey = 'huit_lib_profile';
  private apiUrl = `${environment.apiUrl}/api/v1/auth`;

  constructor(private http: HttpClient, private router: Router) { }

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/login`, { email, password }).pipe(
      tap(response => {
        localStorage.setItem(this.roleKey, response.role);
        localStorage.setItem(this.userKey, response.fullName || response.email || email);
        localStorage.setItem(this.tokenKey, response.accessToken);
        localStorage.setItem(this.profileKey, JSON.stringify(response));
      })
    );
  }

  logout() {
    const token = this.getToken();
    const refreshToken = this.getUserProfile()?.refreshToken;
    if (token) {
      this.http.post(`${this.apiUrl}/logout`, { refreshToken }, {
        headers: { 'Authorization': `Bearer ${token}` }
      }).subscribe({
        next: () => this.clearLocalData(),
        error: () => this.clearLocalData()
      });
    } else {
      this.clearLocalData();
    }
  }

  private clearLocalData() {
    localStorage.removeItem(this.roleKey);
    localStorage.removeItem(this.userKey);
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.profileKey);
    this.router.navigate(['/login']);
  }

  getRole(): Role {
    return localStorage.getItem(this.roleKey) as Role;
  }

  getCurrentUser(): string | null {
    return localStorage.getItem(this.userKey);
  }

  getUserProfile(): LoginResponse | null {
    const raw = localStorage.getItem(this.profileKey);
    return raw ? JSON.parse(raw) : null;
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  fetchMyProfile(): Observable<any> {
    const token = this.getToken();
    return this.http.get(`${this.apiUrl}/me`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
  }

  fetchLibraryCard(): Observable<LibraryCard> {
    return this.http.get<LibraryCard>(`${environment.apiUrl}/api/v1/library-card`);
  }

  updateProfile(data: { fullName?: string; phone?: string; avatarUrl?: string }): Observable<any> {
    const token = this.getToken();
    return this.http.put(`${this.apiUrl}/me`, data, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
  }

  uploadAvatar(file: File): Observable<any> {
    const token = this.getToken();
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post(`${this.apiUrl}/avatar`, formData, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
  }

  changePassword(oldPassword: string, newPassword: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/change-password`, {
      currentPassword: oldPassword,
      newPassword
    }, {
      responseType: 'text'
    });
  }

  isLoggedIn(): boolean {
    return !!this.getRole() && !!this.getToken();
  }

  forgotPassword(email: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/forgot-password`, { email }, {
      responseType: 'text'
    });
  }

  verifyOtp(email: string, otp: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/verify-otp`, { email, otp }, {
      responseType: 'text'
    });
  }

  resetPassword(email: string, otp: string, newPassword: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/reset-password`, { email, otp, newPassword }, {
      responseType: 'text'
    });
  }
}
