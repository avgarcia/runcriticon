import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PasswordResetRequestComponent } from './password-reset-request.component';
import { SessionService } from '../core/session.service';

describe('PasswordResetRequestComponent', () => {
  let fixture: ComponentFixture<PasswordResetRequestComponent>;
  let component: PasswordResetRequestComponent;
  const sessionMock = { requestPasswordReset: jest.fn() };

  beforeEach(async () => {
    jest.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [PasswordResetRequestComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: SessionService, useValue: sessionMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PasswordResetRequestComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('no llama al backend si el email es inválido', () => {
    component.form.setValue({ email: 'no-es-email' });
    component.submit();
    expect(sessionMock.requestPasswordReset).not.toHaveBeenCalled();
  });

  it('con un email válido pide el reseteo y muestra "revisa tu email"', () => {
    sessionMock.requestPasswordReset.mockReturnValue(of(undefined));
    component.form.setValue({ email: 'ana@club.local' });
    component.submit();
    expect(sessionMock.requestPasswordReset).toHaveBeenCalledWith('ana@club.local');
    expect(component.sent()).toBe(true);
  });

  it('ante un error muestra un mensaje y no marca enviado', () => {
    sessionMock.requestPasswordReset.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    component.form.setValue({ email: 'ana@club.local' });
    component.submit();
    expect(component.sent()).toBe(false);
    expect(component.errorMessage()).not.toBeNull();
  });
});
