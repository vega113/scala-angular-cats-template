import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'auth.token';

@Injectable({ providedIn: 'root' })
export class AuthTokenService {
  private readonly tokenSignal = signal<string | null>(this.readInitialToken());

  token(): string | null {
    return this.tokenSignal();
  }

  hasToken(): boolean {
    return this.tokenSignal() != null;
  }

  setToken(token: string): void {
    this.tokenSignal.set(token);
    this.persist(token);
  }

  clearToken(): void {
    this.tokenSignal.set(null);
    this.persist(null);
  }

  private readInitialToken(): string | null {
    try {
      const value = localStorage.getItem(STORAGE_KEY);
      return value ? value : null;
    } catch {
      return null;
    }
  }

  private persist(token: string | null): void {
    try {
      if (token == null) {
        localStorage.removeItem(STORAGE_KEY);
      } else {
        localStorage.setItem(STORAGE_KEY, token);
      }
    } catch {
      // ignore persistence errors (e.g., quota exceeded, SSR)
    }
  }
}
