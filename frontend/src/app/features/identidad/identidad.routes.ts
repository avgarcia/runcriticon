import { Routes } from '@angular/router';
import { adminGuard } from '../../core/admin.guard';
import { authGuard } from '../../core/auth.guard';
import { staffGuard } from '../../core/staff.guard';

export const IDENTIDAD_ROUTES: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'activar',
    loadComponent: () => import('./pages/activate.component').then((m) => m.ActivateComponent),
  },
  {
    path: 'cambiar-contrasena',
    loadComponent: () =>
      import('./pages/force-password-change.component').then(
        (m) => m.ForcePasswordChangeComponent,
      ),
  },
  {
    path: 'entrar-con-enlace',
    loadComponent: () =>
      import('./pages/magic-link-request.component').then((m) => m.MagicLinkRequestComponent),
  },
  {
    path: 'entrar',
    loadComponent: () =>
      import('./pages/magic-link-consume.component').then((m) => m.MagicLinkConsumeComponent),
  },
  {
    path: 'restablecer',
    loadComponent: () =>
      import('./pages/password-reset-request.component').then(
        (m) => m.PasswordResetRequestComponent,
      ),
  },
  {
    path: 'restablecer/nueva',
    loadComponent: () =>
      import('./pages/password-reset-consume.component').then(
        (m) => m.PasswordResetConsumeComponent,
      ),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'coaches',
    canActivate: [authGuard, adminGuard],
    loadComponent: () => import('./pages/coaches.component').then((m) => m.CoachesComponent),
  },
  {
    path: 'alumnos',
    canActivate: [authGuard, staffGuard],
    loadComponent: () => import('./pages/alumnos.component').then((m) => m.AlumnosComponent),
  },
];
