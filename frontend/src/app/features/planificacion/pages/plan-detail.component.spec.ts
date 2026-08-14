import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { of, throwError } from 'rxjs';
import { PlanDetail, PlanService } from '../../../core/plan.service';
import { PlanDetailComponent } from './plan-detail.component';
import { PublishPlanDialogComponent } from '../components/publish-plan-dialog.component';
import { SessionEditorDialogComponent } from '../components/session-editor-dialog.component';

describe('PlanDetailComponent', () => {
  let fixture: ComponentFixture<PlanDetailComponent>;
  let component: PlanDetailComponent;

  const planMock: PlanDetail = {
    id: 'plan-1',
    grupoId: 'grupo-1',
    semana: '2026-08-17',
    estado: 'BORRADOR',
    sesiones: [
      {
        id: 'sesion-mar',
        dia: '2026-08-18',
        tipo: 'SERIES',
        volumen: { tipo: 'DISTANCIA', metros: 4000 },
        ritmo: { tipo: 'ABSOLUTO', segundosPorKm: 225 },
        notas: '8x400m',
      },
    ],
  };

  const planServiceMock = { get: jest.fn() };
  const dialogMock = { open: jest.fn() };

  async function crear(planId = 'plan-1'): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [PlanDetailComponent],
      providers: [
        { provide: PlanService, useValue: planServiceMock },
        { provide: HlmDialogService, useValue: dialogMock },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ planId }) } } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PlanDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    jest.clearAllMocks();
    planServiceMock.get.mockReturnValue(of(planMock));
    dialogMock.open.mockReturnValue({ closed$: of(undefined) });
  });

  it('carga el plan de la ruta y construye los 7 dias de la semana', async () => {
    await crear();

    expect(planServiceMock.get).toHaveBeenCalledWith('plan-1');
    expect(component.days().map((d) => d.day)).toEqual([
      '2026-08-17',
      '2026-08-18',
      '2026-08-19',
      '2026-08-20',
      '2026-08-21',
      '2026-08-22',
      '2026-08-23',
    ]);
  });

  it('el dia con sesion la lleva asociada, el resto no', async () => {
    await crear();

    const martes = component.days().find((d) => d.day === '2026-08-18');
    const lunes = component.days().find((d) => d.day === '2026-08-17');

    expect(martes?.session?.id).toBe('sesion-mar');
    expect(lunes?.session).toBeUndefined();
  });

  it('un fallo de carga se puede reintentar', async () => {
    planServiceMock.get.mockReturnValueOnce(throwError(() => new Error('boom')));
    await crear();

    expect(component.loadFailed()).toBe(true);
    expect(component.plan()).toBeUndefined();

    planServiceMock.get.mockReturnValue(of(planMock));
    component.reload();

    expect(component.plan()).toEqual(planMock);
  });

  it('abrir el editor para un dia vacio no pasa sesion', async () => {
    await crear();

    component.openEditor('2026-08-17');

    expect(dialogMock.open).toHaveBeenCalledWith(
      SessionEditorDialogComponent,
      expect.objectContaining({ context: { planId: 'plan-1', day: '2026-08-17', session: undefined } }),
    );
  });

  it('abrir el editor sobre una sesion existente la pasa al dialogo', async () => {
    await crear();
    const sesion = planMock.sesiones[0];

    component.openEditor('2026-08-18', sesion);

    expect(dialogMock.open).toHaveBeenCalledWith(
      SessionEditorDialogComponent,
      expect.objectContaining({ context: { planId: 'plan-1', day: '2026-08-18', session: sesion } }),
    );
  });

  it('si el dialogo cierra con true, recarga el plan', async () => {
    dialogMock.open.mockReturnValue({ closed$: of(true) });
    await crear();
    planServiceMock.get.mockClear();

    component.openEditor('2026-08-17');

    expect(planServiceMock.get).toHaveBeenCalledWith('plan-1');
  });

  it('si el dialogo se cancela, no recarga', async () => {
    dialogMock.open.mockReturnValue({ closed$: of(false) });
    await crear();
    planServiceMock.get.mockClear();

    component.openEditor('2026-08-17');

    expect(planServiceMock.get).not.toHaveBeenCalled();
  });

  it('el boton de publicar aparece en un plan en borrador', async () => {
    await crear();

    const boton = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (b) => (b as HTMLButtonElement).textContent?.trim() === 'Publicar al grupo',
    );

    expect(boton).toBeDefined();
  });

  it('el boton de publicar no aparece en un plan ya publicado, y sus dias no abren el editor', async () => {
    planServiceMock.get.mockReturnValue(of({ ...planMock, estado: 'PUBLICADO' }));
    await crear();

    const boton = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (b) => (b as HTMLButtonElement).textContent?.trim() === 'Publicar al grupo',
    );
    const anadir = Array.from(fixture.nativeElement.querySelectorAll('button')).find((b) =>
      (b as HTMLButtonElement).textContent?.includes('Añadir sesión'),
    );

    expect(boton).toBeUndefined();
    expect(anadir).toBeUndefined();
  });

  it('abrir publicar pasa el plan cargado al dialogo', async () => {
    await crear();

    component.openPublish(planMock);

    expect(dialogMock.open).toHaveBeenCalledWith(
      PublishPlanDialogComponent,
      expect.objectContaining({ context: { plan: planMock } }),
    );
  });

  it('si el dialogo de publicar cierra con true, recarga el plan', async () => {
    dialogMock.open.mockReturnValue({ closed$: of(true) });
    await crear();
    planServiceMock.get.mockClear();

    component.openPublish(planMock);

    expect(planServiceMock.get).toHaveBeenCalledWith('plan-1');
  });

  it('si el dialogo de publicar se cancela, no recarga', async () => {
    dialogMock.open.mockReturnValue({ closed$: of(false) });
    await crear();
    planServiceMock.get.mockClear();

    component.openPublish(planMock);

    expect(planServiceMock.get).not.toHaveBeenCalled();
  });
});
