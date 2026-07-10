import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { ActivateComponent } from './activate.component';
import { ActivacionService } from '../api/generated/services/activacion.service';
import { SessionService } from '../core/session.service';

describe('ActivateComponent', () => {
  let fixture: ComponentFixture<ActivateComponent>;
  let component: ActivateComponent;
  const activacionMock = { activarCuenta: jest.fn() };
  const sessionMock = { loadCurrent: jest.fn() };
  const routerMock = { navigate: jest.fn() };
  const routeMock = { snapshot: { queryParamMap: { get: jest.fn().mockReturnValue('tok-123') } } };
  const validPassword = 'clave-clave-clave';

  beforeEach(async () => {
    jest.clearAllMocks();
    routeMock.snapshot.queryParamMap.get.mockReturnValue('tok-123');
    await TestBed.configureTestingModule({
      imports: [ActivateComponent],
      providers: [
        { provide: ActivacionService, useValue: activacionMock },
        { provide: SessionService, useValue: sessionMock },
        { provide: Router, useValue: routerMock },
        { provide: ActivatedRoute, useValue: routeMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ActivateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('se crea con el formulario inválido vacío y con token presente', () => {
    expect(component).toBeTruthy();
    expect(component.hasToken).toBe(true);
    expect(component.form.invalid).toBe(true);
  });

  it('con contraseñas válidas y coincidentes activa, carga la sesión y navega a la raíz', async () => {
    activacionMock.activarCuenta.mockResolvedValue({ userId: 'u', clubId: 'c', role: 'ALUMNO' });
    sessionMock.loadCurrent.mockReturnValue(of({ userId: 'u', clubId: 'c', role: 'ALUMNO' }));
    component.form.setValue({ password: validPassword, confirm: validPassword });

    await component.submit();

    expect(activacionMock.activarCuenta).toHaveBeenCalledWith({
      body: { token: 'tok-123', password: validPassword },
    });
    expect(routerMock.navigate).toHaveBeenCalledWith(['/']);
  });

  it('si las contraseñas no coinciden no llama al backend', async () => {
    component.form.setValue({ password: validPassword, confirm: 'otra-cosa-distinta' });

    await component.submit();

    expect(activacionMock.activarCuenta).not.toHaveBeenCalled();
  });

  it('ante 409 muestra que la cuenta ya está activa', async () => {
    activacionMock.activarCuenta.mockRejectedValue(new HttpErrorResponse({ status: 409 }));
    component.form.setValue({ password: validPassword, confirm: validPassword });

    await component.submit();

    expect(component.errorMessage()).toContain('ya está activa');
    expect(component.loading()).toBe(false);
  });

  it('ante 400 muestra que el enlace no es válido', async () => {
    activacionMock.activarCuenta.mockRejectedValue(new HttpErrorResponse({ status: 400 }));
    component.form.setValue({ password: validPassword, confirm: validPassword });

    await component.submit();

    expect(component.errorMessage()).toContain('enlace');
  });
});
