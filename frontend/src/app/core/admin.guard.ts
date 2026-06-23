import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SesionService } from './sesion.service';

/** Protege rutas que solo puede ver el admin (UX — la autorización real la hace el backend, ADR-0009). */
export const adminGuard: CanActivateFn = () => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  const s = sesion.sesion();
  return s?.rol === 'ADMIN' ? true : router.createUrlTree(['/']);
};
