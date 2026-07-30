import { Routes } from '@angular/router';
import { adminGuard } from '../../core/admin.guard';

/**
 * Rutas de la feature Club. Cuelgan del shell autenticado (`app.routes.ts`), que ya aplica el
 * `authGuard`; aquí solo se añade la restricción de rol.
 *
 * El `adminGuard` no sobra porque el menú ya oculte la entrada: ocultar es UX y la URL se puede
 * teclear a mano. La barrera real sigue siendo el backend, que exige `CLUB:UPDATE`.
 */
export const CLUB_ROUTES: Routes = [
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
];
