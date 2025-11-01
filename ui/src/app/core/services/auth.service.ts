import { Signal, inject, Injectable, computed, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, tap, throwError } from 'rxjs';

import {
  AuthResponse,
  AuthUser,
  LoginRequest,
  PasswordResetConfirmPayload,
  PasswordResetRequestPayload,
  SignupRequest,
} from '../models/auth.model';
import { AuthApiService } from './auth-api.service';
import { AuthTokenService } from './auth-token.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly authApi = inject(AuthApiService);
  private readonly tokenService = inject(AuthTokenService);

  private readonly userSignal = signal<AuthUser | null>(null);
  private readonly loadingSignal = signal<boolean>(false);

  readonly currentUser: Signal<AuthUser | null> = computed(() => this.userSignal());
  readonly isAuthenticated: Signal<boolean> = computed(() => this.userSignal() !== null);
  readonly loading: Signal<boolean> = computed(() => this.loadingSignal());

  constructor() {
    if (this.tokenService.hasToken()) {
      this.refreshCurrentUser().subscribe({
        error: () => this.logout(),
      });
    }
  }

  login(payload: LoginRequest): Observable<AuthUser> {
    this.setLoading(true);
    return this.authApi.login(payload).pipe(
      tap((response) => this.persistAuth(response)),
      map((response) => response.user),
      finalize(() => this.setLoading(false)),
    );
  }

  signup(payload: SignupRequest): Observable<AuthUser> {
    this.setLoading(true);
    return this.authApi.signup(payload).pipe(
      tap((response) => this.persistAuth(response)),
      map((response) => response.user),
      finalize(() => this.setLoading(false)),
    );
  }

  requestPasswordReset(payload: PasswordResetRequestPayload): Observable<void> {
    this.setLoading(true);
    return this.authApi.requestPasswordReset(payload).pipe(
      finalize(() => this.setLoading(false)),
    );
  }

  confirmPasswordReset(payload: PasswordResetConfirmPayload): Observable<void> {
    this.setLoading(true);
    return this.authApi.confirmPasswordReset(payload).pipe(
      finalize(() => this.setLoading(false)),
    );
  }

  refreshCurrentUser(): Observable<AuthUser | null> {
    if (!this.tokenService.hasToken()) {
      this.userSignal.set(null);
      return of(null);
    }

    this.setLoading(true);
    return this.authApi.me().pipe(
      tap((user) => this.userSignal.set(user)),
      catchError((error) => {
        this.userSignal.set(null);
        this.tokenService.clearToken();
        this.setLoading(false);
        return throwError(() => error);
      }),
      finalize(() => this.setLoading(false)),
    );
  }

  logout(): void {
    this.tokenService.clearToken();
    this.userSignal.set(null);
  }

  private persistAuth(response: AuthResponse): void {
    this.tokenService.setToken(response.token);
    this.userSignal.set(response.user);
  }

  private setLoading(value: boolean): void {
    this.loadingSignal.set(value);
  }
}
