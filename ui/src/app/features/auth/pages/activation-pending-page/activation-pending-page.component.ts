import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '@core/services/auth.service';

@Component({
  selector: 'app-activation-pending-page',
  templateUrl: './activation-pending-page.component.html',
  styleUrls: ['./activation-pending-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActivationPendingPageComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly email = computed(() => this.authService.pendingActivationEmail());

  goToLogin(): void {
    this.router.navigate(['/auth/login']);
  }

  goToSignup(): void {
    this.router.navigate(['/auth/signup']);
  }
}
