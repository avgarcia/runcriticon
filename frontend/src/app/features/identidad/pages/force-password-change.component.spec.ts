import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ForcePasswordChangeComponent } from './force-password-change.component';
import { SessionService } from '../../../core/session.service';

describe('ForcePasswordChangeComponent', () => {
  let fixture: ComponentFixture<ForcePasswordChangeComponent>;
  let component: ForcePasswordChangeComponent;
  const sessionMock = { takeExpiredCredentials: jest.fn(), changeExpiredPassword: jest.fn() };
  const routerMock = { navigate: jest.fn() };

  async function createComponent(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ForcePasswordChangeComponent],
      providers: [
        { provide: SessionService, useValue: sessionMock },
        { provide: Router, useValue: routerMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ForcePasswordChangeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => jest.clearAllMocks());

  it('sin credenciales caducadas (acceso directo / recarga) redirige al login', async () => {
    sessionMock.takeExpiredCredentials.mockReturnValue(null);
    await createComponent();
    expect(routerMock.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('con una contraseña nueva válida cambia y entra', async () => {
    sessionMock.takeExpiredCredentials.mockReturnValue({ email: 'ana@club.local', password: 'caducada12345' });
    sessionMock.changeExpiredPassword.mockReturnValue(of({ userId: 'u', clubId: 'c', role: 'ENTRENADOR' }));
    await createComponent();

    component.form.setValue({ password: 'clave-nueva-larga', confirm: 'clave-nueva-larga' });
    component.submit();

    expect(sessionMock.changeExpiredPassword).toHaveBeenCalledWith(
      'ana@club.local',
      'caducada12345',
      'clave-nueva-larga',
    );
    expect(routerMock.navigate).toHaveBeenCalledWith(['/']);
  });

  it('si el backend rechaza la contraseña (400) muestra el mensaje de política', async () => {
    sessionMock.takeExpiredCredentials.mockReturnValue({ email: 'ana@club.local', password: 'caducada12345' });
    sessionMock.changeExpiredPassword.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
    await createComponent();

    component.form.setValue({ password: 'clave-nueva-larga', confirm: 'clave-nueva-larga' });
    component.submit();

    expect(component.errorMessage()).toContain('requisitos');
  });
});
