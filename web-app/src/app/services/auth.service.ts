import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

export type Role = 'ADMIN' | 'LIBRARIAN' | 'STUDENT' | 'ACCOUNTANT' | 'TREASURER' | null;

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  role: string;
}

import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private roleKey = 'huit_lib_role';
  private userKey = 'huit_lib_user';
  private tokenKey = 'huit_lib_token';
  private apiUrl = `${environment.apiUrl}/api/v1/auth`;

  constructor(private http: HttpClient, private router: Router) { }

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/login`, { email, password }).pipe(
      tap(response => {
        localStorage.setItem(this.roleKey, response.role);
        localStorage.setItem(this.userKey, email);
        localStorage.setItem(this.tokenKey, response.accessToken);
      })
    );
  }

  logout() {
    const token = this.getToken();
    if (token) {
      this.http.post(`${this.apiUrl}/logout`, {}, {
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
    this.router.navigate(['/login']);
  }

  getRole(): Role {
    return localStorage.getItem(this.roleKey) as Role;
  }

  getCurrentUser(): string | null {
    return localStorage.getItem(this.userKey);
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

  changePassword(oldPassword: string, newPassword: string): Observable<any> {
    const email = this.getCurrentUser();
    return this.http.post(`${this.apiUrl}/change-password`, null, {
      params: { email: email || '', oldPassword, newPassword },
      responseType: 'text'
    });
  }

  isLoggedIn(): boolean {
    return !!this.getRole();
  }

  forgotPassword(email: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/forgot-password`, null, {
      params: { email },
      responseType: 'text'
    });
  }

  verifyOtp(email: string, otp: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/verify-otp`, null, {
      params: { email, otp },
      responseType: 'text'
    });
  }

  resetPassword(email: string, otp: string, newPassword: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/reset-password`, null, {
      params: { email, otp, newPassword },
      responseType: 'text'
    });
  }
}
