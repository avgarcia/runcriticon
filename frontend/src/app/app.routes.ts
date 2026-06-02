import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

/**
 * Rutas raíz (ADR-0012 D10, lazy loading por feature). En H0: login público y la pantalla
 * post-login protegida por sesión (ADR-0003).
 */
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./home/home.component').then((m) => m.HomeComponent),
  },
  { path: '**', redirectTo: '' },
];
