import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../../core/services/auth.service';
import { LoginPageComponent } from './login-page.component';

class AuthServiceStub {
  loading = () => false;
  login = jasmine.createSpy('login').and.returnValue(of({ id: '1', email: 'user@example.com' }));
}

describe('LoginPageComponent', () => {
  let component: LoginPageComponent;
  let fixture: ComponentFixture<LoginPageComponent>;
  let authService: AuthServiceStub;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [LoginPageComponent],
      imports: [ReactiveFormsModule, RouterTestingModule, HttpClientTestingModule],
      providers: [{ provide: AuthService, useClass: AuthServiceStub }],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginPageComponent);
    component = fixture.componentInstance;
    authService = TestBed.inject(AuthService) as unknown as AuthServiceStub;
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));
  });

  it('should disable submit when form invalid', () => {
    component.submit();
    expect(authService.login).not.toHaveBeenCalled();
  });

  it('should call auth service on submit when form valid', () => {
    component.form.setValue({ email: 'user@example.com', password: 'secret' });
    component.submit();

    expect(authService.login).toHaveBeenCalledWith({
      email: 'user@example.com',
      password: 'secret',
    });
  });

  it('should surface api errors', () => {
    authService.login.and.returnValue(throwError(() => new Error('invalid')));
    component.form.setValue({ email: 'user@example.com', password: 'secret' });

    component.submit();
    expect(component.errorMessage()).toBeTruthy();
  });
});
