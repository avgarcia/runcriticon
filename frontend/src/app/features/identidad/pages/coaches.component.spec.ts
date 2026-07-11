import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { of } from 'rxjs';
import { CoachesComponent } from './coaches.component';
import { InviteCoachDialogComponent } from '../components/invite-coach-dialog.component';
import { EntrenadoresService } from '../../../api/generated/services/entrenadores.service';
import { UsuariosService } from '../../../api/generated/services/usuarios.service';
import { CoachSummary } from '../../../api/generated/models/coach-summary';
import { ToastService } from '../../../core/toast.service';

describe('CoachesComponent', () => {
  const coach: CoachSummary = {
    id: 'c1',
    nombre: 'Ana Coach',
    email: 'ana@club.local',
    estado: 'ACTIVO',
  };

  let fixture: ComponentFixture<CoachesComponent>;
  let component: CoachesComponent;
  const dialogServiceMock = { open: jest.fn() };
  const toastServiceMock = { error: jest.fn(), success: jest.fn() };
  const entrenadoresMock = { listarEntrenadores: jest.fn() };
  const usuariosMock = { revocarSesionesUsuario: jest.fn(), desactivarUsuario: jest.fn() };

  beforeEach(async () => {
    jest.clearAllMocks();
    entrenadoresMock.listarEntrenadores.mockReturnValue(of([coach]));
    usuariosMock.revocarSesionesUsuario.mockReturnValue(of(undefined));
    usuariosMock.desactivarUsuario.mockReturnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [CoachesComponent],
      providers: [
        { provide: HlmDialogService, useValue: dialogServiceMock },
        { provide: ToastService, useValue: toastServiceMock },
        { provide: EntrenadoresService, useValue: entrenadoresMock },
        { provide: UsuariosService, useValue: usuariosMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CoachesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('lista los entrenadores del club al iniciar', () => {
    expect(entrenadoresMock.listarEntrenadores).toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Ana Coach');
    expect(fixture.nativeElement.textContent).toContain('ana@club.local');
  });

  it('openInviteDialog abre el dialog y muestra toast con el email', () => {
    dialogServiceMock.open.mockReturnValue({ closed$: of('nuevo@club.local') });
    component.openInviteDialog();
    expect(dialogServiceMock.open).toHaveBeenCalledWith(InviteCoachDialogComponent);
    expect(toastServiceMock.success).toHaveBeenCalledWith('Invitación enviada a nuevo@club.local');
  });

  it('revoca las sesiones tras confirmar', () => {
    dialogServiceMock.open.mockReturnValue({ closed$: of(true) });
    component.revoke(coach);
    expect(usuariosMock.revocarSesionesUsuario).toHaveBeenCalledWith({ id: 'c1' });
  });

  it('no revoca si se cancela la confirmación', () => {
    dialogServiceMock.open.mockReturnValue({ closed$: of(undefined) });
    component.revoke(coach);
    expect(usuariosMock.revocarSesionesUsuario).not.toHaveBeenCalled();
  });

  it('desactiva la cuenta tras confirmar', () => {
    dialogServiceMock.open.mockReturnValue({ closed$: of(true) });
    component.deactivate(coach);
    expect(usuariosMock.desactivarUsuario).toHaveBeenCalledWith({ id: 'c1' });
  });
});
