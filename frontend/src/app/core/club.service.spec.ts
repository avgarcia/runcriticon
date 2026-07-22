import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ClubService } from './club.service';
import { ClubService as ClubApi } from '../api/generated/services/club.service';

describe('ClubService', () => {
  let service: ClubService;
  const apiMock = {
    consultarClub: jest.fn(),
    actualizarClub: jest.fn(),
  };

  const club = { id: 'club-1', nombre: 'Club Atletismo Pinares', slug: null };

  beforeEach(() => {
    jest.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [{ provide: ClubApi, useValue: apiMock }],
    });
    service = TestBed.inject(ClubService);
  });

  it('arranca sin cargar (undefined), que no es lo mismo que no existir', () => {
    expect(service.club()).toBeUndefined();
  });

  it('load delega en consultarClub y guarda la ficha', async () => {
    apiMock.consultarClub.mockResolvedValue(club);

    const result = await firstValueFrom(service.load());

    expect(result).toEqual(club);
    expect(service.club()).toEqual(club);
  });

  it('deja la ficha en null si el backend responde 404', async () => {
    apiMock.consultarClub.mockRejectedValue(new HttpErrorResponse({ status: 404 }));

    await expect(firstValueFrom(service.load())).rejects.toBeDefined();

    expect(service.club()).toBeNull();
  });

  it('un 500 deja la ficha sin cargar, no como inexistente, para poder reintentar', async () => {
    apiMock.consultarClub.mockRejectedValue(new HttpErrorResponse({ status: 500 }));

    await expect(firstValueFrom(service.load())).rejects.toBeDefined();

    expect(service.club()).toBeUndefined();
  });

  it('tras un 500, loadOnce vuelve a intentar la carga', async () => {
    apiMock.consultarClub.mockRejectedValueOnce(new HttpErrorResponse({ status: 500 }));
    service.loadOnce();
    await Promise.resolve();
    await Promise.resolve();

    apiMock.consultarClub.mockResolvedValue(club);
    service.loadOnce();
    await Promise.resolve();

    expect(apiMock.consultarClub).toHaveBeenCalledTimes(2);
  });

  it('loadOnce no repite la llamada si la ficha ya está cargada', async () => {
    apiMock.consultarClub.mockResolvedValue(club);

    service.loadOnce();
    await Promise.resolve();
    service.loadOnce();

    expect(apiMock.consultarClub).toHaveBeenCalledTimes(1);
  });

  it('rename delega en actualizarClub y guarda la respuesta del servidor', async () => {
    const renamed = { ...club, nombre: 'Club Atletismo Central' };
    apiMock.actualizarClub.mockResolvedValue(renamed);

    const result = await firstValueFrom(service.rename('Club Atletismo Central'));

    expect(apiMock.actualizarClub).toHaveBeenCalledWith({
      body: { nombre: 'Club Atletismo Central' },
    });
    expect(result).toEqual(renamed);
    expect(service.club()).toEqual(renamed);
  });

  it('reset devuelve el estado a sin cargar', async () => {
    apiMock.consultarClub.mockResolvedValue(club);
    await firstValueFrom(service.load());

    service.reset();

    expect(service.club()).toBeUndefined();
  });
});
