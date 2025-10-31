import { NgModule } from '@angular/core';

import { SharedModule } from '../../shared/shared.module';
import { AuthRoutingModule } from './auth-routing.module';
import { LoginPageComponent } from './pages/login-page/login-page.component';
import { SignupPageComponent } from './pages/signup-page/signup-page.component';

@NgModule({
  declarations: [LoginPageComponent, SignupPageComponent],
  imports: [SharedModule, AuthRoutingModule],
})
export class AuthModule {}
