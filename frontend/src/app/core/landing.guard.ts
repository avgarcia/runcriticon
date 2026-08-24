import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { SessionService } from './session.service';

/**
 * Redirige al ALUMNO fuera de `HomeComponent` (el placeholder de H0: «¡Estás dentro!» + volcado de
 * rol/userId/clubId) hacia su propia pantalla (LAL-29). ADMIN y ENTRENADOR siguen viendo `Home` sin
 * cambios — no hay nada que redirigir para ellos todavía.
 *
 * Va en la ruta hija `path: ''` del shell, no en el `path: ''` padre: `authGuard` ya garantiza sesión
 * cargada antes de llegar aquí, así que `session()` nunca es `null` en este punto.
 */
export const landingGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);
  const s = session.session();
  if (s !== null) {
    return s.role === 'ALUMNO' ? router.createUrlTree(['/mi-plan']) : true;
  }
  return session.loadCurrent().pipe(
    map((loaded) => (loaded.role === 'ALUMNO' ? router.createUrlTree(['/mi-plan']) : true)),
  );
};
