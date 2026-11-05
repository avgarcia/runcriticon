import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { SeguimientoService } from '../api/generated/services/seguimiento.service';
import { MyPlanService } from './my-plan.service';

describe('MyPlanService', () => {
  const apiMock = {
    consultarMiSemana: jest.fn(),
    reportarSesion: jest.fn(),
    reajustarDia: jest.fn(),
    deshacerReajuste: jest.fn(),
  };
  let service: MyPlanService;

  beforeEach(() => {
    jest.clearAllMocks();
    apiMock.consultarMiSemana.mockResolvedValue({ semana: '2026-08-17' });
    apiMock.reportarSesion.mockResolvedValue({ dia: '2026-08-19' });
    apiMock.reajustarDia.mockResolvedValue({ dia: '2026-08-19' });
    apiMock.deshacerReajuste.mockResolvedValue({ extra: 'se descarta' });

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [MyPlanService, { provide: SeguimientoService, useValue: apiMock }],
    });
    service = TestBed.inject(MyPlanService);
  });

  it('pide la semana indicada', async () => {
    await firstValueFrom(service.getWeek('2026-08-17'));

    expect(apiMock.consultarMiSemana).toHaveBeenCalledWith({ semana: '2026-08-17' });
  });

  it('sin semana deja que el backend elija la semana en curso', async () => {
    await firstValueFrom(service.getWeek());

    expect(apiMock.consultarMiSemana).toHaveBeenCalledWith({ semana: undefined });
  });

  it('el reporte va al día y con el cuerpo indicados', async () => {
    const body = { estado: 'HECHO' } as never;

    await firstValueFrom(service.submitReport('2026-08-19', body));

    expect(apiMock.reportarSesion).toHaveBeenCalledWith({ dia: '2026-08-19', body });
  });

  it('el reajuste va al día de origen y con el cuerpo indicados', async () => {
    const body = { accion: 'SALTADA' } as never;

    await firstValueFrom(service.rescheduleDay('2026-08-19', body));

    expect(apiMock.reajustarDia).toHaveBeenCalledWith({ dia: '2026-08-19', body });
  });

  it('deshacer el reajuste no devuelve nada, sea cual sea la respuesta', async () => {
    const resultado = await firstValueFrom(service.withdrawAdjustment('2026-08-19'));

    expect(apiMock.deshacerReajuste).toHaveBeenCalledWith({ dia: '2026-08-19' });
    expect(resultado).toBeUndefined();
  });
});
