import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { landingGuard } from './landing.guard';
import { SessionService } from './session.service';

describe('landingGuard', () => {
  const urlTree = {} as UrlTree;
  let sessionValue: { role: string } | null;
  const sessionMock = { session: () => sessionValue, loadCurrent: jest.fn() };
  const routerMock = { createUrlTree: jest.fn(() => urlTree) };

  const run = () =>
    TestBed.runInInjectionContext(() =>
      landingGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

  beforeEach(() => {
    jest.clearAllMocks();
    sessionValue = null;
    TestBed.configureTestingModule({
      providers: [
        { provide: SessionService, useValue: sessionMock },
        { provide: Router, useValue: routerMock },
      ],
    });
  });

  it('deja ver la home a un ENTRENADOR', () => {
    sessionValue = { role: 'ENTRENADOR' };
    expect(run()).toBe(true);
  });

  it('manda al ALUMNO a /mi-plan', () => {
    sessionValue = { role: 'ALUMNO' };
    expect(run()).toBe(urlTree);
    expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/mi-plan']);
  });

  it('si la sesión no está en memoria, la carga y decide por el rol', (done) => {
    sessionMock.loadCurrent.mockReturnValue(of({ role: 'ALUMNO' }));
    (run() as Observable<boolean | UrlTree>).subscribe((value) => {
      expect(value).toBe(urlTree);
      expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/mi-plan']);
      done();
    });
  });

  it('si la carga de sesión falla, redirige a /login en lugar de propagar el error', (done) => {
    sessionMock.loadCurrent.mockReturnValue(throwError(() => new Error('401')));
    (run() as Observable<boolean | UrlTree>).subscribe((value) => {
      expect(value).toBe(urlTree);
      expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/login']);
      done();
    });
  });
});
