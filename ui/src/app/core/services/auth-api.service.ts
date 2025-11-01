import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';

import {
  AuthResponse,
  AuthUser,
  SignupResponse,
  LoginRequest,
  PasswordResetConfirmPayload,
  PasswordResetRequestPayload,
  SignupRequest,
  ActivationConfirmPayload,
} from '../models/auth.model';

interface AuthResponseDto {
  token: string;
  user: {
    id: string;
    email: string;
  };
}

interface SignupResponseDto {
  status: string;
  message: string;
}

interface PasswordResetResponseDto {
  status: string;
}

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/auth';

  signup(payload: SignupRequest): Observable<SignupResponse> {
    return this.http
      .post<SignupResponseDto>(`${this.baseUrl}/signup`, payload)
      .pipe(map(mapSignupResponse));
  }

  login(payload: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponseDto>(`${this.baseUrl}/login`, payload)
      .pipe(map(mapAuthResponse));
  }

  me(): Observable<AuthUser> {
    return this.http
      .get<{ id: string; email: string }>(`${this.baseUrl}/me`)
      .pipe(map((dto) => ({ id: dto.id, email: dto.email })));
  }

  requestPasswordReset(payload: PasswordResetRequestPayload): Observable<void> {
    return this.http
      .post<PasswordResetResponseDto>(`${this.baseUrl}/password-reset/request`, payload)
      .pipe(map(() => void 0));
  }

  confirmPasswordReset(payload: PasswordResetConfirmPayload): Observable<void> {
    return this.http
      .post<void>(`${this.baseUrl}/password-reset/confirm`, payload)
      .pipe(map(() => void 0));
  }

  confirmActivation(payload: ActivationConfirmPayload): Observable<AuthResponse> {
    return this.http
      .post<AuthResponseDto>(`${this.baseUrl}/activation/confirm`, payload)
      .pipe(map(mapAuthResponse));
  }
}

function mapAuthResponse(dto: AuthResponseDto): AuthResponse {
  return {
    token: dto.token,
    user: {
      id: dto.user.id,
      email: dto.user.email,
    },
  };
}

function mapSignupResponse(dto: SignupResponseDto): SignupResponse {
  return {
    status: dto.status as SignupResponse['status'],
    message: dto.message,
  };
}
