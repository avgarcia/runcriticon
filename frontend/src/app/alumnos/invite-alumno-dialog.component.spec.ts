import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { InviteAlumnoDialogComponent } from './invite-alumno-dialog.component';
import { AlumnosService } from '../api/generated/services/alumnos.service';

describe('InviteAlumnoDialogComponent', () => {
  let fixture: ComponentFixture<InviteAlumnoDialogComponent>;
  let component: InviteAlumnoDialogComponent;
  const alumnosMock = { invitarAlumno: jest.fn() };
  const dialogRefMock = { close: jest.fn() };

  beforeEach(async () => {
    jest.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [InviteAlumnoDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: AlumnosService, useValue: alumnosMock },
        { provide: MatDialogRef, useValue: dialogRefMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(InviteAlumnoDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('se crea con el formulario inválido vacío', () => {
    expect(component).toBeTruthy();
    expect(component.form.invalid).toBe(true);
  });

  it('con datos válidos cierra el dialog con el email', async () => {
    alumnosMock.invitarAlumno.mockResolvedValue({ id: 'abc-123' });
    component.form.setValue({ name: 'Marta Ruiz', email: 'marta@club.local' });
    await component.submit();
    expect(alumnosMock.invitarAlumno).toHaveBeenCalledWith({
      body: { nombre: 'Marta Ruiz', email: 'marta@club.local' },
    });
    expect(dialogRefMock.close).toHaveBeenCalledWith('marta@club.local');
  });

  it('ante 409 muestra error de email duplicado', async () => {
    alumnosMock.invitarAlumno.mockRejectedValue(
      new HttpErrorResponse({ status: 409, statusText: 'Conflict' }),
    );
    component.form.setValue({ name: 'Marta Ruiz', email: 'marta@club.local' });
    await component.submit();
    expect(component.errorMessage()).toBe('Ya existe un alumno con ese email.');
    expect(component.loading()).toBe(false);
  });

  it('ante 400 con field=email marca el control con el mensaje del catálogo, no el message crudo', async () => {
    alumnosMock.invitarAlumno.mockRejectedValue(
      new HttpErrorResponse({
        status: 400,
        error: { code: 'INVALID_INPUT', field: 'email', message: 'texto interno del backend' },
      }),
    );
    component.form.setValue({ name: 'Marta Ruiz', email: 'marta@club.local' });
    await component.submit();
    expect(component.form.controls.email.getError('backend')).toBe('Revisa los datos introducidos.');
    expect(component.form.controls.email.getError('backend')).not.toBe('texto interno del backend');
    expect(component.errorMessage()).toBeNull();
  });

  it('ante 400 sin field muestra el mensaje general del catálogo', async () => {
    alumnosMock.invitarAlumno.mockRejectedValue(
      new HttpErrorResponse({
        status: 400,
        error: { code: 'INVALID_INPUT', message: 'texto interno del backend' },
      }),
    );
    component.form.setValue({ name: 'Marta Ruiz', email: 'marta@club.local' });
    await component.submit();
    expect(component.errorMessage()).toBe('Revisa los datos introducidos.');
  });
});
