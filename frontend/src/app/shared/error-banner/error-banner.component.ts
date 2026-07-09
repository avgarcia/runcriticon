import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Banner de error reutilizable para los formularios de identidad y acceso (ADR-0012 D1/D3). */
@Component({
  selector: 'rc-error-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p class="error-banner" role="alert"><ng-content /></p>`,
  styles: [
    `
      .error-banner {
        margin: 0 0 0.5rem;
        padding: 0.625rem 0.875rem;
        border-radius: 0.625rem;
        background: var(--mat-sys-error-container);
        color: var(--mat-sys-on-error-container);
        font-size: 0.9rem;
        line-height: 1.4;
      }
    `,
  ],
})
export class ErrorBannerComponent {}
