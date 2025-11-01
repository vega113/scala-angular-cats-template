import { inject } from '@angular/core';
import {
  CanActivateFn,
  CanMatchFn,
  Router,
  Route,
  UrlSegment,
  UrlTree,
} from '@angular/router';

import { AuthTokenService } from '../services/auth-token.service';

const ensureAuthenticated = (): boolean | UrlTree => {
  const authTokenService = inject(AuthTokenService);
  if (authTokenService.hasToken()) {
    return true;
  }
  const router = inject(Router);
  return router.parseUrl('/auth/login');
};

export const authCanActivate: CanActivateFn = () => ensureAuthenticated();

export const authCanMatch: CanMatchFn = (
  _route: Route,
  _segments: UrlSegment[],
): boolean | UrlTree => ensureAuthenticated();
