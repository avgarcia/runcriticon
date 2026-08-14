import { DIALOG_DATA } from '@angular/cdk/dialog';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { of, throwError } from 'rxjs';
import { ERROR_MESSAGES } from '../../../core/api/error-codes';
import { GroupDetail, GroupService } from '../../../core/group.service';
import { PlanDetail, PlanService } from '../../../core/plan.service';
import { PublishPlanDialogComponent, PublishPlanDialogData } from './publish-plan-dialog.component';

describe('PublishPlanDialogComponent', () => {
  let fixture: ComponentFixture<PublishPlanDialogComponent>;
  let component: PublishPlanDialogComponent;
  const dialogRefMock = { close: jest.fn() };

  const planMock: PlanDetail = {
    id: 'plan-1',
    grupoId: 'grupo-1',
    semana: '2026-08-17',
    estado: 'BORRADOR',
    sesiones: [{ id: 'sesion-1', dia: '2026-08-18', tipo: 'RODAJE' }],
  };

  const groupDetailMock: GroupDetail = {
    id: 'grupo-1',
    nombre: 'Maratón primavera',
    valores: [],
    excluidos: [],
    total: 2,
    miembros: [
      { id: 'alumno-1', nombre: 'Alba Reverte', origen: 'FILTRO', ajusteManual: false },
      { id: 'alumno-2', nombre: 'Carlos Domínguez', origen: 'FILTRO', ajusteManual: false },
    ],
  };

  const planServiceMock = { publish: jest.fn() };
  const groupServiceMock = { getDetail: jest.fn() };

  const datos = (overrides: Partial<PublishPlanDialogData> = {}): PublishPlanDialogData => ({
    plan: planMock,
    ...overrides,
  });

  async function crear(data: PublishPlanDialogData = datos()): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [PublishPlanDialogComponent],
      providers: [
        { provide: BrnDialogRef, useValue: dialogRefMock },
        { provide: DIALOG_DATA, useValue: data },
        { provide: PlanService, useValue: planServiceMock },
        { provide: GroupService, useValue: groupServiceMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PublishPlanDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    jest.clearAllMocks();
    groupServiceMock.getDetail.mockReturnValue(of(groupDetailMock));
    planServiceMock.publish.mockReturnValue(of({ plan: { ...planMock, estado: 'PUBLICADO' }, alumnosEnSnapshot: 2 }));
  });

  it('carga los miembros actuales del grupo del plan', async () => {
    await crear();

    expect(groupServiceMock.getDetail).toHaveBeenCalledWith('grupo-1');
    expect(component.members()).toEqual(groupDetailMock.miembros);
  });

  it('publicar llama al servicio con el id del plan y cierra con true', async () => {
    await crear();

    await component.publish();

    expect(planServiceMock.publish).toHaveBeenCalledWith('plan-1');
    expect(dialogRefMock.close).toHaveBeenCalledWith(true);
  });

  it('cancelar cierra sin llamar a la API', async () => {
    await crear();

    component.close();

    expect(dialogRefMock.close).toHaveBeenCalledWith(false);
    expect(planServiceMock.publish).not.toHaveBeenCalled();
  });

  it('un 403 no pinta mensaje propio: ya lo avisa el interceptor', async () => {
    await crear();
    planServiceMock.publish.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

    await component.publish();

    expect(component.errorMessage()).toBeNull();
  });

  it('un 409 de plan ya publicado pinta el mensaje del catalogo', async () => {
    await crear();
    planServiceMock.publish.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { code: 'PLAN_ALREADY_PUBLISHED', message: 'ya publicado' },
          }),
      ),
    );

    await component.publish();

    expect(component.errorMessage()).toBe(ERROR_MESSAGES['PLAN_ALREADY_PUBLISHED']);
  });

  it('un fallo al cargar los miembros no bloquea el resumen de sesiones', async () => {
    groupServiceMock.getDetail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    await crear();

    expect(component.members()).toEqual([]);
    expect(component.errorMessage()).toBeNull();
  });
});
