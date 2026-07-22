import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { routes } from './app.routes';
import { Session, SessionService } from './core/session.service';
import { ClubService } from './core/club.service';
import { PermissionsService } from './core/permissions.service';

/**
 * El árbol raíz monta dos bloques bajo el mismo `path: ''` — las rutas de acceso primero, el shell
 * autenticado después — y depende de que el router descarte el primero cuando ningún hijo casa.
 * Estos casos fijan esa mecánica: si alguien mete un `path: ''` o un comodín en las rutas públicas,
 * `/login` empezaría a exigir sesión y el login quedaría inalcanzable.
 */
describe('rutas raíz', () => {
  const session = signal<Session | null>(null);
  const sessionMock = {
    session,
    loadCurrent: jest.fn(),
    close: jest.fn().mockReturnValue(of(undefined)),
  };
  const clubMock = { club: signal(undefined), loadOnce: jest.fn(), reset: jest.fn() };
  const permissionsMock = {
    can: jest.fn().mockReturnValue(false),
    loadOnce: jest.fn(),
    reset: jest.fn(),
  };

  beforeEach(() => {
    jest.clearAllMocks();
    session.set(null);
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SessionService, useValue: sessionMock },
        { provide: ClubService, useValue: clubMock },
        { provide: PermissionsService, useValue: permissionsMock },
      ],
    });
  });

  it('/login se resuelve sin pasar por el authGuard', async () => {
    // Sin sesión en memoria: si /login cayera bajo el shell, el guard llamaría a loadCurrent.
    sessionMock.loadCurrent.mockReturnValue(throwError(() => new Error('no debería llamarse')));

    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/login');

    expect(sessionMock.loadCurrent).not.toHaveBeenCalled();
    // Y llega a pintarse de verdad, no se queda en una redirección silenciosa.
    expect(harness.routeNativeElement?.textContent).toContain('Inicia sesión');
  });

  it('/restablecer/nueva tampoco pasa por el authGuard', async () => {
    sessionMock.loadCurrent.mockReturnValue(throwError(() => new Error('no debería llamarse')));

    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/restablecer/nueva');

    expect(sessionMock.loadCurrent).not.toHaveBeenCalled();
  });

  it('la raíz sí pasa por el authGuard', async () => {
    session.set({ userId: 'u-1', clubId: 'c-1', role: 'ADMIN' });
    sessionMock.loadCurrent.mockReturnValue(of(session()));

    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');

    expect(sessionMock.loadCurrent).toHaveBeenCalled();
  });

  it('/coaches exige sesión de admin', async () => {
    session.set({ userId: 'u-1', clubId: 'c-1', role: 'ADMIN' });
    sessionMock.loadCurrent.mockReturnValue(of(session()));

    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/coaches');

    expect(sessionMock.loadCurrent).toHaveBeenCalled();
  });
});
