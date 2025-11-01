import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { LoginPageComponent } from './pages/login-page/login-page.component';
import { PasswordResetConfirmPageComponent } from './pages/password-reset-confirm-page/password-reset-confirm-page.component';
import { PasswordResetRequestPageComponent } from './pages/password-reset-request-page/password-reset-request-page.component';
import { SignupPageComponent } from './pages/signup-page/signup-page.component';

const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'login',
  },
  {
    path: 'login',
    component: LoginPageComponent,
    data: { title: 'Login' },
  },
  {
    path: 'signup',
    component: SignupPageComponent,
    data: { title: 'Create Account' },
  },
  {
    path: 'password-reset',
    children: [
      {
        path: 'request',
        component: PasswordResetRequestPageComponent,
        data: { title: 'Password Reset' },
      },
      {
        path: 'confirm/:token',
        component: PasswordResetConfirmPageComponent,
        data: { title: 'Choose New Password' },
      },
      {
        path: 'confirm',
        component: PasswordResetConfirmPageComponent,
        data: { title: 'Choose New Password' },
      },
    ],
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class AuthRoutingModule {}
