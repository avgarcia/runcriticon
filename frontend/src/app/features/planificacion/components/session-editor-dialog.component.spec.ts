import { DIALOG_DATA } from '@angular/cdk/dialog';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { of, throwError } from 'rxjs';
import { ERROR_MESSAGES } from '../../../core/api/error-codes';
import { PlanService, PlanSession } from '../../../core/plan.service';
import { SessionEditorDialogComponent, SessionEditorDialogData } from './session-editor-dialog.component';

describe('SessionEditorDialogComponent', () => {
  let fixture: ComponentFixture<SessionEditorDialogComponent>;
  let component: SessionEditorDialogComponent;
  const dialogRefMock = { close: jest.fn() };

  const sessionMock: PlanSession = {
    id: 'sesion-1',
    dia: '2026-08-19',
    tipo: 'SERIES',
    volumen: { tipo: 'DISTANCIA', metros: 4000 },
    ritmo: { tipo: 'ABSOLUTO', segundosPorKm: 225 },
    notas: 'series de 400',
  };

  const planServiceMock = {
    addSession: jest.fn(),
    updateSession: jest.fn(),
    deleteSession: jest.fn(),
  };

  const datos = (overrides: Partial<SessionEditorDialogData> = {}): SessionEditorDialogData => ({
    planId: 'plan-1',
    day: '2026-08-19',
    ...overrides,
  });

  async function crear(data: SessionEditorDialogData = datos()): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [SessionEditorDialogComponent],
      providers: [
        { provide: BrnDialogRef, useValue: dialogRefMock },
        { provide: DIALOG_DATA, useValue: data },
        { provide: PlanService, useValue: planServiceMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SessionEditorDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    jest.clearAllMocks();
    planServiceMock.addSession.mockReturnValue(of({ ...sessionMock, id: 'nueva' }));
    planServiceMock.updateSession.mockReturnValue(of(sessionMock));
    planServiceMock.deleteSession.mockReturnValue(of(undefined));
  });

  it('en alta, sin tipo elegido no se puede guardar', async () => {
    await crear();

    expect(component.canSubmit()).toBe(false);
  });

  it('elegir un tipo sin mas campos ya permite guardar', async () => {
    await crear();

    component.selectType('RODAJE');

    expect(component.canSubmit()).toBe(true);
  });

  it('al guardar en alta, llama a addSession con el dia del hueco y el tipo elegido', async () => {
    await crear();
    component.selectType('RODAJE');

    await component.submit();

    expect(planServiceMock.addSession).toHaveBeenCalledWith(
      'plan-1',
      expect.objectContaining({ dia: '2026-08-19', tipo: 'RODAJE' }),
    );
    expect(dialogRefMock.close).toHaveBeenCalledWith(true);
  });

  it('volumen y ritmo van undefined si no se rellenan', async () => {
    await crear();
    component.selectType('RODAJE');

    await component.submit();

    const body = planServiceMock.addSession.mock.calls[0][1];
    expect(body.volumen).toBeUndefined();
    expect(body.ritmo).toBeUndefined();
    expect(body.notas).toBeUndefined();
  });

  it('un volumen de distancia y un ritmo absoluto se traducen al contrato', async () => {
    await crear();
    component.selectType('RODAJE');
    component.setVolumeType('DISTANCIA');
    component.form.controls.volumeValue.setValue(8000);
    component.form.controls.paceText.setValue('3:45');

    await component.submit();

    const body = planServiceMock.addSession.mock.calls[0][1];
    expect(body.volumen).toEqual({ tipo: 'DISTANCIA', metros: 8000 });
    expect(body.ritmo).toEqual({ tipo: 'ABSOLUTO', segundosPorKm: 225 });
  });

  it('elegir DESCANSO tras rellenar volumen y ritmo los limpia', async () => {
    await crear();
    component.selectType('RODAJE');
    component.setVolumeType('DISTANCIA');
    component.form.controls.volumeValue.setValue(8000);
    component.form.controls.paceText.setValue('3:45');

    component.selectType('DESCANSO');

    expect(component.volumeType()).toBeNull();
    expect(component.form.controls.paceText.value).toBe('');
  });

  it('un ritmo con formato invalido invalida el formulario', async () => {
    await crear();
    component.selectType('RODAJE');

    component.form.controls.paceText.setValue('no-es-un-ritmo');

    expect(component.form.invalid).toBe(true);
    expect(component.canSubmit()).toBe(false);
  });

  it('en edicion, precarga tipo, volumen, ritmo y notas de la sesion', async () => {
    await crear(datos({ session: sessionMock }));

    expect(component.selectedType()).toBe('SERIES');
    expect(component.volumeType()).toBe('DISTANCIA');
    expect(component.form.controls.volumeValue.value).toBe(4000);
    expect(component.form.controls.paceText.value).toBe('3:45');
    expect(component.form.controls.notes.value).toBe('series de 400');
  });

  it('al guardar en edicion, llama a updateSession sin dia', async () => {
    await crear(datos({ session: sessionMock }));

    await component.submit();

    expect(planServiceMock.updateSession).toHaveBeenCalledWith(
      'plan-1',
      'sesion-1',
      expect.not.objectContaining({ dia: expect.anything() }),
    );
  });

  it('eliminar sesion llama a deleteSession y cierra con true', async () => {
    await crear(datos({ session: sessionMock }));

    component.deleteSession();

    expect(planServiceMock.deleteSession).toHaveBeenCalledWith('plan-1', 'sesion-1');
    expect(dialogRefMock.close).toHaveBeenCalledWith(true);
  });

  it('cancelar cierra sin llamar a la API', async () => {
    await crear();

    component.close();

    expect(dialogRefMock.close).toHaveBeenCalledWith(false);
    expect(planServiceMock.addSession).not.toHaveBeenCalled();
  });

  it('un 403 no pinta mensaje propio: ya lo avisa el interceptor', async () => {
    await crear();
    component.selectType('RODAJE');
    planServiceMock.addSession.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

    await component.submit();

    expect(component.errorMessage()).toBeNull();
  });

  it('un 409 de dia duplicado pinta el mensaje del catalogo', async () => {
    await crear();
    component.selectType('RODAJE');
    planServiceMock.addSession.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { code: 'DUPLICATE_SESSION_DAY', field: 'dia', message: 'duplicate' },
          }),
      ),
    );

    await component.submit();

    expect(component.errorMessage()).toBe(ERROR_MESSAGES['DUPLICATE_SESSION_DAY']);
  });

  // LAL-26: personalizaciones.

  it('en alta, no muestra el bloque de personalizaciones (no hay sesion todavia)', async () => {
    await crear();

    const boton = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Gestionar'),
    );
    expect(boton).toBeUndefined();
  });

  it('en edicion, muestra el bloque de personalizaciones con el recuento recibido', async () => {
    await crear(datos({ session: sessionMock, personalizationCount: 2 }));

    const boton = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Gestionar'),
    );
    expect(boton).toBeDefined();
    expect(fixture.nativeElement.textContent).toContain('2 alumno(s) con un ajuste personalizado');
  });

  it('gestionar personalizaciones cierra el dialogo pidiendo abrir el otro, sin llamar a la API', async () => {
    await crear(datos({ session: sessionMock }));

    component.manage();

    expect(dialogRefMock.close).toHaveBeenCalledWith('manage-personalizations');
    expect(planServiceMock.updateSession).not.toHaveBeenCalled();
  });
});
