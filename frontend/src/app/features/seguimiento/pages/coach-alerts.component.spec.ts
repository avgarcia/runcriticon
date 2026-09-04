import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { Alert, CoachAlertService } from '../../../core/coach-alert.service';
import { GroupService, GroupSummary } from '../../../core/group.service';
import { CoachAlertsComponent } from './coach-alerts.component';

describe('CoachAlertsComponent', () => {
  const coachAlertServiceMock = { getAlerts: jest.fn() };
  const groupsSignal = signal<GroupSummary[] | undefined>([]);
  const groupServiceMock = { groups: groupsSignal, load: jest.fn() };

  let fixture: ComponentFixture<CoachAlertsComponent>;
  let component: CoachAlertsComponent;

  const painAlert: Alert = {
    tipo: 'DOLOR_REPORTADO',
    alumnoId: 'a-1',
    grupoId: 'g-1',
    dia: '2026-09-01',
    notas: 'Pinchazo en el isquio',
    reportadoEn: new Date().toISOString(),
  };

  const noReportAlert: Alert = {
    tipo: 'SIN_REPORTAR',
    alumnoId: 'a-2',
    grupoId: 'g-1',
    diasSinReportar: 9,
  };

  const paceAlert: Alert = {
    tipo: 'RITMO_FUERA_DE_OBJETIVO',
    alumnoId: 'a-3',
    grupoId: 'g-1',
    dia: '2026-09-01',
    notas: 'Fui por encima del ritmo previsto',
  };

  async function crear(
    getAlertsReturn: Observable<Alert[]> = of([]),
    groups: GroupSummary[] = [],
  ): Promise<void> {
    jest.clearAllMocks();
    coachAlertServiceMock.getAlerts.mockReturnValue(getAlertsReturn);
    groupServiceMock.load.mockReturnValue(of(groups));
    groupsSignal.set(groups);

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [CoachAlertsComponent],
      providers: [
        { provide: CoachAlertService, useValue: coachAlertServiceMock },
        { provide: GroupService, useValue: groupServiceMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CoachAlertsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('carga las alertas de todos los grupos al entrar', async () => {
    await crear();

    expect(coachAlertServiceMock.getAlerts).toHaveBeenCalledWith(undefined);
  });

  it('sin alertas activas muestra el estado vacio', async () => {
    await crear(of([]));

    expect(fixture.nativeElement.textContent).toContain('Todo en orden');
  });

  it('separa dolor y sin reportar en Urgente, y ritmo fuera de objetivo en Informativo', async () => {
    await crear(of([painAlert, noReportAlert, paceAlert]));

    expect(component.urgentAlerts()).toHaveLength(2);
    expect(component.infoAlerts()).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('Pinchazo en el isquio');
    expect(fixture.nativeElement.textContent).toContain('9');
    expect(fixture.nativeElement.textContent).toContain('Fui por encima del ritmo previsto');
  });

  it('pinta el nombre del grupo, no el id, cuando ya se cargaron los grupos', async () => {
    await crear(of([painAlert]), [grupo('g-1', 'Maratón Valencia avanzado')]);

    expect(fixture.nativeElement.textContent).toContain('Maratón Valencia avanzado');
    expect(fixture.nativeElement.textContent).not.toContain('g-1');
  });

  it('si la carga falla ofrece reintentar', async () => {
    await crear(throwError(() => new Error('boom')) as Observable<never>);

    expect(component.loadFailed()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Reintentar');
  });

  it('elegir un grupo recarga acotado a ese grupo', async () => {
    await crear(of([]), [grupo('g-1', 'Grupo 1')]);
    coachAlertServiceMock.getAlerts.mockClear();

    component.selectGroup('g-1');

    expect(coachAlertServiceMock.getAlerts).toHaveBeenCalledWith('g-1');
  });

  it('volver a Todos mis grupos recarga sin filtro', async () => {
    await crear();
    component.selectGroup('g-1');
    coachAlertServiceMock.getAlerts.mockClear();

    component.selectGroup('');

    expect(coachAlertServiceMock.getAlerts).toHaveBeenCalledWith(undefined);
  });
});

function grupo(id: string, nombre: string): GroupSummary {
  return { id, nombre, totalAlumnos: 0, valores: [] };
}
