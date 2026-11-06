import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { SessionService } from './session.service';

/** Único rol autorizado por el backend para `RESOLVED_SESSION:LIST` (ver `AuthorizationMatrix`). */
const STUDENT_ROLES = ['ALUMNO'];

/**
 * Protege rutas exclusivas del alumno (UX — la autorización real la hace el backend, ADR-0009).
 * Primer guard de rol para ALUMNO: hasta la vista "hoy" del alumno (arranque del módulo
 * seguimiento) solo existían `admin`/`staff`/`coach`.
 *
 * Estas rutas cuelgan de segmentos de nivel raíz propios, sin `authGuard` delante, así que este guard es
 * el único que ve un 401. Sin sesión redirige a `/login` conservando la ruta pedida en `returnUrl`
 * (ADR-0012 D15), igual que `authGuard`; un rol que no es ALUMNO va a la home.
 */
export const studentGuard: CanActivateFn = (_route, state) => {
  const session = inject(SessionService);
  const router = inject(Router);
  const s = session.session();
  if (s !== null) {
    return STUDENT_ROLES.includes(s.role) ? true : router.createUrlTree(['/']);
  }
  return session.loadCurrent().pipe(
    map((loaded) => (STUDENT_ROLES.includes(loaded.role) ? true : router.createUrlTree(['/']))),
    catchError(() =>
      of(router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } })),
    ),
  );
};
