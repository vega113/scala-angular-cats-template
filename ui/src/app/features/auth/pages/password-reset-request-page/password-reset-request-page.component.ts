import { ChangeDetectionStrategy, Component, Signal, computed, inject, signal } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { AuthService } from '../../../../core/services/auth.service';

@Component({
  selector: 'app-password-reset-request-page',
  templateUrl: './password-reset-request-page.component.html',
  styleUrls: ['./password-reset-request-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordResetRequestPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
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

    this.errorSignal.set(null);
    this.successSignal.set(false);

    this.authService.requestPasswordReset(this.form.getRawValue()).subscribe({
      next: () => this.successSignal.set(true),
      error: (error: unknown) => this.errorSignal.set(extractErrorMessage(error)),
    });
  }
}

function extractErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.error?.error?.message) {
    return error.error.error.message;
  }
  return 'Unable to process password reset request. Please try again later.';
}
