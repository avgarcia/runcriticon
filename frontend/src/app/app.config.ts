import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import {
  provideHttpClient,
  withXsrfConfiguration,
  withFetch,
  withInterceptors,
} from '@angular/common/http';

import { routes } from './app.routes';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { authInterceptor } from './core/interceptors/auth.interceptor';

/**
 * Configuración raíz de la app (standalone, ADR-0012).
 *
 * - Router con lazy loading por feature (ADR-0012 D10) — rutas en app.routes.ts.
 * - HttpClient con CSRF: lee la cookie XSRF-TOKEN y la reenvía como X-XSRF-TOKEN
 *   (cruce ADR-0003 D14). La cookie de sesión httpOnly la adjunta el navegador sola.
 * - Interceptores de errores (D14) y autenticación (D15): clasifican 403/429/5xx/red con un toast
 *   y redirigen a /login con returnUrl ante un 401 fuera de los flujos anónimos de sesión.
 * - Sin animaciones de framework: los helm de spartan usan transiciones CSS (tw-animate-css).
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(
      withFetch(),
      withXsrfConfiguration({
        cookieName: 'XSRF-TOKEN',
        headerName: 'X-XSRF-TOKEN',
      }),
      withInterceptors([errorInterceptor, authInterceptor]),
    ),
  ],
};
