import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { AlumnosService } from '../api/generated/services/alumnos.service';
import { StudentService } from './student.service';

describe('StudentService', () => {
  const alumno = {
    id: 'a1',
    nombre: 'Pedro Cordero',
    email: 'pedro@club.test',
    estado: 'ACTIVO' as const,
    valores: ['v1'],
  };
  const apiMock = { listarAlumnos: jest.fn() };
  let service: StudentService;

  beforeEach(() => {
    jest.clearAllMocks();
    apiMock.listarAlumnos.mockResolvedValue({ alumnos: [alumno] });

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [StudentService, { provide: AlumnosService, useValue: apiMock }],
    });
    service = TestBed.inject(StudentService);
  });

  it('desenvuelve la lista de alumnos de la respuesta y la guarda', async () => {
    const alumnos = await firstValueFrom(service.load());

    expect(alumnos).toEqual([alumno]);
    expect(service.students()).toEqual([alumno]);
  });

  it('sin argumentos pide sin filtro (lista vacía, no ausente)', async () => {
    await firstValueFrom(service.load());

    expect(apiMock.listarAlumnos).toHaveBeenCalledWith({ tagValueId: [] });
  });

  it('manda los tagValueId del filtro tal cual', async () => {
    await firstValueFrom(service.load(['v1', 'v2']));

    expect(apiMock.listarAlumnos).toHaveBeenCalledWith({ tagValueId: ['v1', 'v2'] });
  });

  it('reset vacía la caché al cerrar sesión', async () => {
    await firstValueFrom(service.load());

    service.reset();

    expect(service.students()).toBeUndefined();
  });
});
