import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { EntrenadoresService } from '../api/generated/services/entrenadores.service';
import { CoachService } from './coach.service';

describe('CoachService', () => {
  const entrenador = {
    id: 'c1',
    nombre: 'Carlos Ruiz',
    email: 'carlos@club.test',
    estado: 'ACTIVO' as const,
    grupos: [],
    totalAlumnos: 0,
  };
  const apiMock = {
    listarResumenDeEntrenadores: jest.fn(),
  };
  let service: CoachService;

  beforeEach(() => {
    jest.clearAllMocks();
    apiMock.listarResumenDeEntrenadores.mockResolvedValue({ entrenadores: [entrenador] });

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [CoachService, { provide: EntrenadoresService, useValue: apiMock }],
    });
    service = TestBed.inject(CoachService);
  });

  it('desenvuelve el listado de entrenadores de la respuesta y lo guarda', async () => {
    const entrenadores = await firstValueFrom(service.load());

    expect(entrenadores).toEqual([entrenador]);
    expect(service.coaches()).toEqual([entrenador]);
  });

  it('reset vacía la caché al cerrar sesión', async () => {
    await firstValueFrom(service.load());

    service.reset();

    expect(service.coaches()).toBeUndefined();
  });
});
