import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { InviteCoachDialogComponent } from './invite-coach-dialog.component';
import { CoachesService } from './coaches.service';

describe('InviteCoachDialogComponent', () => {
  let fixture: ComponentFixture<InviteCoachDialogComponent>;
  let component: InviteCoachDialogComponent;
  const coachesMock = { invite: jest.fn() };
  const dialogRefMock = { close: jest.fn() };

  beforeEach(async () => {
    jest.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [InviteCoachDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: CoachesService, useValue: coachesMock },
        { provide: MatDialogRef, useValue: dialogRefMock },
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

  it('con datos válidos cierra el dialog con el email', () => {
    coachesMock.invite.mockReturnValue(of({ id: 'abc-123' }));
    component.form.setValue({ name: 'Ana García', email: 'ana@club.local' });
    component.submit();
    expect(coachesMock.invite).toHaveBeenCalledWith('Ana García', 'ana@club.local');
    expect(dialogRefMock.close).toHaveBeenCalledWith('ana@club.local');
  });

  it('ante 409 muestra error de email duplicado', () => {
    coachesMock.invite.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, statusText: 'Conflict' })),
    );
    component.form.setValue({ name: 'Ana García', email: 'ana@club.local' });
    component.submit();
    expect(component.errorMessage()).toBe('Ya existe un entrenador con ese email.');
    expect(component.loading()).toBe(false);
  });

  it('ante 400 con message muestra el mensaje del backend', () => {
    coachesMock.invite.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { code: 'INVALID_INPUT', field: 'email', message: 'Formato de email inválido' },
          }),
      ),
    );
    component.form.setValue({ name: 'Ana García', email: 'ana@club.local' });
    component.submit();
    expect(component.errorMessage()).toBe('Formato de email inválido');
  });
});
