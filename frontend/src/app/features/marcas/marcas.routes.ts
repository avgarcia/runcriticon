import { Routes } from '@angular/router';

/**
 * Rutas de la feature Marcas (LAL-31): la pantalla "Mis marcas" del alumno. `app.routes.ts` las cuelga
 * bajo `/mis-marcas` con `StudentShellComponent` como padre y `studentGuard` — mismo patrón que
 * `seguimiento.routes.ts`/`cuenta.routes.ts`, tercer segmento de ruta raíz propio del alumno.
 */
export const MARCAS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/my-marks.component').then((m) => m.MyMarksComponent),
  },
];
