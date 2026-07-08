import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { SessionService } from './session.service';

/**
 * Protege las rutas que exigen sesión (ADR-0003). Consulta `GET /api/sesion/actual`: si responde,
 * deja pasar y deja la sesión cargada; si falla (401), redirige a `/login` con `returnUrl` a la
 * ruta pedida (ADR-0012 D15) — `state.url` es la única fuente fiable de esa ruta aquí; el
 * interceptor de autenticación no la conoce en pleno guard. De paso, esa primera petición provoca
 * que el backend emita la cookie XSRF-TOKEN para el posterior POST de login.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const session = inject(SessionService);
  const router = inject(Router);
  return session.loadCurrent().pipe(
    map(() => true),
    catchError(() =>
      of(router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } })),
    ),
  );
};
