import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { AuthService } from '../../../../core/services/auth.service';

@Component({
  selector: 'app-signup-page',
  templateUrl: './signup-page.component.html',
  styleUrls: ['./signup-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SignupPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    passwordConfirm: ['', [Validators.required]],
  });

  private readonly errorSignal = signal<string | null>(null);
  readonly errorMessage = computed(() => this.errorSignal());
  readonly loading = this.authService.loading;

  submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }

    const { password, passwordConfirm } = this.form.getRawValue();
    if (password !== passwordConfirm) {
      this.errorSignal.set('Passwords must match.');
      return;
    }

    this.errorSignal.set(null);
    const { email } = this.form.getRawValue();

    this.authService.signup({ email, password }).subscribe({
      next: () => this.router.navigate(['/auth/activate/pending']),
      error: (error: unknown) => this.errorSignal.set(extractErrorMessage(error)),
    });
  }
}

function extractErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.error?.error?.message) {
    return error.error.error.message;
  }
  return 'Unable to create account. Please try again.';
}
