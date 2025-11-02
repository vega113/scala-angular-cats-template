import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, throwError } from 'rxjs';

import { AuthTokenService } from '../services/auth-token.service';

export const authInterceptor: HttpInterceptorFn = (
  request: HttpRequest<unknown>,
  next: HttpHandlerFn,
): Observable<HttpEvent<unknown>> => {
  const authTokenService = inject(AuthTokenService);
  const router = inject(Router);
  const token = authTokenService.token();
  const authRequest = token
    ? request.clone({
        setHeaders: { Authorization: `Bearer ${token}` },
      })
    : request;

  return next(authRequest).pipe(
    catchError((error: unknown) =>
      handleUnauthorized(error, authTokenService, router, token !== null),
    ),
  );
};

function handleUnauthorized(
  error: unknown,
  authTokenService: AuthTokenService,
  router: Router,
  hadToken: boolean,
) {
  if (error instanceof HttpErrorResponse && error.status === 401 && hadToken) {
    authTokenService.clearToken();
    router.navigate(['/auth/login'], {
      replaceUrl: true,
      state: { sessionExpired: true },
    });
  }
  return throwError(() => error);
}
