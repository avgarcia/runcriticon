import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { SessionService } from './session.service';
import { studentGuard } from './student.guard';

describe('studentGuard', () => {
  const urlTree = {} as UrlTree;
  let sessionValue: { role: string } | null;
  const sessionMock = { session: () => sessionValue, loadCurrent: jest.fn() };
  const routerMock = { createUrlTree: jest.fn(() => urlTree) };

  const run = () =>
    TestBed.runInInjectionContext(() =>
      studentGuard({} as ActivatedRouteSnapshot, { url: '/mi-plan' } as RouterStateSnapshot),
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

  it('permite el acceso a un ALUMNO ya en sesión', () => {
    sessionValue = { role: 'ALUMNO' };
    expect(run()).toBe(true);
  });

  it('redirige a la home a un ENTRENADOR', () => {
    sessionValue = { role: 'ENTRENADOR' };
    expect(run()).toBe(urlTree);
    expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/']);
  });

  it('si la sesión no está en memoria, la carga y decide por el rol', (done) => {
    sessionMock.loadCurrent.mockReturnValue(of({ role: 'ALUMNO' }));
    (run() as Observable<boolean | UrlTree>).subscribe((value) => {
      expect(value).toBe(true);
      done();
    });
  });

  it('ante un 401 redirige a /login conservando la ruta pedida en returnUrl', (done) => {
    sessionMock.loadCurrent.mockReturnValue(throwError(() => new Error('401')));
    (run() as Observable<boolean | UrlTree>).subscribe((value) => {
      expect(value).toBe(urlTree);
      expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/login'], {
        queryParams: { returnUrl: '/mi-plan' },
      });
      done();
    });
  });
});
