import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { AuthService } from '@core/services/auth.service';

type ActivationState = 'loading' | 'success' | 'error';

@Component({
  selector: 'app-activation-confirm-page',
  templateUrl: './activation-confirm-page.component.html',
  styleUrls: ['./activation-confirm-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActivationConfirmPageComponent {
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private redirectTimer: ReturnType<typeof setTimeout> | null = null;

  private readonly stateSignal = signal<ActivationState>('loading');
  private readonly messageSignal = signal<string>('Activating your account…');
  private readonly emailSignal = signal<string | null>(null);

  readonly isLoading = computed(() => this.stateSignal() === 'loading');
  readonly isSuccess = computed(() => this.stateSignal() === 'success');
  readonly isError = computed(() => this.stateSignal() === 'error');
  readonly message = computed(() => this.messageSignal());
  readonly email = computed(() => this.emailSignal());

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const token = params.get('token')?.trim();
      const email = params.get('email');
      this.emailSignal.set(email);

      if (!token) {
        this.setError('Activation link is missing or invalid. Request a new link and try again.');
        return;
      }

      this.confirmActivation(token);
    });

    this.destroyRef.onDestroy(() => {
      if (this.redirectTimer) {
        clearTimeout(this.redirectTimer);
      }
    });
  }

  retry(): void {
    this.router.navigate(['/auth/login']);
  }

  skipToTodos(): void {
    this.router.navigate(['/todos']);
  }

  private confirmActivation(token: string): void {
    this.setLoading('Activating your account…');
    this.authService
      .confirmActivation({ token })
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: () => {
          this.stateSignal.set('success');
          this.messageSignal.set('Your account is active. Redirecting to your todos…');
          this.scheduleRedirect();
        },
        error: (error) => this.setError(extractActivationError(error)),
      });
  }

  private setLoading(message: string): void {
    this.stateSignal.set('loading');
    this.messageSignal.set(message);
  }

  private setError(message: string): void {
    this.stateSignal.set('error');
    this.messageSignal.set(message);
    if (this.redirectTimer) {
      clearTimeout(this.redirectTimer);
      this.redirectTimer = null;
    }
  }

  private scheduleRedirect(): void {
    if (this.redirectTimer) {
      clearTimeout(this.redirectTimer);
    }
    this.redirectTimer = setTimeout(() => this.skipToTodos(), 1500);
  }
}

function extractActivationError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const code: string | undefined = error.error?.error?.code;
    const message: string | undefined = error.error?.error?.message;
    switch (code) {
      case 'activation_invalid':
        return (
          message ??
          'This activation link is invalid. Request a new activation email from the login page.'
        );
      case 'activation_expired':
        return (
          message ??
          'This activation link has expired. Request a new activation email from the login page.'
        );
      default:
        if (message) {
          return message;
        }
    }
  }
  return 'We could not activate your account. Request a new activation email and try again.';
}
