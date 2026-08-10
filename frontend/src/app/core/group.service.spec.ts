import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { GruposService } from '../api/generated/services/grupos.service';
import { GroupService } from './group.service';

describe('GroupService', () => {
  const resumen = { id: 'g1', nombre: 'Maratón Valencia', valores: ['v1'], totalAlumnos: 12 };
  const apiMock = { listarGrupos: jest.fn() };
  let service: GroupService;

  beforeEach(() => {
    jest.clearAllMocks();
    apiMock.listarGrupos.mockResolvedValue({ grupos: [resumen] });

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [GroupService, { provide: GruposService, useValue: apiMock }],
    });
    service = TestBed.inject(GroupService);
  });

  it('desenvuelve la lista de grupos de la respuesta y la guarda', async () => {
    const grupos = await firstValueFrom(service.load());

    expect(grupos).toEqual([resumen]);
    expect(service.groups()).toEqual([resumen]);
  });

  it('reset vacía la caché al cerrar sesión', async () => {
    await firstValueFrom(service.load());

    service.reset();

    expect(service.groups()).toBeUndefined();
  });
});
