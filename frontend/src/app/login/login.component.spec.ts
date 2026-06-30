import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { SessionService } from '../core/session.service';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let navigate: jest.SpyInstance;
  const sessionMock = { start: jest.fn(), stashExpiredCredentials: jest.fn() };

  beforeEach(async () => {
    jest.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: SessionService, useValue: sessionMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    navigate = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  it('se crea con el formulario inválido vacío', () => {
    expect(component).toBeTruthy();
    expect(component.form.invalid).toBe(true);
  });

  it('no llama al backend si el formulario es inválido', () => {
    component.submit();
    expect(sessionMock.start).not.toHaveBeenCalled();
  });

  it('con credenciales válidas inicia sesión y navega a la raíz', () => {
    sessionMock.start.mockReturnValue(of({ userId: 'u', clubId: 'c', role: 'ADMIN' }));
    component.form.setValue({ email: 'admin@club.local', password: 'secreta12345' });
    component.submit();
    expect(sessionMock.start).toHaveBeenCalledWith('admin@club.local', 'secreta12345');
    expect(navigate).toHaveBeenCalledWith(['/']);
  });

  it('ante un error de login muestra el mensaje neutro', () => {
    sessionMock.start.mockReturnValue(throwError(() => new Error('401')));
    component.form.setValue({ email: 'admin@club.local', password: 'incorrecta' });
    component.submit();
    expect(component.error()).toBe(true);
  });

  it('ante PASSWORD_EXPIRED guarda las credenciales y navega al cambio obligatorio', () => {
    const err = new HttpErrorResponse({ status: 409, error: { code: 'PASSWORD_EXPIRED' } });
    sessionMock.start.mockReturnValue(throwError(() => err));
    component.form.setValue({ email: 'admin@club.local', password: 'caducada12345' });
    component.submit();
    expect(sessionMock.stashExpiredCredentials).toHaveBeenCalledWith(
      'admin@club.local',
      'caducada12345',
    );
    expect(navigate).toHaveBeenCalledWith(['/cambiar-contrasena']);
    expect(component.error()).toBe(false);
  });
});
