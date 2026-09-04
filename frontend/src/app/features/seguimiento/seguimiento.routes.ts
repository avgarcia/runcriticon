import { Routes } from '@angular/router';

/**
 * Rutas del ALUMNO en esta feature (LAL-29): la home. `app.routes.ts` las cuelga bajo `/mi-plan` con
 * `StudentShellComponent` como padre y `studentGuard` — no del shell de gestión.
 */
export const SEGUIMIENTO_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/my-week.component').then((m) => m.MyWeekComponent),
  },
];

/**
 * Rutas del ENTRENADOR en esta feature (LAL-116, panel de alertas): a diferencia de
 * [SEGUIMIENTO_ROUTES], cuelgan del shell de gestión (`AppShellComponent`). `app.routes.ts` monta
 * este array bajo `path: 'alertas'` con `coachGuard` — de ahí el `path: ''` interno, mismo patrón que
 * `CUENTA_ROUTES`/`MARCAS_ROUTES` bajo sus respectivos segmentos de nivel raíz. Un segundo `path: ''`
 * *hermano* del `path: ''` de la home del shell de gestión ya rompió `authGuard` una vez (ver el
 * comentario de `app.routes.ts`) — por eso este array nunca se monta directamente en `children`, sino
 * bajo su propio segmento con nombre.
 */
export const SEGUIMIENTO_COACH_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/coach-alerts.component').then((m) => m.CoachAlertsComponent),
  },
];
