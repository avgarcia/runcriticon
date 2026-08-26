import { Routes } from '@angular/router';

/**
 * Rutas de la feature Cuenta (LAL-128): "Mi cuenta" del alumno. `app.routes.ts` las cuelga bajo
 * `/mi-cuenta` con `StudentShellComponent` como padre y `studentGuard` — mismo criterio que
 * `SEGUIMIENTO_ROUTES` bajo `/mi-plan`.
 */
export const CUENTA_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/my-account.component').then((m) => m.MyAccountComponent),
  },
];
