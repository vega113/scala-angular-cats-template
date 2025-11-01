import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpClient,
  HttpErrorResponse,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';

import { authInterceptor } from './auth.interceptor';
import { AuthTokenService } from '../services/auth-token.service';

class AuthTokenServiceStub {
  private value: string | null = null;

  token(): string | null {
    return this.value;
  }

  hasToken(): boolean {
    return this.value != null;
  }

  setToken(token: string): void {
    this.value = token;
  }

  clearToken(): void {
    this.value = null;
  }
}

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authTokenService: AuthTokenServiceStub;
  let navigateSpy: jasmine.Spy;

  beforeEach(() => {
    authTokenService = new AuthTokenServiceStub();
    authTokenService.setToken('abc123');
    navigateSpy = jasmine.createSpy('navigate');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthTokenService, useValue: authTokenService },
        {
          provide: Router,
          useValue: { navigate: navigateSpy },
        },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('attaches bearer token when available', () => {
    http.get('/api/todos').subscribe();
    const req = httpMock.expectOne('/api/todos');
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc123');
    req.flush({});
  });

  it('clears token and redirects on 401 response', () => {
    let capturedError: HttpErrorResponse | undefined;

    http.get('/api/secure').subscribe({
      error: (err) => (capturedError = err),
    });
    const req = httpMock.expectOne('/api/secure');
    req.flush(
      { message: 'unauthorized' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(authTokenService.hasToken()).toBeFalse();
    expect(navigateSpy).toHaveBeenCalledWith(['/auth/login'], {
      replaceUrl: true,
    });
    expect(capturedError?.status).toBe(401);
  });
});
