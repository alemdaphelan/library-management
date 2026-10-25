import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

describe('AuthService session handling', () => {
  let service: AuthService;
  let http: HttpTestingController;
  const navigate = vi.fn();

  const jwt = (expiresAtSeconds: number): string => {
    const payload = btoa(JSON.stringify({ exp: expiresAtSeconds }));
    return `header.${payload}.signature`;
  };

  beforeEach(() => {
    localStorage.clear();
    navigate.mockReset();
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate } },
      ],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('rejects expired or malformed tokens instead of trusting local storage', () => {
    localStorage.setItem('huit_lib_role', 'STUDENT');
    localStorage.setItem('huit_lib_token', jwt(Math.floor(Date.now() / 1000) - 10));
    expect(service.isLoggedIn()).toBe(false);

    localStorage.setItem('huit_lib_token', 'not-a-jwt');
    expect(service.isLoggedIn()).toBe(false);

    localStorage.setItem('huit_lib_token', jwt(Math.floor(Date.now() / 1000) + 3600));
    expect(service.isLoggedIn()).toBe(true);
  });

  it('clears the browser session immediately while notifying the backend', () => {
    const token = jwt(Math.floor(Date.now() / 1000) + 3600);
    localStorage.setItem('huit_lib_role', 'STUDENT');
    localStorage.setItem('huit_lib_token', token);
    localStorage.setItem('huit_lib_user', 'Sinh viên');
    localStorage.setItem('huit_lib_profile', JSON.stringify({ refreshToken: 'refresh-1' }));

    service.logout();

    expect(localStorage.getItem('huit_lib_token')).toBeNull();
    expect(localStorage.getItem('huit_lib_role')).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], { replaceUrl: true });
    const request = http.expectOne(req => req.url.endsWith('/api/v1/auth/logout'));
    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    expect(request.request.body).toEqual({ refreshToken: 'refresh-1' });
    request.flush('ok');
  });
});
