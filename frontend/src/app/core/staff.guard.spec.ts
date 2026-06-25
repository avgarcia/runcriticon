import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { SessionService } from './session.service';
import { staffGuard } from './staff.guard';

describe('staffGuard', () => {
  const urlTree = {} as UrlTree;
  let sessionValue: { role: string } | null;
  const sessionMock = {
    session: () => sessionValue,
    loadCurrent: jest.fn(),
  };
  const routerMock = { createUrlTree: jest.fn(() => urlTree) };

  const run = () =>
    TestBed.runInInjectionContext(() =>
      staffGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
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

  it('permite el acceso a un ADMIN ya en sesión', () => {
    sessionValue = { role: 'ADMIN' };
    expect(run()).toBe(true);
  });

  it('permite el acceso a un ENTRENADOR ya en sesión', () => {
    sessionValue = { role: 'ENTRENADOR' };
    expect(run()).toBe(true);
  });

  it('redirige a la home a un ALUMNO', () => {
    sessionValue = { role: 'ALUMNO' };
    expect(run()).toBe(urlTree);
    expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/']);
  });

  it('si la sesión no está en memoria, la carga y decide por el rol', (done) => {
    sessionValue = null;
    sessionMock.loadCurrent.mockReturnValue(of({ role: 'ENTRENADOR' }));
    (run() as Observable<boolean | UrlTree>).subscribe((value) => {
      expect(value).toBe(true);
      done();
    });
  });

  it('si la carga de sesión falla (401), redirige a la home', (done) => {
    sessionValue = null;
    sessionMock.loadCurrent.mockReturnValue(throwError(() => new Error('401')));
    (run() as Observable<boolean | UrlTree>).subscribe((value) => {
      expect(value).toBe(urlTree);
      done();
    });
  });
});
