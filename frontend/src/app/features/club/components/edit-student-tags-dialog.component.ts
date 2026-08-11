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

/** Datos que necesita el diálogo: el alumno y los ejes con los que se puede clasificar. */
export interface EditStudentTagsData {
  readonly studentId: string;
  readonly studentName: string;
  readonly currentValueIds: readonly string[];
  readonly axes: readonly TagKey[];
}

/**
 * Editar los tags de un alumno (LAL-87): un `hlm-select` por eje, mismo criterio de "un valor por eje"
 * que ya fijan el filtro de `students-list.component.ts` y `group-condition-row.component.ts` — el
 * alumno tiene como mucho un valor de cada eje a la vez.
 *
 * Guardar manda el conjunto **completo** a `reemplazarTagsDelAlumno` (`ReplaceStudentTagsCommand` en el
 * backend deja al alumno exactamente con lo indicado). Los valores que el alumno ya tenía y no
 * pertenecen a ningún eje visible aquí se preservan tal cual en el envío — no hay ningún control para
 * tocarlos, así que tocar otro eje no puede borrarlos por sorpresa.
 */
@Component({
  selector: 'rc-edit-student-tags-dialog',
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
      <h2 hlmDialogTitle>{{ data.studentName }}</h2>
    </div>

    <div class="flex min-w-80 flex-col gap-3 pt-2">
      @for (axis of data.axes; track axis.id) {
        <div class="flex flex-col gap-1.5">
          <label class="text-sm font-medium" [for]="axisTriggerId(axis.id)">{{ axis.nombre }}</label>
          <div class="flex flex-wrap items-center gap-2">
            <hlm-select
              class="min-w-[170px]"
              [value]="selected().get(axis.id) ?? ''"
              [itemToString]="axisValueToString(axis)"
              (valueChange)="selectAxisValue(axis.id, $event)"
            >
              <hlm-select-trigger [buttonId]="axisTriggerId(axis.id)">
                <hlm-select-value placeholder="Sin valor" i18n-placeholder />
              </hlm-select-trigger>
              <hlm-select-content [label]="axis.nombre">
                @for (value of axis.valores; track value.id) {
                  <hlm-select-item [value]="value.id">{{ valueOptionLabel(value) }}</hlm-select-item>
                }
              </hlm-select-content>
            </hlm-select>
            @if (selected().has(axis.id)) {
              <button
                type="button"
                class="text-xs text-muted-foreground underline"
                (click)="clearAxis(axis.id)"
                i18n
              >
                Quitar
              </button>
            }
          </div>
        </div>
      }
      @if (errorMessage()) {
        <p class="text-sm text-danger" role="alert">{{ errorMessage() }}</p>
      }
    </div>

    <div hlmDialogFooter>
      <button hlmBtn variant="outline" type="button" hlmDialogClose i18n>Cancelar</button>
      <button hlmBtn type="button" [disabled]="saving()" (click)="save()">
        @if (saving()) {
          <hlm-spinner aria-label="Guardando" i18n-aria-label />
        }
        <span i18n>Guardar</span>
      </button>
    </div>
  `,
})
export class EditStudentTagsDialogComponent {
  private readonly studentService = inject(StudentService);
  private readonly dialogRef = inject(BrnDialogRef<boolean>);

  readonly data = injectBrnDialogContext<EditStudentTagsData>();

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  /** Un valor seleccionado por eje: `tagId → valueId`. */
  readonly selected = signal<ReadonlyMap<string, string>>(this.initialSelection());

  /** Valores actuales del alumno que no pertenecen a ningún eje de `data.axes` — se preservan tal cual. */
  private readonly unmanaged = this.data.currentValueIds.filter(
    (valueId) => !this.data.axes.some((axis) => axis.valores.some((value) => value.id === valueId)),
  );

  selectAxisValue(tagId: string, valueId: string | null | undefined): void {
    if (!valueId) return;
    this.selected.update((current) => new Map(current).set(tagId, valueId));
  }

  clearAxis(tagId: string): void {
    this.selected.update((current) => {
      const next = new Map(current);
      next.delete(tagId);
      return next;
    });
  }

  save(): void {
    const tagValueIds = [...this.selected().values(), ...this.unmanaged];
    this.saving.set(true);
    this.errorMessage.set(null);
    this.studentService.replaceTags(this.data.studentId, tagValueIds).subscribe({
      next: () => this.dialogRef.close(true),
      error: (err: unknown) => {
        this.saving.set(false);
        // El 403 ya lo avisa el interceptor global con su toast; no duplicamos el mensaje.
        if (err instanceof HttpErrorResponse && err.status === 403) return;
        this.errorMessage.set(messageForError(err));
      },
    });
  }

  axisTriggerId(tagId: string): string {
    return `editar-tags-${tagId}`;
  }

  readonly axisValueToString =
    (axis: TagKey) =>
    (valueId: unknown): string =>
      axis.valores.find((value) => value.id === valueId)?.valor ?? '';

  valueOptionLabel(value: { valor: string; archivadoEn?: string | null }): string {
    return value.archivadoEn ? $localize`${value.valor}:valor: · archivado` : value.valor;
  }

  private initialSelection(): ReadonlyMap<string, string> {
    const map = new Map<string, string>();
    for (const axis of this.data.axes) {
      const current = axis.valores.find((value) => this.data.currentValueIds.includes(value.id));
      if (current) map.set(axis.id, current.id);
    }
    return map;
  }
}
