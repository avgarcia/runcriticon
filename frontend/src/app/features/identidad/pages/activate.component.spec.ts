import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { ActivateComponent } from './activate.component';
import { ActivacionService } from '../../../api/generated/services/activacion.service';
import { CONSENT_TEXT_VERSION } from '../../../core/consent.service';
import { SessionService } from '../../../core/session.service';

describe('ActivateComponent', () => {
  let fixture: ComponentFixture<ActivateComponent>;
  let component: ActivateComponent;
  const invitationDetails = {
    nombre: 'Andrea López',
    club: 'Club Atletismo Pinares',
    rol: 'ALUMNO' as const,
  };
  const activacionMock = {
    activarCuenta: jest.fn(),
    consultarInvitacion: jest.fn().mockResolvedValue(invitationDetails),
  };
  const sessionMock = { loadCurrent: jest.fn() };
  const routerMock = { navigate: jest.fn() };
  const routeMock = { snapshot: { queryParamMap: { get: jest.fn().mockReturnValue('tok-123') } } };
  const validPassword = 'clave-clave-clave';

  beforeEach(async () => {
    jest.clearAllMocks();
    routeMock.snapshot.queryParamMap.get.mockReturnValue('tok-123');
    activacionMock.consultarInvitacion.mockResolvedValue(invitationDetails);
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
    // Se invoca y se espera directamente, sin pasar por detectChanges()/whenStable(): ngOnInit es
    // async y depender del ciclo de detección de cambios de Angular para esperarlo es propenso a
    // condiciones de carrera en el entorno de test (confirmado en CI) -- mismo criterio que ya usan
    // los tests de submit() de este fichero, que esperan la promesa directamente.
    await component.ngOnInit();
    fixture.detectChanges();
  });

  it('resuelve la invitación al cargar y deja el formulario inválido vacío', () => {
    expect(component).toBeTruthy();
    expect(activacionMock.consultarInvitacion).toHaveBeenCalledWith({ token: 'tok-123' });
    expect(component.invitationState()).toBe('valid');
    expect(component.greeting()).toContain('Andrea');
    expect(component.form.invalid).toBe(true);
  });

  it('sin token en la URL muestra el estado inválido sin consultar el backend', async () => {
    routeMock.snapshot.queryParamMap.get.mockReturnValue(null);
    activacionMock.consultarInvitacion.mockClear(); // descarta la llamada que ya hizo el fixture de beforeEach
    const localFixture = TestBed.createComponent(ActivateComponent);
    await localFixture.componentInstance.ngOnInit();

    expect(localFixture.componentInstance.invitationState()).toBe('invalid');
    expect(activacionMock.consultarInvitacion).not.toHaveBeenCalled();
  });

  it('si el backend rechaza el token (400/404/409) muestra el estado inválido', async () => {
    activacionMock.consultarInvitacion.mockRejectedValueOnce(
      new HttpErrorResponse({ status: 409 }),
    );
    const localFixture = TestBed.createComponent(ActivateComponent);
    await localFixture.componentInstance.ngOnInit();

    expect(localFixture.componentInstance.invitationState()).toBe('invalid');
  });

  it('con contraseñas válidas y coincidentes activa, carga la sesión y navega a la raíz', async () => {
    activacionMock.activarCuenta.mockResolvedValue({ userId: 'u', clubId: 'c', role: 'ALUMNO' });
    sessionMock.loadCurrent.mockReturnValue(of({ userId: 'u', clubId: 'c', role: 'ALUMNO' }));
    component.form.setValue({ password: validPassword, confirm: validPassword });

    await component.submit();

    expect(activacionMock.activarCuenta).toHaveBeenCalledWith({
      body: {
        token: 'tok-123',
        password: validPassword,
        consentimiento: false,
        versionConsentimiento: CONSENT_TEXT_VERSION,
      },
    });
    expect(routerMock.navigate).toHaveBeenCalledWith(['/']);
  });

  it('marcar la casilla de consentimiento la envia como true', async () => {
    activacionMock.activarCuenta.mockResolvedValue({ userId: 'u', clubId: 'c', role: 'ALUMNO' });
    sessionMock.loadCurrent.mockReturnValue(of({ userId: 'u', clubId: 'c', role: 'ALUMNO' }));
    component.form.setValue({ password: validPassword, confirm: validPassword });
    component.consentGranted.set(true);

    await component.submit();

    expect(activacionMock.activarCuenta).toHaveBeenCalledWith({
      body: {
        token: 'tok-123',
        password: validPassword,
        consentimiento: true,
        versionConsentimiento: CONSENT_TEXT_VERSION,
      },
    });
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

  it('ante CONSENTIMIENTO_REQUERIDO pide marcar la casilla', async () => {
    activacionMock.activarCuenta.mockRejectedValue(
      new HttpErrorResponse({ status: 400, error: { code: 'CONSENTIMIENTO_REQUERIDO' } }),
    );
    component.form.setValue({ password: validPassword, confirm: validPassword });

    await component.submit();

    expect(component.errorMessage()).toContain('casilla');
  });

  it('ante VERSION_CONSENTIMIENTO_OBSOLETA pide recargar la pagina', async () => {
    activacionMock.activarCuenta.mockRejectedValue(
      new HttpErrorResponse({ status: 409, error: { code: 'VERSION_CONSENTIMIENTO_OBSOLETA' } }),
    );
    component.form.setValue({ password: validPassword, confirm: validPassword });

    await component.submit();

    expect(component.errorMessage()).toContain('recarga');
  });
});
