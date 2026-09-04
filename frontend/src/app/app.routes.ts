import { Routes } from '@angular/router';
import { adminGuard } from './core/admin.guard';
import { authGuard } from './core/auth.guard';
import { coachGuard } from './core/coach.guard';
import { landingGuard } from './core/landing.guard';
import { staffGuard } from './core/staff.guard';
import { studentGuard } from './core/student.guard';
import { IDENTIDAD_PUBLIC_ROUTES } from './features/identidad/identidad.routes';

/**
 * Rutas raíz, con carga diferida por pantalla. Cinco bloques:
 *
 * 1. Las rutas de acceso (login, activación, magic link, reseteo), que van **primero** y no pasan
 *    por `authGuard` — por eso no muestran cabecera ni navegación.
 * 2. `/mi-plan`, la home del ALUMNO (LAL-29), con su propio shell mobile-first
 *    (`StudentShellComponent`) y `studentGuard`.
 * 3. `/mi-cuenta`, el consentimiento de datos de salud del ALUMNO (LAL-128) — mismo shell y guard
 *    que `/mi-plan`, segmento de ruta propio en vez de un hijo suyo (ver más abajo).
 * 4. `/mis-marcas`, las marcas privadas del ALUMNO (LAL-31) — mismo shell y guard, tercer segmento
 *    de ruta propio.
 * 5. El shell de gestión en `path: ''`, que envuelve al resto (ADMIN/ENTRENADOR) con la cabecera y
 *    el menú.
 *
 * `/mi-plan`, `/mi-cuenta` y `/mis-marcas` son rutas de nivel raíz con path propio, no un segundo
 * `path: ''` hermano del shell de gestión: dos `path: ''` compitiendo ya rompió `authGuard` una vez
 * (ver el bloque 1 de más abajo) — mismo motivo por el que las rutas públicas se insertan con spread
 * y no `loadChildren`.
 */
export const routes: Routes = [
  ...IDENTIDAD_PUBLIC_ROUTES,
  {
    path: 'mi-plan',
    canActivate: [studentGuard],
    loadComponent: () =>
      import('./shared/layout/student-shell.component').then((m) => m.StudentShellComponent),
    loadChildren: () =>
      import('./features/seguimiento/seguimiento.routes').then((m) => m.SEGUIMIENTO_ROUTES),
  },
  {
    // Segmento de nivel raíz propio, hermano de `/mi-plan` — mismo motivo que aquel: dos `path: ''`
    // compitiendo rompió `authGuard` una vez, así que cada ruta del alumno lleva su propio segmento.
    path: 'mi-cuenta',
    canActivate: [studentGuard],
    loadComponent: () =>
      import('./shared/layout/student-shell.component').then((m) => m.StudentShellComponent),
    loadChildren: () => import('./features/cuenta/cuenta.routes').then((m) => m.CUENTA_ROUTES),
  },
  {
    // Mismo motivo que `/mi-cuenta`: tercer segmento de ruta propio del alumno (LAL-31).
    path: 'mis-marcas',
    canActivate: [studentGuard],
    loadComponent: () =>
      import('./shared/layout/student-shell.component').then((m) => m.StudentShellComponent),
    loadChildren: () => import('./features/marcas/marcas.routes').then((m) => m.MARCAS_ROUTES),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./shared/layout/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      {
        path: '',
        canActivate: [landingGuard],
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
          import('./features/club/pages/students-list.component').then(
            (m) => m.StudentsListComponent,
          ),
      },
      {
        path: 'club',
        loadChildren: () => import('./features/club/club.routes').then((m) => m.CLUB_ROUTES),
      },
      {
        path: 'planificacion',
        loadChildren: () =>
          import('./features/planificacion/planificacion.routes').then(
            (m) => m.PLANIFICACION_ROUTES,
          ),
      },
      {
        // Panel de alertas del entrenador (LAL-116) — no confundir con `/mi-plan` de más arriba, que
        // es la home del ALUMNO en la misma feature `seguimiento`, con shell y guard distintos.
        path: 'alertas',
        canActivate: [coachGuard],
        loadChildren: () =>
          import('./features/seguimiento/seguimiento.routes').then(
            (m) => m.SEGUIMIENTO_COACH_ROUTES,
          ),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
