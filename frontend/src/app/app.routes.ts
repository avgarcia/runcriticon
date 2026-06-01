import { Routes } from '@angular/router';

/**
 * Rutas raíz. En H0 Bloque 2A solo la pantalla trivial.
 * El login (Bloque 5) y las features con lazy loading (Bloque 1 funcional)
 * se añaden después siguiendo ADR-0012 D10 (estructura por features).
 */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./home/home.component').then((m) => m.HomeComponent),
  },
  { path: '**', redirectTo: '' },
];
