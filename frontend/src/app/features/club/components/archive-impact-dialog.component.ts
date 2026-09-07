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
import { TagArchiveImpact } from '../../../core/taxonomy.service';

/** Datos del diálogo de archivado de un eje o un valor, con el impacto (LAL-83) ya resuelto. */
export interface ArchiveImpactDialogData {
  readonly title: string;
  readonly message: string;
  readonly confirmLabel: string;
  readonly impact: TagArchiveImpact;
}

/**
 * Diálogo de archivado de la taxonomía que reemplaza al confirm-dialog genérico para este caso: la
 * confirmación por sí sola no basta cuando un grupo vivo depende de lo que se va a archivar.
 *
 * Si `impact.gruposQueLoRequieren` no está vacío, ADR-0002 D10 bloquea el archivado (el backend lo
 * rechaza con 409 igualmente) — se lista qué grupos hay que editar primero y no se ofrece
 * confirmar, solo cerrar. Si está vacío, se comporta como el confirm-dialog genérico: el número de
 * alumnos afectados es solo informativo, porque archivar no les quita el tag (ADR-0002 D10).
 *
 * Devuelve `true` al confirmar, `undefined` al cancelar o cerrar — mismo contrato que
 * `ConfirmDialogComponent`.
 */
@Component({
  selector: 'rc-archive-impact-dialog',
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

    @if (blocked()) {
      <div class="flex flex-col gap-2 pt-2" role="alert">
        <p class="text-sm font-medium text-danger" i18n>
          No se puede archivar: el filtro de estos grupos lo requiere. Edita su filtro primero.
        </p>
        <ul class="m-0 flex list-none flex-col gap-1 p-0 text-sm">
          @for (grupo of data.impact.gruposQueLoRequieren; track grupo.id) {
            <li class="rounded-md border border-border px-3 py-2">
              {{ grupo.nombre }}
              @if (grupo.perderiaTodosLosTagsRequeridos) {
                <span class="block text-xs text-muted-foreground" i18n>
                  Se quedaría sin ningún tag requerido activo.
                </span>
              }
            </li>
          }
        </ul>
      </div>
      <div hlmDialogFooter>
        <button hlmBtn variant="outline" type="button" hlmDialogClose i18n>Cerrar</button>
      </div>
    } @else {
      @if (data.impact.alumnosAfectados > 0) {
        <p class="pt-2 text-sm text-muted-foreground" role="status">{{ studentsWarning() }}</p>
      }
      <div hlmDialogFooter>
        <button hlmBtn variant="outline" type="button" hlmDialogClose i18n>Cancelar</button>
        <button hlmBtn type="button" (click)="confirm()">{{ data.confirmLabel }}</button>
      </div>
    }
  `,
})
export class ArchiveImpactDialogComponent {
  private readonly dialogRef = inject(BrnDialogRef<boolean>);
  readonly data = injectBrnDialogContext<ArchiveImpactDialogData>();

  readonly blocked = (): boolean => this.data.impact.gruposQueLoRequieren.length > 0;

  studentsWarning(): string {
    const alumnos = this.data.impact.alumnosAfectados;
    return alumnos === 1
      ? $localize`1 alumno lo tiene asignado y lo conservará.`
      : $localize`${alumnos}:alumnos: alumnos lo tienen asignado y lo conservarán.`;
  }

  confirm(): void {
    this.dialogRef.close(true);
  }
}
