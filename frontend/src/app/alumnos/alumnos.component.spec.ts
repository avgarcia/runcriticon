import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { AlumnosComponent } from './alumnos.component';
import { InviteAlumnoDialogComponent } from './invite-alumno-dialog.component';

describe('AlumnosComponent', () => {
  let fixture: ComponentFixture<AlumnosComponent>;
  let component: AlumnosComponent;
  const dialogMock = { open: jest.fn() };
  const snackBarMock = { open: jest.fn() };

  beforeEach(async () => {
    jest.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [AlumnosComponent],
      providers: [
        provideNoopAnimations(),
        { provide: MatDialog, useValue: dialogMock },
        { provide: MatSnackBar, useValue: snackBarMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AlumnosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('se crea correctamente', () => {
    expect(component).toBeTruthy();
  });

  it('openInviteDialog abre el dialog y muestra snackbar con el email', () => {
    dialogMock.open.mockReturnValue({ afterClosed: () => of('marta@club.local') });
    component.openInviteDialog();
    expect(dialogMock.open).toHaveBeenCalledWith(InviteAlumnoDialogComponent);
    expect(snackBarMock.open).toHaveBeenCalledWith(
      'Invitación enviada a marta@club.local',
      'Cerrar',
      { duration: 4000 },
    );
  });

  it('openInviteDialog no muestra snackbar si el dialog se cancela', () => {
    dialogMock.open.mockReturnValue({ afterClosed: () => of(undefined) });
    component.openInviteDialog();
    expect(snackBarMock.open).not.toHaveBeenCalled();
  });
});
