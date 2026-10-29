import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { AuthGuard } from './auth.guard';
import { AuthService, Role } from '../services/auth.service';

describe('AuthGuard', () => {
  let role: Role;
  let loggedIn: boolean;
  let navigate: ReturnType<typeof vi.fn>;
  let guard: AuthGuard;

  beforeEach(() => {
    role = 'ADMIN';
    loggedIn = true;
    navigate = vi.fn();
    const authService = {
      isLoggedIn: () => loggedIn,
      getRole: () => role,
    } as AuthService;
    const router = { navigate } as unknown as Router;
    guard = new AuthGuard(authService, router);
  });

  function routeWithRoles(roles: Role[]): ActivatedRouteSnapshot {
    return { data: { roles } } as unknown as ActivatedRouteSnapshot;
  }

  const state = {} as RouterStateSnapshot;

  it('redirects unauthenticated users to login', () => {
    loggedIn = false;

    expect(guard.canActivate(routeWithRoles(['ADMIN']), state)).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('checks roles on child routes', () => {
    role = 'LIBRARIAN';

    expect(guard.canActivateChild(routeWithRoles(['ADMIN']), state)).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/']);
  });

  it('allows a librarian into a librarian route', () => {
    role = 'LIBRARIAN';

    expect(guard.canActivateChild(routeWithRoles(['ADMIN', 'LIBRARIAN']), state)).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });
});
