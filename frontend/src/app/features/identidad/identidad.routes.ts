import { Routes } from '@angular/router';

/**
 * Rutas de acceso (sin sesión): quedan fuera del shell autenticado a propósito, para que login,
 * activación, magic link y reseteo no muestren cabecera ni navegación.
 *
 * IMPORTANTE: ninguna de estas rutas puede tener `path: ''` ni comodín. `app.routes.ts` las
 * inserta con spread **antes** del shell, que es quien ocupa `path: ''`; una ruta vacía aquí
 * taparía el shell y dejaría la app sin cabecera. Las pantallas con sesión (home, coaches,
 * alumnos) viven en `app.routes.ts` como hijas del shell; sus componentes siguen en esta feature.
 */
export const IDENTIDAD_PUBLIC_ROUTES: Routes = [
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
      import('./pages/force-password-change.component').then((m) => m.ForcePasswordChangeComponent),
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
];
