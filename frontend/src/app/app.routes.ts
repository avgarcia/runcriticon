import { Routes } from '@angular/router';

/**
 * Rutas raíz (ADR-0012 D10, lazy loading por feature). Cada feature agrupa sus rutas en su
 * propio `*.routes.ts`, cargado vía `loadChildren`.
 */
export const routes: Routes = [
  {
    path: '',
    loadChildren: () =>
      import('./features/identidad/identidad.routes').then((m) => m.IDENTIDAD_ROUTES),
  },
  { path: '**', redirectTo: '' },
];
