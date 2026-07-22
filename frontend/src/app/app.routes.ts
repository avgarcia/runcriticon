import { Routes } from '@angular/router';
import { adminGuard } from './core/admin.guard';
import { authGuard } from './core/auth.guard';
import { staffGuard } from './core/staff.guard';
import { IDENTIDAD_PUBLIC_ROUTES } from './features/identidad/identidad.routes';

/**
 * Rutas raíz, con carga diferida por pantalla. Dos bloques:
 *
 * 1. Las rutas de acceso (login, activación, magic link, reseteo), que van **primero** y no pasan
 *    por `authGuard` — por eso no muestran cabecera ni navegación.
 * 2. El shell autenticado en `path: ''`, que envuelve al resto con la cabecera y el menú.
 *
 * El bloque público se inserta con spread y no con `loadChildren`. Se intentó lo segundo (dos
 * `path: ''` hermanos, confiando en que el router descartara el primero al no casar ningún hijo) y
 * **no funciona**: `/` dejaba de activar el `authGuard`. Traer el array de rutas de forma estática
 * cuesta unos bytes en el bundle inicial y no arrastra ningún componente — cada ruta conserva su
 * `loadComponent`, así que el lazy loading por pantalla sigue intacto.
 */
export const routes: Routes = [
  ...IDENTIDAD_PUBLIC_ROUTES,
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./shared/layout/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/identidad/pages/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'coaches',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/identidad/pages/coaches.component').then((m) => m.CoachesComponent),
      },
      {
        path: 'alumnos',
        canActivate: [staffGuard],
        loadComponent: () =>
          import('./features/identidad/pages/alumnos.component').then((m) => m.AlumnosComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
