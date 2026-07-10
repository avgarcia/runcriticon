import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { InviteCoachDialogComponent } from './invite-coach-dialog.component';
import { EntrenadoresService } from '../api/generated/services/entrenadores.service';

describe('InviteCoachDialogComponent', () => {
  let fixture: ComponentFixture<InviteCoachDialogComponent>;
  let component: InviteCoachDialogComponent;
  const entrenadoresMock = { invitarEntrenador: jest.fn() };
  const dialogRefMock = { close: jest.fn() };

  beforeEach(async () => {
    jest.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [InviteCoachDialogComponent],
      providers: [
        { provide: EntrenadoresService, useValue: entrenadoresMock },
        { provide: BrnDialogRef, useValue: dialogRefMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(InviteCoachDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('se crea con el formulario inválido vacío', () => {
    expect(component).toBeTruthy();
    expect(component.form.invalid).toBe(true);
  });

  it('con datos válidos cierra el dialog con el email', async () => {
    entrenadoresMock.invitarEntrenador.mockResolvedValue({ id: 'abc-123' });
    component.form.setValue({ name: 'Ana García', email: 'ana@club.local' });
    await component.submit();
    expect(entrenadoresMock.invitarEntrenador).toHaveBeenCalledWith({
      body: { nombre: 'Ana García', email: 'ana@club.local' },
    });
    expect(dialogRefMock.close).toHaveBeenCalledWith('ana@club.local');
  });

  it('ante 409 muestra error de email duplicado', async () => {
    entrenadoresMock.invitarEntrenador.mockRejectedValue(
      new HttpErrorResponse({ status: 409, statusText: 'Conflict' }),
    );
    component.form.setValue({ name: 'Ana García', email: 'ana@club.local' });
    await component.submit();
    expect(component.errorMessage()).toBe('Ya existe un entrenador con ese email.');
    expect(component.loading()).toBe(false);
  });

  it('ante 400 con field=email marca el control con el mensaje del catálogo, no el message crudo', async () => {
    entrenadoresMock.invitarEntrenador.mockRejectedValue(
      new HttpErrorResponse({
        status: 400,
        error: { code: 'INVALID_INPUT', field: 'email', message: 'texto interno del backend' },
      }),
    );
    component.form.setValue({ name: 'Ana García', email: 'ana@club.local' });
    await component.submit();
    expect(component.form.controls.email.getError('backend')).toBe(
      'Revisa los datos introducidos.',
    );
    expect(component.form.controls.email.getError('backend')).not.toBe('texto interno del backend');
    expect(component.errorMessage()).toBeNull();
  });

  it('ante 400 sin field muestra el mensaje general del catálogo', async () => {
    entrenadoresMock.invitarEntrenador.mockRejectedValue(
      new HttpErrorResponse({
        status: 400,
        error: { code: 'INVALID_INPUT', message: 'texto interno del backend' },
      }),
    );
    component.form.setValue({ name: 'Ana García', email: 'ana@club.local' });
    await component.submit();
    expect(component.errorMessage()).toBe('Revisa los datos introducidos.');
  });
});
