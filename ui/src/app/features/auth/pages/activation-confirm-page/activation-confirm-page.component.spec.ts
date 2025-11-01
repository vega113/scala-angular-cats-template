import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '@core/services/auth.service';
import { ActivationConfirmPageComponent } from './activation-confirm-page.component';

describe('ActivationConfirmPageComponent', () => {
  let fixture: ComponentFixture<ActivationConfirmPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ActivationConfirmPageComponent],
      providers: [
        { provide: AuthService, useValue: { confirmActivation: () => of({ id: '1', email: 'test@example.com' }) } },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: of(convertToParamMap({ token: 'abc' })) },
        },
        { provide: Router, useValue: { navigate: jasmine.createSpy('navigate') } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ActivationConfirmPageComponent);
    fixture.detectChanges();
  });

  it('creates the component', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });
});
