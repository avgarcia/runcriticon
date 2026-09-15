import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import {
  HlmDialogClose,
  HlmDialogFooter,
  HlmDialogHeader,
  HlmDialogTitle,
} from '@spartan-ng/helm/dialog';
import { HlmSelectImports } from '@spartan-ng/helm/select';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { messageForError } from '../../../core/api/error-codes';
import { StudentService } from '../../../core/student.service';
import { TagKey } from '../../../core/taxonomy.service';

/** Las dos acciones de la bulk-bar de la maqueta que tienen dominio propio; "cambiar estado" y "eliminar" no. */
export type BulkTagMode = 'assign' | 'unassign';

/** Datos que necesita el diálogo: el modo, los alumnos seleccionados y los ejes disponibles para elegir un valor. */
export interface BulkTagDialogData {
  readonly mode: BulkTagMode;
  readonly studentIds: readonly string[];
  readonly axes: readonly TagKey[];
}

/**
 * Asignar o quitar un valor de la taxonomía a todos los alumnos seleccionados en el listado, de una vez
 * (bulk-bar de la maqueta `docs/diseno/alta-alumnos.html`).
 *
 * A diferencia de `edit-student-tags-dialog.component.ts` (un `hlm-select` por eje, "un valor por eje"),
 * aquí solo hace falta **un** eje y **un** valor — la operación en masa toca una sola etiqueta a la vez,
 * como fija la spec 03 ("Modal: selecciona tag + valor").
 *
 * En modo `unassign` los valores archivados se ofrecen sin marca especial: quitar nunca valida la
 * taxonomía (paridad con `desasignarTagDelAlumno` de un solo alumno). En modo `assign` se reutiliza el
 * mismo sufijo "· archivado" que ya usa el diálogo de edición individual.
 */
@Component({
  selector: 'rc-bulk-tag-dialog',
  standalone: true,
  imports: [
    HlmButton,
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogFooter,
    HlmDialogClose,
    HlmSpinner,
    ...HlmSelectImports,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div hlmDialogHeader>
      <h2 hlmDialogTitle>{{ title() }}</h2>
    </div>

    <div class="flex min-w-80 flex-col gap-3 pt-2">
      <div class="flex flex-col gap-1.5">
        <label class="text-sm font-medium" for="bulk-tag-eje" i18n>Eje</label>
        <hlm-select
          [value]="selectedAxisId() ?? ''"
          [itemToString]="axisToString"
          (valueChange)="selectAxis($event)"
        >
          <hlm-select-trigger [buttonId]="'bulk-tag-eje'">
            <hlm-select-value placeholder="Selecciona un eje" i18n-placeholder />
          </hlm-select-trigger>
          <hlm-select-content label="Eje" i18n-label>
            @for (axis of data.axes; track axis.id) {
              <hlm-select-item [value]="axis.id">{{ axis.nombre }}</hlm-select-item>
            }
          </hlm-select-content>
        </hlm-select>
      </div>

      <div class="flex flex-col gap-1.5">
        <label class="text-sm font-medium" for="bulk-tag-valor" i18n>Valor</label>
        <hlm-select
          [value]="selectedValueId() ?? ''"
          [disabled]="!selectedAxis()"
          [itemToString]="valueToString"
          (valueChange)="selectValue($event)"
        >
          <hlm-select-trigger [buttonId]="'bulk-tag-valor'">
            <hlm-select-value placeholder="Selecciona un valor" i18n-placeholder />
          </hlm-select-trigger>
          <hlm-select-content label="Valor" i18n-label>
            @for (value of selectedAxis()?.valores ?? []; track value.id) {
              <hlm-select-item [value]="value.id">{{ valueOptionLabel(value) }}</hlm-select-item>
            }
          </hlm-select-content>
        </hlm-select>
      </div>

      @if (errorMessage()) {
        <p class="text-sm text-danger" role="alert">{{ errorMessage() }}</p>
      }
    </div>

    <div hlmDialogFooter>
      <button hlmBtn variant="outline" type="button" hlmDialogClose i18n>Cancelar</button>
      <button hlmBtn type="button" [disabled]="!selectedValueId() || saving()" (click)="confirm()">
        @if (saving()) {
          <hlm-spinner aria-label="Guardando" i18n-aria-label />
        }
        <span>{{ confirmLabel() }}</span>
      </button>
    </div>
  `,
})
export class BulkTagDialogComponent {
  private readonly studentService = inject(StudentService);
  private readonly dialogRef = inject(BrnDialogRef<number>);

  readonly data = injectBrnDialogContext<BulkTagDialogData>();

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedAxisId = signal<string | undefined>(undefined);
  readonly selectedValueId = signal<string | undefined>(undefined);

  selectedAxis(): TagKey | undefined {
    const axisId = this.selectedAxisId();
    return axisId ? this.data.axes.find((axis) => axis.id === axisId) : undefined;
  }

  selectAxis(axisId: string | null | undefined): void {
    if (!axisId) return;
    this.selectedAxisId.set(axisId);
    // Cambiar de eje invalida el valor elegido: pertenecía al eje anterior.
    this.selectedValueId.set(undefined);
  }

  selectValue(valueId: string | null | undefined): void {
    if (!valueId) return;
    this.selectedValueId.set(valueId);
  }

  confirm(): void {
    const valorId = this.selectedValueId();
    if (!valorId) return;
    this.saving.set(true);
    this.errorMessage.set(null);
    const operation =
      this.data.mode === 'assign'
        ? this.studentService.assignTagInBulk(this.data.studentIds, valorId)
        : this.studentService.unassignTagInBulk(this.data.studentIds, valorId);
    operation.subscribe({
      next: (actualizados) => this.dialogRef.close(actualizados),
      error: (err: unknown) => {
        this.saving.set(false);
        // El 403 ya lo avisa el interceptor global con su toast; no duplicamos el mensaje.
        if (err instanceof HttpErrorResponse && err.status === 403) return;
        this.errorMessage.set(messageForError(err));
      },
    });
  }

  title(): string {
    const total = this.data.studentIds.length;
    return this.data.mode === 'assign'
      ? total === 1
        ? $localize`Asignar tag a 1 alumno`
        : $localize`Asignar tag a ${total}:total: alumnos`
      : total === 1
        ? $localize`Quitar tag de 1 alumno`
        : $localize`Quitar tag de ${total}:total: alumnos`;
  }

  confirmLabel(): string {
    return this.data.mode === 'assign' ? $localize`Asignar` : $localize`Quitar`;
  }

  readonly axisToString = (axisId: unknown): string =>
    this.data.axes.find((axis) => axis.id === axisId)?.nombre ?? '';

  readonly valueToString = (valueId: unknown): string =>
    this.selectedAxis()?.valores.find((value) => value.id === valueId)?.valor ?? '';

  /** Sufijo "· archivado" solo en modo `assign`: en `unassign` el archivado no se distingue, se quita igual. */
  valueOptionLabel(value: { valor: string; archivadoEn?: string | null }): string {
    if (this.data.mode === 'unassign' || !value.archivadoEn) return value.valor;
    return $localize`${value.valor}:valor: · archivado`;
  }
}
