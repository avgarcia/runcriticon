import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { SessionService } from './session.service';

/** Rol único autorizado por el backend para `PLAN:CREATE`/`PLAN:LIST`/`COACH_ALERT:LIST` (ver
 * `AuthorizationMatrix`). */
const COACH_ROLES = ['ENTRENADOR'];

/**
 * Protege rutas exclusivas del entrenador (UX — la autorización real la hace el backend, ADR-0009). A
 * diferencia de `staffGuard`, el admin no entra: crear/ver planes y el panel de alertas son actos
 * operativos de quien entrena.
 *
 * Misma estrategia que `staffGuard`/`adminGuard`.
 */
export const coachGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);
  const s = session.session();
  if (s !== null) {
    return COACH_ROLES.includes(s.role) ? true : router.createUrlTree(['/']);
  }
  return session.loadCurrent().pipe(
    map((loaded) => (COACH_ROLES.includes(loaded.role) ? true : router.createUrlTree(['/']))),
    catchError(() => of(router.createUrlTree(['/']))),
  );
};
