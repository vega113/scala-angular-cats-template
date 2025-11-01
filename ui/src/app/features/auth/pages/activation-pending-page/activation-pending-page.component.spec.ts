import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { AuthService } from '@core/services/auth.service';
import { ActivationPendingPageComponent } from './activation-pending-page.component';

describe('ActivationPendingPageComponent', () => {
  let component: ActivationPendingPageComponent;
  let fixture: ComponentFixture<ActivationPendingPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ActivationPendingPageComponent],
      providers: [
        { provide: AuthService, useValue: { pendingActivationEmail: () => null } },
        { provide: Router, useValue: { navigate: jasmine.createSpy('navigate') } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ActivationPendingPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the component', () => {
    expect(component).toBeTruthy();
  });
});
