import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { SessionService } from './session.service';

/** Roles que el backend autoriza a dar de alta alumnos (ADR-0003 D3): admin y entrenador. */
const STAFF_ROLES = ['ADMIN', 'ENTRENADOR'];

/**
 * Protege rutas que pueden ver admin y entrenador (UX — la autorización real la hace el backend,
 * ADR-0009). El alta de alumnos está delegada al entrenador (ADR-0003 D3).
 *
 * Misma estrategia que `adminGuard`: si la sesión ya está en memoria (navegación client-side) la
 * comprobamos sin HTTP; si es `null` (guards en paralelo, la señal aún no fue cargada por
 * `authGuard`), la cargamos para no depender del orden de ejecución.
 */
export const staffGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);
  const s = session.session();
  if (s !== null) {
    return STAFF_ROLES.includes(s.role) ? true : router.createUrlTree(['/']);
  }
  return session.loadCurrent().pipe(
    map((loaded) => (STAFF_ROLES.includes(loaded.role) ? true : router.createUrlTree(['/']))),
    catchError(() => of(router.createUrlTree(['/']))),
  );
};
