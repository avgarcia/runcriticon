import { Routes } from '@angular/router';
import { coachGuard } from '../../core/coach.guard';

/**
 * Rutas de la feature Planificación. Cuelgan del shell autenticado (`app.routes.ts`), que ya aplica el
 * `authGuard`; aquí solo se añade la restricción de rol.
 *
 * Todas con `coachGuard`, no `staffGuard`: `PLAN:CREATE`/`PLAN:LIST` son solo del entrenador (ver
 * `AuthorizationMatrix`), a diferencia de los grupos de `club_taxonomia`, que comparten admin y entrenador.
 */
export const PLANIFICACION_ROUTES: Routes = [
  {
    path: 'grupos/:grupoId/planes',
    canActivate: [coachGuard],
    loadComponent: () =>
      import('./pages/plans-list.component').then((m) => m.PlansListComponent),
  },
  {
    path: 'planes/:planId',
    canActivate: [coachGuard],
    loadComponent: () => import('./pages/plan-detail.component').then((m) => m.PlanDetailComponent),
  },
];
