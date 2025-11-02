import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { AuthService } from '../../../../core/services/auth.service';

@Component({
  selector: 'app-login-page',
  templateUrl: './login-page.component.html',
  styleUrls: ['./login-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  private readonly errorSignal = signal<string | null>(null);
  readonly errorMessage = computed(() => this.errorSignal());
  readonly loading = this.authService.loading;

  constructor() {
    const navigation = this.router.getCurrentNavigation();
    const navigationState = navigation?.extras?.state as Record<string, unknown> | undefined;
    const historyState =
      typeof window !== 'undefined'
        ? (window.history.state as Record<string, unknown> | undefined)
        : undefined;
    const sessionExpired =
      (navigationState?.['sessionExpired'] ?? historyState?.['sessionExpired']) === true;
    if (sessionExpired) {
      this.errorSignal.set('Your session expired. Please sign in again.');
    }
  }

  submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorSignal.set(null);
    const credentials = this.form.getRawValue();
    const normalizedEmail = credentials.email.trim().toLowerCase();

    this.authService.login(credentials).subscribe({
      next: () => this.router.navigate(['/todos']),
      error: (error: unknown) => {
        if (isAccountNotActivated(error)) {
          this.authService.setPendingActivationEmail(normalizedEmail);
          this.router.navigate(['/auth/activate/pending']);
          return;
        }
        this.errorSignal.set(extractErrorMessage(error));
      },
    });
  }
}

function extractErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.error?.error?.message) {
    return error.error.error.message;
  }
  return 'Unable to log in. Please check your credentials and try again.';
}

function isAccountNotActivated(error: unknown): boolean {
  return (
    error instanceof HttpErrorResponse &&
    error.status === 403 &&
    error.error?.error?.code === 'account_not_activated'
  );
}
