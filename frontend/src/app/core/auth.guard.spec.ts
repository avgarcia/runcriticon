import { TestBed } from '@angular/core/testing';
import { Observable, firstValueFrom, of, throwError } from 'rxjs';
import { Router, RouterStateSnapshot, UrlTree, convertToParamMap } from '@angular/router';
import { authGuard } from './auth.guard';
import { SessionService } from './session.service';

describe('authGuard', () => {
  const sessionMock = { loadCurrent: jest.fn() };
  let router: Router;

  beforeEach(() => {
    jest.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [{ provide: SessionService, useValue: sessionMock }],
    });
    router = TestBed.inject(Router);
  });

  function runGuard(url: string): Observable<boolean | UrlTree> {
    const route = { paramMap: convertToParamMap({}) } as never;
    const state = { url } as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() => authGuard(route, state)) as Observable<
      boolean | UrlTree
    >;
  }

  it('deja pasar cuando hay sesión activa', async () => {
    sessionMock.loadCurrent.mockReturnValue(of({ userId: 'u', clubId: 'c', role: 'ADMIN' }));
    await expect(firstValueFrom(runGuard('/coaches'))).resolves.toBe(true);
  });

  it('ante fallo de sesión redirige a /login con returnUrl a la ruta pedida', async () => {
    sessionMock.loadCurrent.mockReturnValue(throwError(() => new Error('401')));
    const result = await firstValueFrom(runGuard('/coaches'));
    const expected = router.createUrlTree(['/login'], { queryParams: { returnUrl: '/coaches' } });
    expect(router.serializeUrl(result as UrlTree)).toBe(router.serializeUrl(expected));
  });
});
