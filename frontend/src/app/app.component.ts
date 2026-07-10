import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HlmToaster } from '@spartan-ng/helm/sonner';

/**
 * Shell raíz de la aplicación (standalone, OnPush). En H0 solo aloja el router-outlet y el
 * contenedor de toasts globales (ADR-0012 D14, D20). La navegación (toolbar, sidenav) se añade
 * cuando existan las features (Bloque 1 funcional).
 */
@Component({
  selector: 'rc-root',
  standalone: true,
  imports: [RouterOutlet, HlmToaster],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <router-outlet />
    <hlm-toaster />
  `,
})
export class AppComponent {}
