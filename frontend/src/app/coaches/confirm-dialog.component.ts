import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import {
  HlmDialogClose,
  HlmDialogDescription,
  HlmDialogFooter,
  HlmDialogHeader,
  HlmDialogTitle,
} from '@spartan-ng/helm/dialog';

/** Datos del diálogo de confirmación genérico (acciones destructivas del admin, LAL-13). */
export interface ConfirmDialogData {
  readonly title: string;
  readonly message: string;
  readonly confirmLabel: string;
}

/**
 * Diálogo de confirmación reutilizable para acciones admin destructivas (revocar sesiones,
 * desactivar cuenta). Devuelve `true` al confirmar, `undefined` al cancelar.
 */
@Component({
  selector: 'rc-confirm-dialog',
  standalone: true,
  imports: [
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogDescription,
    HlmDialogFooter,
    HlmDialogClose,
    HlmButton,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div hlmDialogHeader>
      <h2 hlmDialogTitle>{{ data.title }}</h2>
      <p hlmDialogDescription>{{ data.message }}</p>
    </div>
    <div hlmDialogFooter>
      <button hlmBtn variant="outline" type="button" hlmDialogClose i18n>Cancelar</button>
      <button hlmBtn type="button" (click)="confirm()">{{ data.confirmLabel }}</button>
    </div>
  `,
})
export class ConfirmDialogComponent {
  private readonly dialogRef = inject(BrnDialogRef<boolean>);
  readonly data = injectBrnDialogContext<ConfirmDialogData>();

  confirm(): void {
    this.dialogRef.close(true);
  }
}
