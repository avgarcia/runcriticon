import { DIALOG_DATA } from '@angular/cdk/dialog';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { of, throwError } from 'rxjs';
import { MyPlanService, MyResolvedSession } from '../../../core/my-plan.service';
import { ReportDialogComponent, ReportDialogData } from './report-dialog.component';

describe('ReportDialogComponent', () => {
  const myPlanServiceMock = { submitReport: jest.fn() };
  const dialogRefMock = { close: jest.fn() };

  let fixture: ComponentFixture<ReportDialogComponent>;
  let component: ReportDialogComponent;

  function session(overrides: Partial<MyResolvedSession> = {}): MyResolvedSession {
    return { dia: '2026-08-17', tipo: 'RODAJE', ...overrides };
  }

  async function crear(data: ReportDialogData) {
    jest.clearAllMocks();
    myPlanServiceMock.submitReport.mockReturnValue(of(session()));

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ReportDialogComponent],
      providers: [
        { provide: MyPlanService, useValue: myPlanServiceMock },
        { provide: BrnDialogRef, useValue: dialogRefMock },
        { provide: DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ReportDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('sin estado elegido, no se puede enviar', async () => {
    await crear({ day: '2026-08-17', session: session() });

    expect(component.canSubmit()).toBe(false);
  });

  it('HECHO sin valoracion no habilita el envio; con valoracion si', async () => {
    await crear({ day: '2026-08-17', session: session() });

    component.selectStatus('HECHO');
    expect(component.canSubmit()).toBe(false);

    component.rating.set(4);
    expect(component.canSubmit()).toBe(true);
  });

  it('NO_HECHO sin motivo no habilita el envio; con motivo si, y limpia la valoracion', async () => {
    await crear({ day: '2026-08-17', session: session() });

    component.selectStatus('HECHO');
    component.rating.set(3);
    component.selectStatus('NO_HECHO');

    expect(component.rating()).toBeNull();
    expect(component.canSubmit()).toBe(false);

    component.reason.set('MOLESTIAS');
    expect(component.canSubmit()).toBe(true);
  });

  it('cambiar de NO_HECHO a HECHO limpia el motivo elegido', async () => {
    await crear({ day: '2026-08-17', session: session() });

    component.selectStatus('NO_HECHO');
    component.reason.set('CANSANCIO');
    component.selectStatus('HECHO');

    expect(component.reason()).toBeNull();
  });

  it('enviar HECHO llama al servicio con la valoracion y sin motivo, y cierra con true', async () => {
    await crear({ day: '2026-08-17', session: session() });
    component.selectStatus('HECHO');
    component.rating.set(4);
    component.form.patchValue({ notes: 'bien' });

    await component.submit();

    expect(myPlanServiceMock.submitReport).toHaveBeenCalledWith('2026-08-17', {
      estado: 'HECHO',
      valoracion: 4,
      motivo: undefined,
      notas: 'bien',
    });
    expect(dialogRefMock.close).toHaveBeenCalledWith(true);
  });

  it('enviar NO_HECHO con MOLESTIAS llama al servicio con el motivo y sin valoracion', async () => {
    await crear({ day: '2026-08-17', session: session() });
    component.selectStatus('NO_HECHO');
    component.reason.set('MOLESTIAS');

    await component.submit();

    expect(myPlanServiceMock.submitReport).toHaveBeenCalledWith('2026-08-17', {
      estado: 'NO_HECHO',
      valoracion: undefined,
      motivo: 'MOLESTIAS',
      notas: undefined,
    });
  });

  it('un reporte ya existente precarga estado, valoracion y notas, y el boton dice Actualizar', async () => {
    await crear({
      day: '2026-08-17',
      session: session({
        reporte: { estado: 'PARCIAL', valoracion: 2, notas: 'me costó', marcaDolor: false, reportadoEn: '2026-08-17T10:00:00Z' },
      }),
    });

    expect(component.status()).toBe('PARCIAL');
    expect(component.rating()).toBe(2);
    expect(component.form.getRawValue().notes).toBe('me costó');
    expect(fixture.nativeElement.textContent).toContain('Actualizar');
    expect(fixture.nativeElement.textContent).toContain('Editando reporte enviado');
  });

  it('un error de servidor muestra el mensaje y no cierra el dialogo', async () => {
    await crear({ day: '2026-08-17', session: session() });
    myPlanServiceMock.submitReport.mockReturnValue(throwError(() => new Error('boom')));
    component.selectStatus('HECHO');
    component.rating.set(4);

    await component.submit();

    expect(component.errorMessage()).not.toBeNull();
    expect(dialogRefMock.close).not.toHaveBeenCalled();
    expect(component.saving()).toBe(false);
  });

  it('cancelar cierra el dialogo con false', async () => {
    await crear({ day: '2026-08-17', session: session() });

    component.close();

    expect(dialogRefMock.close).toHaveBeenCalledWith(false);
  });
});
