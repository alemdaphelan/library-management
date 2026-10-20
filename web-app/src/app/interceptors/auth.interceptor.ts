import { HttpInterceptorFn } from '@angular/common/http';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('huit_lib_token');
  const isPublicRecommendation =
    req.method === 'GET' &&
    (req.url.endsWith('/books/recommendations') ||
      req.url.includes('/books/recommendations/popular'));

  if (token && !isPublicRecommendation) {
    const cloned = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
    return next(cloned);
  }

  return next(req);
};
