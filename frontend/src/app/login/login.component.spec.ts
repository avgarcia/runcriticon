import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { SesionService } from '../core/sesion.service';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  const sesionMock = { iniciar: jest.fn() };
  const routerMock = { navigate: jest.fn() };

  beforeEach(async () => {
    jest.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideNoopAnimations(),
        { provide: SesionService, useValue: sesionMock },
        { provide: Router, useValue: routerMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('se crea con el formulario inválido vacío', () => {
    expect(component).toBeTruthy();
    expect(component.form.invalid).toBe(true);
  });

  it('no llama al backend si el formulario es inválido', () => {
    component.enviar();
    expect(sesionMock.iniciar).not.toHaveBeenCalled();
  });

  it('con credenciales válidas inicia sesión y navega a la raíz', () => {
    sesionMock.iniciar.mockReturnValue(of({ userId: 'u', clubId: 'c', rol: 'ADMIN' }));
    component.form.setValue({ email: 'admin@club.local', password: 'secreta12345' });
    component.enviar();
    expect(sesionMock.iniciar).toHaveBeenCalledWith('admin@club.local', 'secreta12345');
    expect(routerMock.navigate).toHaveBeenCalledWith(['/']);
  });

  it('ante un error de login muestra el mensaje neutro', () => {
    sesionMock.iniciar.mockReturnValue(throwError(() => new Error('401')));
    component.form.setValue({ email: 'admin@club.local', password: 'incorrecta' });
    component.enviar();
    expect(component.error()).toBe(true);
  });
});
