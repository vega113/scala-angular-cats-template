import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import {
  AuthResponse,
  LoginRequest,
  PasswordResetConfirmPayload,
  PasswordResetRequestPayload,
} from '../models/auth.model';
import { AuthApiService } from './auth-api.service';
import { AuthService } from './auth.service';
import { AuthTokenService } from './auth-token.service';

describe('AuthService', () => {
  let service: AuthService;
  let api: jasmine.SpyObj<AuthApiService>;
  let tokenService: AuthTokenService;

  const loginPayload: LoginRequest = { email: 'user@example.com', password: 'secret' };
  const authResponse: AuthResponse = {
    token: 'jwt-token',
    user: { id: '123', email: 'user@example.com' },
  };

  beforeEach(() => {
    api = jasmine.createSpyObj<AuthApiService>('AuthApiService', [
      'login',
      'signup',
      'me',
      'requestPasswordReset',
      'confirmPasswordReset',
    ]);

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        AuthTokenService,
        { provide: AuthApiService, useValue: api },
      ],
    });

    service = TestBed.inject(AuthService);
    tokenService = TestBed.inject(AuthTokenService);

    spyOn(localStorage, 'setItem').and.callFake(() => {});
    spyOn(localStorage, 'removeItem').and.callFake(() => {});
  });

  afterEach(() => {
    service.logout();
  });

  it('logs in and stores token and user', (done) => {
    api.login.and.returnValue(of(authResponse));

    service.login(loginPayload).subscribe({
      next: (user) => {
        expect(user).toEqual(authResponse.user);
        expect(tokenService.token()).toBe(authResponse.token);
        expect(service.isAuthenticated()).toBeTrue();
        done();
      },
    });
  });

  it('handles request password reset', (done) => {
    api.requestPasswordReset.and.returnValue(of(void 0));

    service.requestPasswordReset({ email: 'reset@example.com' }).subscribe({
      complete: () => {
        expect(api.requestPasswordReset).toHaveBeenCalledWith({
          email: 'reset@example.com',
        } as PasswordResetRequestPayload);
        done();
      },
    });
  });

  it('propagates error when refreshCurrentUser fails', (done) => {
    api.me.and.returnValue(throwError(() => new Error('invalid token')));
    tokenService.setToken('jwt-token');

    service.refreshCurrentUser().subscribe({
      error: (error) => {
        expect(error).toBeTruthy();
        expect(service.isAuthenticated()).toBeFalse();
        done();
      },
    });
  });

  it('confirms password reset', (done) => {
    api.confirmPasswordReset.and.returnValue(of(void 0));

    service.confirmPasswordReset({
      token: 'reset-token',
      password: 'new-secret',
    } as PasswordResetConfirmPayload).subscribe({
      complete: () => {
        expect(api.confirmPasswordReset).toHaveBeenCalled();
        done();
      },
    });
  });
});
