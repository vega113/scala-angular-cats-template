import { NgModule } from '@angular/core';

import { SharedModule } from '../../shared/shared.module';
import { AuthRoutingModule } from './auth-routing.module';
import { LoginPageComponent } from './pages/login-page/login-page.component';
import { PasswordResetConfirmPageComponent } from './pages/password-reset-confirm-page/password-reset-confirm-page.component';
import { PasswordResetRequestPageComponent } from './pages/password-reset-request-page/password-reset-request-page.component';
import { SignupPageComponent } from './pages/signup-page/signup-page.component';

@NgModule({
  declarations: [
    LoginPageComponent,
    SignupPageComponent,
    PasswordResetRequestPageComponent,
    PasswordResetConfirmPageComponent,
  ],
  imports: [SharedModule, AuthRoutingModule],
})
export class AuthModule {}
