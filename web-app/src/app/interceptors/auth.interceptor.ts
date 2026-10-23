import { HttpInterceptorFn } from '@angular/common/http';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('huit_lib_token');

  // Không đính kèm Bearer token cho các API bên ngoài như Google Gemini
  if (token && !req.url.includes('googleapis.com')) {
    const cloned = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
    return next(cloned);
  }

  return next(req);
};
