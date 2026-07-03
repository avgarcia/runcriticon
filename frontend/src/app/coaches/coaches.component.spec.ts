import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { CoachesComponent } from './coaches.component';
import { InviteCoachDialogComponent } from './invite-coach-dialog.component';
import { EntrenadoresService } from '../api/generated/services/entrenadores.service';
import { UsuariosService } from '../api/generated/services/usuarios.service';
import { CoachSummary } from '../api/generated/models/coach-summary';

describe('CoachesComponent', () => {
  const coach: CoachSummary = { id: 'c1', nombre: 'Ana Coach', email: 'ana@club.local', estado: 'ACTIVO' };

  let fixture: ComponentFixture<CoachesComponent>;
  let component: CoachesComponent;
  const dialogMock = { open: jest.fn() };
  const snackBarMock = { open: jest.fn() };
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
        provideNoopAnimations(),
        { provide: MatDialog, useValue: dialogMock },
        { provide: MatSnackBar, useValue: snackBarMock },
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

  it('openInviteDialog abre el dialog y muestra snackbar con el email', () => {
    dialogMock.open.mockReturnValue({ afterClosed: () => of('nuevo@club.local') });
    component.openInviteDialog();
    expect(dialogMock.open).toHaveBeenCalledWith(InviteCoachDialogComponent);
    expect(snackBarMock.open).toHaveBeenCalledWith(
      'Invitación enviada a nuevo@club.local',
      'Cerrar',
      { duration: 4000 },
    );
  });

  it('revoca las sesiones tras confirmar', () => {
    dialogMock.open.mockReturnValue({ afterClosed: () => of(true) });
    component.revoke(coach);
    expect(usuariosMock.revocarSesionesUsuario).toHaveBeenCalledWith({ id: 'c1' });
  });

  it('no revoca si se cancela la confirmación', () => {
    dialogMock.open.mockReturnValue({ afterClosed: () => of(undefined) });
    component.revoke(coach);
    expect(usuariosMock.revocarSesionesUsuario).not.toHaveBeenCalled();
  });

  it('desactiva la cuenta tras confirmar', () => {
    dialogMock.open.mockReturnValue({ afterClosed: () => of(true) });
    component.deactivate(coach);
    expect(usuariosMock.desactivarUsuario).toHaveBeenCalledWith({ id: 'c1' });
  });
});
