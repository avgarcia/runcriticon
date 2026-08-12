import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { GruposService } from '../api/generated/services/grupos.service';
import { GroupService } from './group.service';

describe('GroupService', () => {
  const resumen = { id: 'g1', nombre: 'Maratón Valencia', valores: ['v1'], totalAlumnos: 12 };
  const detalle = {
    id: 'g1',
    nombre: 'Maratón Valencia',
    valores: ['v1'],
    total: 1,
    miembros: [{ id: 'a1', nombre: 'Ana Ruiz', origen: 'FILTRO' as const, ajusteManual: false }],
    excluidos: [],
  };
  const apiMock = {
    listarGrupos: jest.fn(),
    previsualizarMiembrosDeGrupo: jest.fn(),
    crearGrupo: jest.fn(),
    consultarGrupo: jest.fn(),
    ajustarPertenenciaAGrupo: jest.fn(),
    quitarAjusteDePertenencia: jest.fn(),
  };
  let service: GroupService;

  beforeEach(() => {
    jest.clearAllMocks();
    apiMock.listarGrupos.mockResolvedValue({ grupos: [resumen] });
    apiMock.previsualizarMiembrosDeGrupo.mockResolvedValue({ total: 0, alumnos: [] });
    apiMock.crearGrupo.mockResolvedValue({ id: 'g2', nombre: 'Trail', valores: [] });
    apiMock.consultarGrupo.mockResolvedValue(detalle);
    apiMock.ajustarPertenenciaAGrupo.mockResolvedValue(detalle);
    apiMock.quitarAjusteDePertenencia.mockResolvedValue(undefined);

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

  it('el detalle pide el grupo por id sin tocar la caché del listado', async () => {
    await firstValueFrom(service.load());

    const grupo = await firstValueFrom(service.getDetail('g1'));

    expect(apiMock.consultarGrupo).toHaveBeenCalledWith({ grupoId: 'g1' });
    expect(grupo).toEqual(detalle);
    expect(service.groups()).toEqual([resumen]);
  });

  it('el ajuste de pertenencia manda el sentido de incluido', async () => {
    await firstValueFrom(service.setOverride('g1', 'a1', false));

    expect(apiMock.ajustarPertenenciaAGrupo).toHaveBeenCalledWith({
      grupoId: 'g1',
      alumnoId: 'a1',
      body: { incluido: false },
    });
  });

  it('quitar el ajuste llama al DELETE del override', async () => {
    await firstValueFrom(service.clearOverride('g1', 'a1'));

    expect(apiMock.quitarAjusteDePertenencia).toHaveBeenCalledWith({ grupoId: 'g1', alumnoId: 'a1' });
  });

  it('reset vacía la caché al cerrar sesión', async () => {
    await firstValueFrom(service.load());

    service.reset();

    expect(service.groups()).toBeUndefined();
  });
});
