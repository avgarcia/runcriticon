import { Routes } from '@angular/router';
import { adminGuard } from '../../core/admin.guard';
import { staffGuard } from '../../core/staff.guard';

/**
 * Rutas de la feature Club. Cuelgan del shell autenticado (`app.routes.ts`), que ya aplica el
 * `authGuard`; aquí solo se añade la restricción de rol.
 *
 * El guard no sobra porque el menú ya oculte la entrada: ocultar es UX y la URL se puede teclear a
 * mano. La barrera real sigue siendo el backend.
 *
 * Los grupos van con `staffGuard` y no con `adminGuard`: el entrenador es quien arma los grupos con
 * los que trabaja, y el backend le concede tanto listarlos como crearlos.
 */
export const CLUB_ROUTES: Routes = [
  {
    path: 'grupos/nuevo',
    canActivate: [staffGuard],
    loadComponent: () =>
      import('./pages/group-builder.component').then((m) => m.GroupBuilderComponent),
  },
  {
    path: 'grupos',
    canActivate: [staffGuard],
    loadComponent: () => import('./pages/groups-list.component').then((m) => m.GroupsListComponent),
  },
  {
    path: 'ajustes',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./pages/club-settings.component').then((m) => m.ClubSettingsComponent),
  },
  {
    path: 'taxonomia',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./pages/taxonomy-editor.component').then((m) => m.TaxonomyEditorComponent),
  },
  {
    path: 'entrenadores',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./pages/coaches-list.component').then((m) => m.CoachesListComponent),
  },
];
