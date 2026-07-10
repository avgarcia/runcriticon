import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { of } from 'rxjs';
import { AlumnosComponent } from './alumnos.component';
import { InviteAlumnoDialogComponent } from './invite-alumno-dialog.component';
import { ToastService } from '../core/toast.service';

describe('AlumnosComponent', () => {
  let fixture: ComponentFixture<AlumnosComponent>;
  let component: AlumnosComponent;
  const dialogServiceMock = { open: jest.fn() };
  const toastServiceMock = { error: jest.fn(), success: jest.fn() };

  beforeEach(async () => {
    jest.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [AlumnosComponent],
      providers: [
        { provide: HlmDialogService, useValue: dialogServiceMock },
        { provide: ToastService, useValue: toastServiceMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AlumnosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('se crea correctamente', () => {
    expect(component).toBeTruthy();
  });

  it('openInviteDialog abre el dialog y muestra toast con el email', () => {
    dialogServiceMock.open.mockReturnValue({ closed$: of('marta@club.local') });
    component.openInviteDialog();
    expect(dialogServiceMock.open).toHaveBeenCalledWith(InviteAlumnoDialogComponent);
    expect(toastServiceMock.success).toHaveBeenCalledWith('Invitación enviada a marta@club.local');
  });

  it('openInviteDialog no muestra toast si el dialog se cancela', () => {
    dialogServiceMock.open.mockReturnValue({ closed$: of(undefined) });
    component.openInviteDialog();
    expect(toastServiceMock.success).not.toHaveBeenCalled();
  });
});
