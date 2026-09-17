import { Injectable } from '@angular/core';

export type Role = 'ADMIN' | 'LIBRARIAN' | 'STUDENT' | null;

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private roleKey = 'huit_lib_role';
  private userKey = 'huit_lib_user';

  constructor() { }

  login(username: string): Role {
    let role: Role = 'STUDENT';
    
    if (username === 'admin') {
      role = 'ADMIN';
    } else if (username === 'thuthu') {
      role = 'LIBRARIAN';
    }
    
    localStorage.setItem(this.roleKey, role);
    localStorage.setItem(this.userKey, username);
    return role;
  }

  logout() {
    localStorage.removeItem(this.roleKey);
    localStorage.removeItem(this.userKey);
  }

  getRole(): Role {
    return localStorage.getItem(this.roleKey) as Role;
  }

  getCurrentUser(): string | null {
    return localStorage.getItem(this.userKey);
  }

  isLoggedIn(): boolean {
    return !!this.getRole();
  }
}
