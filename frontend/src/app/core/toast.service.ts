import { Injectable } from '@angular/core';
import { toast } from 'ngx-sonner';

/**
 * Toasts globales (ADR-0012 D14, D20; spartan.ng sonner). Wrapper inyectable sobre la función
 * suelta `toast()` de ngx-sonner: mockeable por provider en tests, igual que el `MatSnackBar` que
 * sustituye.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  error(message: string): void {
    toast.error(message);
  }

  success(message: string): void {
    toast.success(message);
  }
}
