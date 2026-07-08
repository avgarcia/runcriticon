import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';

const SNACKBAR_DURATION_MS = 4000;

/**
 * Interceptor global de errores (ADR-0012 D14). Solo actúa en los status que la tabla del ADR le
 * asigna a él — 403/429/5xx/red — con un toast neutro. El resto (400/404/409) los deja pasar sin
 * tocar: los maneja el caller (D19, o su propia lógica de UI). Siempre re-lanza el error para que
 * el caller pueda parar su spinner o añadir su propio manejo.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        const toast = toastFor(err.status);
        if (toast) {
          snackBar.open(toast, 'Cerrar', { duration: SNACKBAR_DURATION_MS });
        }
      }
      return throwError(() => err);
    }),
  );
};

function toastFor(status: number): string | null {
  if (status === 403) return 'No tienes permiso para esta acción.';
  if (status === 429) return 'Demasiados intentos. Espera unos segundos.';
  if (status === 0) return 'Sin conexión.';
  if (status >= 500) return 'Algo ha ido mal. Vuelve a intentarlo.';
  return null;
}
