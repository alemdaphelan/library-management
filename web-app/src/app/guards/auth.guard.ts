import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot, Router } from '@angular/router';
import { AuthService, Role } from '../services/auth.service';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {

  constructor(private authService: AuthService, private router: Router) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    const isLoggedIn = this.authService.isLoggedIn();
    
    if (!isLoggedIn) {
      this.router.navigate(['/login']);
      return false;
    }

    const requiredRoles = route.data['roles'] as Role[];
    if (requiredRoles) {
      const userRole = this.authService.getRole();
      if (!requiredRoles.includes(userRole)) {
        // Redirect to a safe page if no permission
        alert("Bạn không có quyền truy cập trang này!");
        this.router.navigate(['/']);
        return false;
      }
    }

    return true;
  }
}
