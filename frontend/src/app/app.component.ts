import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Shell raíz de la aplicación (standalone, OnPush). En H0 solo aloja el router-outlet.
 * La navegación (toolbar, sidenav) se añade cuando existan las features (Bloque 1 funcional).
 */
@Component({
  selector: 'rc-root',
  standalone: true,
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<router-outlet />`,
})
export class AppComponent {}
