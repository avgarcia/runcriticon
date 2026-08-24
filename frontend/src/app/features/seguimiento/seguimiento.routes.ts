import { Routes } from '@angular/router';

/**
 * Rutas de la feature Seguimiento (LAL-29): la home del alumno. `app.routes.ts` las cuelga bajo
 * `/mi-plan` con `StudentShellComponent` como padre y `studentGuard` — no del shell de gestión.
 */
export const SEGUIMIENTO_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/my-week.component').then((m) => m.MyWeekComponent),
  },
];
