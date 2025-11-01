import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthApiService } from './auth-api.service';

describe('AuthApiService', () => {
  let service: AuthApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(AuthApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should map login response', () => {
    let token: string | undefined;
    let email: string | undefined;

    service.login({ email: 'user@example.com', password: 'secret' }).subscribe((response) => {
      token = response.token;
      email = response.user.email;
    });

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    req.flush({
      token: 'jwt-token',
      user: { id: '123', email: 'user@example.com' },
    });

    expect(token).toBe('jwt-token');
    expect(email).toBe('user@example.com');
  });

  it('should issue password reset request', () => {
    service.requestPasswordReset({ email: 'reset@example.com' }).subscribe();
    const req = httpMock.expectOne('/api/auth/password-reset/request');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'reset@example.com' });
    req.flush({ status: 'ok' });
  });
});
