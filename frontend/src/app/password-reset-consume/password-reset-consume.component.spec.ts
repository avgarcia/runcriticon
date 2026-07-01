import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PasswordResetConsumeComponent } from './password-reset-consume.component';
import { SessionService } from '../core/session.service';

describe('PasswordResetConsumeComponent', () => {
  let fixture: ComponentFixture<PasswordResetConsumeComponent>;
  let component: PasswordResetConsumeComponent;
  let navigate: jest.SpyInstance;
  const sessionMock = { consumePasswordReset: jest.fn() };

  async function createComponent(token: string | null): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PasswordResetConsumeComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: SessionService, useValue: sessionMock },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: () => token } } },
        },
      ],
    }).compileComponents();
    navigate = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(PasswordResetConsumeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => jest.clearAllMocks());

  it('sin token marca el enlace como caducado', async () => {
    await createComponent(null);
    expect(component.linkInvalid()).toBe(true);
  });

  it('no llama al backend si el formulario es inválido', async () => {
    await createComponent('token-xyz');
    component.form.setValue({ password: 'corta', confirm: 'corta' });
    component.submit();
    expect(sessionMock.consumePasswordReset).not.toHaveBeenCalled();
  });

  it('con token y contraseña válida fija la contraseña y entra', async () => {
    sessionMock.consumePasswordReset.mockReturnValue(
      of({ userId: 'u', clubId: 'c', role: 'ALUMNO' }),
    );
    await createComponent('token-xyz');
    component.form.setValue({ password: 'clave-clave-clave', confirm: 'clave-clave-clave' });
    component.submit();
    expect(sessionMock.consumePasswordReset).toHaveBeenCalledWith('token-xyz', 'clave-clave-clave');
    expect(navigate).toHaveBeenCalledWith(['/']);
  });

  it('ante 404/409 (caducado o usado) muestra el estado de enlace inválido', async () => {
    sessionMock.consumePasswordReset.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );
    await createComponent('token-caducado');
    component.form.setValue({ password: 'clave-clave-clave', confirm: 'clave-clave-clave' });
    component.submit();
    expect(component.linkInvalid()).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('ante 400 (política) muestra el mensaje de error sin navegar', async () => {
    sessionMock.consumePasswordReset.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400 })),
    );
    await createComponent('token-xyz');
    component.form.setValue({ password: 'clave-clave-clave', confirm: 'clave-clave-clave' });
    component.submit();
    expect(component.errorMessage()).not.toBeNull();
    expect(component.linkInvalid()).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });
});
