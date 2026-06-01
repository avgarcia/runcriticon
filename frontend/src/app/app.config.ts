import {
  ApplicationConfig,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import {
  provideHttpClient,
  withXsrfConfiguration,
  withFetch,
} from '@angular/common/http';

import { routes } from './app.routes';

/**
 * Configuración raíz de la app (standalone, ADR-0012).
 *
 * - Router con lazy loading por feature (ADR-0012 D10) — rutas en app.routes.ts.
 * - HttpClient con CSRF: lee la cookie XSRF-TOKEN y la reenvía como X-XSRF-TOKEN
 *   (cruce ADR-0003 D14). La cookie de sesión httpOnly la adjunta el navegador sola.
 * - Animaciones de Material cargadas async.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideAnimationsAsync(),
    provideHttpClient(
      withFetch(),
      withXsrfConfiguration({
        cookieName: 'XSRF-TOKEN',
        headerName: 'X-XSRF-TOKEN',
      }),
    ),
  ],
};
