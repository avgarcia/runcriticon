import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { SessionService } from './session.service';
import { SesionService } from '../api/generated/services/sesion.service';

describe('SessionService', () => {
  let service: SessionService;
  const apiMock = {
    iniciarSesion: jest.fn(),
    consultarSesion: jest.fn(),
    cerrarSesion: jest.fn(),
    cambiarContrasenaCaducada: jest.fn(),
    solicitarMagicLink: jest.fn(),
    consumirMagicLink: jest.fn(),
  };

  beforeEach(() => {
    jest.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [{ provide: SesionService, useValue: apiMock }],
    });
    service = TestBed.inject(SessionService);
  });

  it('start delega en iniciarSesion del cliente generado y guarda la sesión', async () => {
    const session = { userId: 'u-1', clubId: 'c-1', role: 'ADMIN' };
    apiMock.iniciarSesion.mockResolvedValue(session);

    const result = await firstValueFrom(service.start('a@b.com', 'secreta'));

    expect(apiMock.iniciarSesion).toHaveBeenCalledWith({
      body: { email: 'a@b.com', password: 'secreta' },
    });
    expect(result).toEqual(session);
    expect(service.session()).toEqual(session);
  });

  it('loadCurrent delega en consultarSesion', async () => {
    apiMock.consultarSesion.mockResolvedValue({ userId: 'u-1', clubId: 'c-1', role: 'ALUMNO' });

    await firstValueFrom(service.loadCurrent());

    expect(service.session()?.role).toBe('ALUMNO');
  });

  it('close delega en cerrarSesion y limpia la sesión', async () => {
    apiMock.cerrarSesion.mockResolvedValue(undefined);

    await firstValueFrom(service.close());

    expect(service.session()).toBeNull();
  });

  it('changeExpiredPassword delega en cambiarContrasenaCaducada y guarda la sesión', async () => {
    const session = { userId: 'u-2', clubId: 'c-1', role: 'ENTRENADOR' };
    apiMock.cambiarContrasenaCaducada.mockResolvedValue(session);

    const result = await firstValueFrom(
      service.changeExpiredPassword('a@b.com', 'clave-vieja-larga', 'clave-nueva-larga'),
    );

    expect(apiMock.cambiarContrasenaCaducada).toHaveBeenCalledWith({
      body: {
        email: 'a@b.com',
        currentPassword: 'clave-vieja-larga',
        newPassword: 'clave-nueva-larga',
      },
    });
    expect(result).toEqual(session);
    expect(service.session()).toEqual(session);
  });

  it('requestMagicLink delega en solicitarMagicLink (sin guardar sesión)', async () => {
    apiMock.solicitarMagicLink.mockResolvedValue(undefined);

    await firstValueFrom(service.requestMagicLink('a@b.com'));

    expect(apiMock.solicitarMagicLink).toHaveBeenCalledWith({ body: { email: 'a@b.com' } });
    expect(service.session()).toBeNull();
  });

  it('consumeMagicLink delega en consumirMagicLink y guarda la sesión', async () => {
    const session = { userId: 'u-3', clubId: 'c-1', role: 'ALUMNO' };
    apiMock.consumirMagicLink.mockResolvedValue(session);

    const result = await firstValueFrom(service.consumeMagicLink('token-xyz'));

    expect(apiMock.consumirMagicLink).toHaveBeenCalledWith({ body: { token: 'token-xyz' } });
    expect(result).toEqual(session);
    expect(service.session()).toEqual(session);
  });

  it('stash/take de credenciales caducadas es de un solo uso', () => {
    service.stashExpiredCredentials('a@b.com', 'caducada123');

    expect(service.takeExpiredCredentials()).toEqual({ email: 'a@b.com', password: 'caducada123' });
    expect(service.takeExpiredCredentials()).toBeNull();
  });
});
