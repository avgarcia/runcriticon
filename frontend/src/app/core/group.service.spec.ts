import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { GruposService } from '../api/generated/services/grupos.service';
import { GroupService } from './group.service';

describe('GroupService', () => {
  const resumen = { id: 'g1', nombre: 'Maratón Valencia', valores: ['v1'], totalAlumnos: 12 };
  const apiMock = {
    listarGrupos: jest.fn(),
    previsualizarMiembrosDeGrupo: jest.fn(),
    crearGrupo: jest.fn(),
  };
  let service: GroupService;

  beforeEach(() => {
    jest.clearAllMocks();
    apiMock.listarGrupos.mockResolvedValue({ grupos: [resumen] });
    apiMock.previsualizarMiembrosDeGrupo.mockResolvedValue({ total: 0, alumnos: [] });
    apiMock.crearGrupo.mockResolvedValue({ id: 'g2', nombre: 'Trail', valores: [] });

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

  it('previsualiza mandando los valores del filtro tal cual', async () => {
    await firstValueFrom(service.previewMembers(['v1', 'v2']));

    expect(apiMock.previsualizarMiembrosDeGrupo).toHaveBeenCalledWith({ tagValueId: ['v1', 'v2'] });
  });

  it('la previsualización no toca la caché del listado', async () => {
    await firstValueFrom(service.load());

    await firstValueFrom(service.previewMembers(['v1']));

    expect(service.groups()).toEqual([resumen]);
  });

  // El alta devuelve el grupo pero no cuántos alumnos caen dentro: parchear la caché con un cero
  // pintaría un número falso en la lista, así que se invalida y quien la necesite la recarga.
  it('crear invalida la caché en vez de parchearla', async () => {
    await firstValueFrom(service.load());
    expect(service.groups()).toBeDefined();

    await firstValueFrom(service.create('Trail', ['v1']));

    expect(apiMock.crearGrupo).toHaveBeenCalledWith({ body: { nombre: 'Trail', valores: ['v1'] } });
    expect(service.groups()).toBeUndefined();
  });

  it('reset vacía la caché al cerrar sesión', async () => {
    await firstValueFrom(service.load());

    service.reset();

    expect(service.groups()).toBeUndefined();
  });
});
