import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { PlanesService } from '../api/generated/services/planes.service';
import { PlanService } from './plan.service';

describe('PlanService', () => {
  const apiMock = {
    listarPlanesEnBorrador: jest.fn(),
    crearPlanEnBorrador: jest.fn(),
    consultarPlan: jest.fn(),
    anadirSesion: jest.fn(),
    editarSesion: jest.fn(),
    eliminarSesion: jest.fn(),
    publicarPlan: jest.fn(),
    aplicarPersonalizacion: jest.fn(),
    retirarPersonalizacion: jest.fn(),
  };
  let service: PlanService;

  beforeEach(() => {
    jest.clearAllMocks();
    for (const fn of Object.values(apiMock)) fn.mockResolvedValue({});

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [PlanService, { provide: PlanesService, useValue: apiMock }],
    });
    service = TestBed.inject(PlanService);
  });

  it('el listado de borradores desenvuelve la lista de planes de la respuesta', async () => {
    const plan = { id: 'p1', semana: '2026-08-17' };
    apiMock.listarPlanesEnBorrador.mockResolvedValue({ planes: [plan] });

    const planes = await firstValueFrom(service.listDrafts('g1'));

    expect(apiMock.listarPlanesEnBorrador).toHaveBeenCalledWith({ grupoId: 'g1' });
    expect(planes).toEqual([plan]);
  });

  it('crear un borrador envía el grupo y la semana', async () => {
    await firstValueFrom(service.create('g1', '2026-08-17'));

    expect(apiMock.crearPlanEnBorrador).toHaveBeenCalledWith({
      body: { grupoId: 'g1', semana: '2026-08-17' },
    });
  });

  it('consulta un plan por su id', async () => {
    await firstValueFrom(service.get('p1'));

    expect(apiMock.consultarPlan).toHaveBeenCalledWith({ planId: 'p1' });
  });

  it('añadir, editar y borrar una sesión usan el plan y la sesión indicados', async () => {
    const alta = { dia: '2026-08-17' } as never;
    const cambio = { tipo: 'TEMPO' } as never;

    await firstValueFrom(service.addSession('p1', alta));
    await firstValueFrom(service.updateSession('p1', 's1', cambio));
    await firstValueFrom(service.deleteSession('p1', 's1'));

    expect(apiMock.anadirSesion).toHaveBeenCalledWith({ planId: 'p1', body: alta });
    expect(apiMock.editarSesion).toHaveBeenCalledWith({
      planId: 'p1',
      sesionId: 's1',
      body: cambio,
    });
    expect(apiMock.eliminarSesion).toHaveBeenCalledWith({ planId: 'p1', sesionId: 's1' });
  });

  it('publicar envía solo el id del plan', async () => {
    await firstValueFrom(service.publish('p1'));

    expect(apiMock.publicarPlan).toHaveBeenCalledWith({ planId: 'p1' });
  });

  it('aplicar y retirar una personalización identifican plan, sesión y alumno', async () => {
    const body = { mensaje: 'Hoy suave' } as never;

    await firstValueFrom(service.setPersonalization('p1', 's1', 'a1', body));
    await firstValueFrom(service.removePersonalization('p1', 's1', 'a1'));

    expect(apiMock.aplicarPersonalizacion).toHaveBeenCalledWith({
      planId: 'p1',
      sesionId: 's1',
      alumnoId: 'a1',
      body,
    });
    expect(apiMock.retirarPersonalizacion).toHaveBeenCalledWith({
      planId: 'p1',
      sesionId: 's1',
      alumnoId: 'a1',
    });
  });
});
