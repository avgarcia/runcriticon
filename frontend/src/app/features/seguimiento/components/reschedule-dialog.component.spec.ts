import { DIALOG_DATA } from '@angular/cdk/dialog';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { of, throwError } from 'rxjs';
import { MyPlanService, MyResolvedSession } from '../../../core/my-plan.service';
import { todayIsoDate } from '../date-format-es';
import { DaySlot } from '../pages/my-week.component';
import { RescheduleDialogComponent, RescheduleDialogData } from './reschedule-dialog.component';

/** Fechas relativas a `todayIsoDate()`, no fijas: igual que `my-week.component.spec.ts`, evita que el
 * test se vuelva flaky al cruzar el año real durante la ejecución. */
function addDaysIso(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

describe('RescheduleDialogComponent', () => {
  const myPlanServiceMock = { rescheduleDay: jest.fn() };
  const dialogRefMock = { close: jest.fn() };
  const hoy = todayIsoDate();
  const manana = addDaysIso(hoy, 1);
  const pasadoManana = addDaysIso(hoy, 2);

  let fixture: ComponentFixture<RescheduleDialogComponent>;
  let component: RescheduleDialogComponent;

  function session(overrides: Partial<MyResolvedSession> = {}): MyResolvedSession {
    return { dia: hoy, tipo: 'TEMPO', ...overrides };
  }

  function days(overrides: DaySlot[] = []): DaySlot[] {
    const base: DaySlot[] = [
      { day: hoy, label: 'hoy', session: session() },
      { day: manana, label: 'mañana' },
      { day: pasadoManana, label: 'pasado' },
    ];
    for (const override of overrides) {
      const index = base.findIndex((d) => d.day === override.day);
      if (index >= 0) base[index] = override;
      else base.push(override);
    }
    return base;
  }

  async function crear(data: RescheduleDialogData) {
    jest.clearAllMocks();
    myPlanServiceMock.rescheduleDay.mockReturnValue(
      of({ accion: 'MOVIDA', diaPlanificado: data.day, motivo: 'CANSANCIO', marcaDolor: false }),
    );

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [RescheduleDialogComponent],
      providers: [
        { provide: MyPlanService, useValue: myPlanServiceMock },
        { provide: BrnDialogRef, useValue: dialogRefMock },
        { provide: DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(RescheduleDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('sin accion elegida, no se puede aplicar', async () => {
    await crear({ day: hoy, session: session(), days: days() });

    expect(component.canSubmit()).toBe(false);
  });

  it('SALTADA solo necesita motivo, no dia destino', async () => {
    await crear({ day: hoy, session: session(), days: days() });

    component.selectAction('SALTADA');
    expect(component.canSubmit()).toBe(false);

    component.reason.set('CANSANCIO');
    expect(component.canSubmit()).toBe(true);
  });

  it('MOVIDA sin dia destino no habilita el envio; con dia libre y motivo si', async () => {
    await crear({ day: hoy, session: session(), days: days() });

    component.selectAction('MOVIDA');
    component.reason.set('IMPREVISTO');
    expect(component.canSubmit()).toBe(false);

    component.selectTargetDay(manana);
    expect(component.canSubmit()).toBe(true);
  });

  it('el dia de origen no aparece entre las opciones de destino', async () => {
    await crear({ day: hoy, session: session(), days: days() });

    expect(component.targetOptions().map((d) => d.day)).not.toContain(hoy);
    expect(component.targetOptions().map((d) => d.day)).toEqual([manana, pasadoManana]);
  });

  it('MOVIDA a un dia ocupado exige resolucionConflicto antes de habilitar el envio', async () => {
    const ocupado = days([{ day: manana, label: 'mañana', session: session({ dia: manana, tipo: 'SERIES' }) }]);
    await crear({ day: hoy, session: session(), days: ocupado });

    component.selectAction('MOVIDA');
    component.reason.set('CANSANCIO');
    component.selectTargetDay(manana);
    expect(component.canSubmit()).toBe(false);
    expect(component.occupantAt(manana)?.tipo).toBe('SERIES');

    component.conflictResolution.set('REEMPLAZAR');
    expect(component.canSubmit()).toBe(true);
  });

  it('cambiar de dia destino limpia la resolucionConflicto ya elegida', async () => {
    const ocupado = days([{ day: manana, label: 'mañana', session: session({ dia: manana }) }]);
    await crear({ day: hoy, session: session(), days: ocupado });
    component.selectAction('MOVIDA');
    component.selectTargetDay(manana);
    component.conflictResolution.set('INTERCAMBIAR');

    component.selectTargetDay(pasadoManana);

    expect(component.conflictResolution()).toBeNull();
  });

  it('cambiar de MOVIDA a SALTADA limpia el dia destino y la resolucionConflicto', async () => {
    await crear({ day: hoy, session: session(), days: days() });
    component.selectAction('MOVIDA');
    component.selectTargetDay(manana);

    component.selectAction('SALTADA');

    expect(component.targetDay()).toBeNull();
    expect(component.conflictResolution()).toBeNull();
  });

  it('enviar MOVIDA llama al servicio con accion, diaDestino y motivo', async () => {
    await crear({ day: hoy, session: session(), days: days() });
    component.selectAction('MOVIDA');
    component.selectTargetDay(manana);
    component.reason.set('CANSANCIO');
    component.form.patchValue({ message: 'salgo tarde del trabajo' });

    await component.submit();

    expect(myPlanServiceMock.rescheduleDay).toHaveBeenCalledWith(hoy, {
      accion: 'MOVIDA',
      diaDestino: manana,
      motivo: 'CANSANCIO',
      mensaje: 'salgo tarde del trabajo',
      resolucionConflicto: undefined,
    });
    expect(dialogRefMock.close).toHaveBeenCalledWith(true);
  });

  it('enviar SALTADA llama al servicio sin diaDestino', async () => {
    await crear({ day: hoy, session: session(), days: days() });
    component.selectAction('SALTADA');
    component.reason.set('MOLESTIAS');

    await component.submit();

    expect(myPlanServiceMock.rescheduleDay).toHaveBeenCalledWith(hoy, {
      accion: 'SALTADA',
      diaDestino: undefined,
      motivo: 'MOLESTIAS',
      mensaje: undefined,
      resolucionConflicto: undefined,
    });
  });

  it('un error de servidor muestra el mensaje y no cierra el dialogo', async () => {
    await crear({ day: hoy, session: session(), days: days() });
    myPlanServiceMock.rescheduleDay.mockReturnValue(throwError(() => new Error('boom')));
    component.selectAction('SALTADA');
    component.reason.set('CANSANCIO');

    await component.submit();

    expect(component.errorMessage()).not.toBeNull();
    expect(dialogRefMock.close).not.toHaveBeenCalled();
    expect(component.saving()).toBe(false);
  });

  it('cancelar cierra el dialogo con false', async () => {
    await crear({ day: hoy, session: session(), days: days() });

    component.close();

    expect(dialogRefMock.close).toHaveBeenCalledWith(false);
  });
});
