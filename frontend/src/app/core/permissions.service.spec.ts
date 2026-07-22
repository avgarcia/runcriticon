import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { PermissionsService } from './permissions.service';
import { MeService } from '../api/generated/services/me.service';

describe('PermissionsService', () => {
  let service: PermissionsService;
  const apiMock = {
    consultarMisPermisos: jest.fn(),
  };

  /** Lo que devuelve la matriz de autorización para un ADMIN. */
  const permisosDeAdmin = {
    COACH: ['INVITE', 'LIST'],
    STUDENT: ['INVITE'],
    USER: ['REVOKE_SESSIONS', 'DEACTIVATE'],
    CLUB: ['UPDATE'],
  };

  beforeEach(() => {
    jest.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [{ provide: MeService, useValue: apiMock }],
    });
    service = TestBed.inject(PermissionsService);
  });

  it('can devuelve true para una acción concedida', async () => {
    apiMock.consultarMisPermisos.mockResolvedValue(permisosDeAdmin);

    await firstValueFrom(service.load());

    expect(service.can('CLUB', 'UPDATE')).toBe(true);
  });

  it('can devuelve false para una acción no concedida sobre un recurso presente', async () => {
    apiMock.consultarMisPermisos.mockResolvedValue(permisosDeAdmin);

    await firstValueFrom(service.load());

    expect(service.can('STUDENT', 'LIST')).toBe(false);
  });

  it('can devuelve false para un recurso ausente del mapa (alumno: mapa vacío)', async () => {
    apiMock.consultarMisPermisos.mockResolvedValue({});

    await firstValueFrom(service.load());

    expect(service.can('CLUB', 'UPDATE')).toBe(false);
  });

  it('can devuelve false mientras no se hayan cargado los permisos (fail-closed)', () => {
    expect(service.can('CLUB', 'UPDATE')).toBe(false);
  });

  it('can devuelve false si la carga falla (fail-closed)', async () => {
    apiMock.consultarMisPermisos.mockRejectedValue({ status: 500 });

    await expect(firstValueFrom(service.load())).rejects.toBeDefined();

    expect(service.can('CLUB', 'UPDATE')).toBe(false);
  });

  it('loadOnce no repite la llamada si los permisos ya están en memoria', async () => {
    apiMock.consultarMisPermisos.mockResolvedValue(permisosDeAdmin);

    service.loadOnce();
    await Promise.resolve();
    service.loadOnce();

    expect(apiMock.consultarMisPermisos).toHaveBeenCalledTimes(1);
  });

  it('reset vacía la caché', async () => {
    apiMock.consultarMisPermisos.mockResolvedValue(permisosDeAdmin);
    await firstValueFrom(service.load());

    service.reset();

    expect(service.permissions()).toBeNull();
    expect(service.can('CLUB', 'UPDATE')).toBe(false);
  });
});
