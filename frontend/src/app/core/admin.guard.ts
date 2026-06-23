import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionService } from './session.service';

/** Protege rutas que solo puede ver el admin (UX — la autorización real la hace el backend, ADR-0009). */
export const adminGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);
  const s = session.session();
  return s?.role === 'ADMIN' ? true : router.createUrlTree(['/']);
};
