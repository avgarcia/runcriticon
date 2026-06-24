import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { SessionService } from './session.service';

/** Protege rutas que solo puede ver el admin (UX — la autorización real la hace el backend, ADR-0009). */
export const adminGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);
  const s = session.session();
  // Si la sesión ya está en memoria (navegación client-side), la comprobamos sin HTTP.
  // Si es null (guards paralelos en Angular v15+, la señal aún no fue cargada por authGuard),
  // cargamos nosotros mismos para no depender del orden de ejecución.
  if (s !== null) {
    return s.role === 'ADMIN' ? true : router.createUrlTree(['/']);
  }
  return session.loadCurrent().pipe(
    map((loaded) => (loaded.role === 'ADMIN' ? true : router.createUrlTree(['/']))),
    catchError(() => of(router.createUrlTree(['/']))),
  );
};
