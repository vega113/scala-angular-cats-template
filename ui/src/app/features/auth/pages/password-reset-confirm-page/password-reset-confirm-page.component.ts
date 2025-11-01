import { ChangeDetectionStrategy, Component, Signal, computed, inject, signal } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { AuthService } from '../../../../core/services/auth.service';

@Component({
  selector: 'app-password-reset-confirm-page',
  templateUrl: './password-reset-confirm-page.component.html',
  styleUrls: ['./password-reset-confirm-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordResetConfirmPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);

  private readonly tokenSignal = signal(
    this.route.snapshot.paramMap.get('token') ??
      this.route.snapshot.queryParamMap.get('token') ??
      '',
  );

  readonly token = computed(() => this.tokenSignal());

  readonly form = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(8)]],
    passwordConfirm: ['', [Validators.required]],
  });

  private readonly successSignal = signal<boolean>(false);
  private readonly errorSignal = signal<string | null>(null);

  readonly success = computed(() => this.successSignal());
  readonly errorMessage = computed(() => this.errorSignal());
  readonly loading = this.authService.loading;

  submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }

    const token = this.token();
    if (!token) {
      this.errorSignal.set('Reset link is missing or invalid.');
      return;
    }

    const { password, passwordConfirm } = this.form.getRawValue();
    if (password !== passwordConfirm) {
      this.errorSignal.set('Passwords must match.');
      return;
    }

    this.errorSignal.set(null);
    this.successSignal.set(false);

    this.authService.confirmPasswordReset({ token, password }).subscribe({
      next: () => this.successSignal.set(true),
      error: (error: unknown) => this.errorSignal.set(extractErrorMessage(error)),
    });
  }

  navigateToLogin(): void {
    this.router.navigate(['/auth/login']);
  }
}

function extractErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.error?.error?.message) {
    return error.error.error.message;
  }
  return 'Unable to reset password. Request a new link and try again.';
}
