import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '@core/services/auth.service';

@Component({
  selector: 'app-shell',
  templateUrl: './shell.component.html',
  styleUrls: ['./shell.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly user = this.authService.currentUser;
  readonly isAuthenticated = this.authService.isAuthenticated;

  readonly initials = computed(() => {
    const user = this.user();
    return user ? user.email.charAt(0).toUpperCase() : '?';
  });

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/auth/login']);
  }
}
