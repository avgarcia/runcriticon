import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { SessionService } from './session.service';

/**
 * Protege las rutas que exigen sesión (ADR-0003). Consulta `GET /api/sesion/actual`: si responde,
 * deja pasar y deja la sesión cargada; si falla (401), redirige a `/login`. De paso, esa primera
 * petición provoca que el backend emita la cookie XSRF-TOKEN para el posterior POST de login.
 */
export const authGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);
  return session.loadCurrent().pipe(
    map(() => true),
    catchError(() => of(router.createUrlTree(['/login']))),
  );
};
