import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { ApiConfiguration } from '../../api/generated/api-configuration';
import { SesionService } from '../../api/generated/services/sesion.service';

/**
 * Rutas anónimas de `/sesion` donde un 401 es un error de dominio (credenciales o token
 * inválidos), no sesión caducada — la propia pantalla pública lo maneja inline: login
 * (`AuthenticateUser`), cambio de contraseña caducada (`ChangeExpiredPassword`), consumo de
 * magic-link/reseteo con cuenta inactiva (`ConsumeMagicLink`/`ConsumePasswordReset`).
 *
 * `/sesion/actual` también se excluye: la redirige ya `authGuard`, que es quien conoce la ruta de
 * destino real (`state.url`) — el interceptor, en pleno guard, todavía vería la ruta *anterior* en
 * `router.url` y calcularía un `returnUrl` equivocado.
 */
function anonymousAuthPaths(rootUrl: string): string[] {
  return [
    SesionService.IniciarSesionPath,
    SesionService.CambiarContrasenaCaducadaPath,
    SesionService.ConsumirMagicLinkPath,
    SesionService.ConsumirReseteoPath,
    SesionService.ConsultarSesionPath,
  ].map((path) => rootUrl + path);
}

/**
 * Interceptor de autenticación (ADR-0012 D15). Cubre la sesión que caduca mientras el usuario ya
 * está en una pantalla activada (p. ej. clic en una acción con la cookie expirada): en ese caso no
 * hay navegación en curso, así que `router.url` es la página actual — el `returnUrl` correcto.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const config = inject(ApiConfiguration);
  const excludedPaths = anonymousAuthPaths(config.rootUrl);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && !excludedPaths.includes(req.url)) {
        void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
      }
      return throwError(() => err);
    }),
  );
};
