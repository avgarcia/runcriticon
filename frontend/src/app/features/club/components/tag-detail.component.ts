import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmDropdownMenuImports } from '@spartan-ng/helm/dropdown-menu';
import { Observable, filter } from 'rxjs';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/confirm-dialog/confirm-dialog.component';
import { TagKey, TagValue, TaxonomyService } from '../../../core/taxonomy.service';
import { ToastService } from '../../../core/toast.service';
import { messageForError } from '../../../core/api/error-codes';
import { LabelDialogComponent, LabelDialogData } from './label-dialog.component';

/** Límites del contrato: 40 caracteres el nombre de un tag, 60 el de un valor. */
const MAX_TAG_LENGTH = 40;
const MAX_VALUE_LENGTH = 60;

/**
 * Detalle del tag seleccionado en el editor de taxonomía: su nombre, sus valores y las acciones
 * sobre ambos.
 *
 * Cada acción impacta al instante contra su endpoint y lo confirma con un toast. No hay borrador ni
 * botón de guardar como en la maqueta: la API son operaciones independientes y acumularlas en local
 * fingiría una transacción que el backend no ofrece, con un guardado a medias imposible de deshacer.
 */
@Component({
  selector: 'rc-tag-detail',
  standalone: true,
  imports: [HlmButton, HlmDropdownMenuImports],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col overflow-hidden rounded-xl border border-border bg-card">
      <header class="border-b border-border px-6 pb-4 pt-5">
        <p class="text-[11.5px] font-semibold uppercase tracking-[0.6px] text-muted-foreground" i18n>
          Editando tag
        </p>
        <div class="mt-1 flex items-center gap-2">
          <h2 class="text-xl font-semibold tracking-[-0.2px]">{{ tag().nombre }}</h2>
          <button
            hlmBtn
            variant="ghost"
            size="sm"
            (click)="renameTag()"
            aria-label="Renombrar tag"
            i18n-aria-label
            i18n
          >
            Renombrar
          </button>
        </div>
      </header>

      @if (archived()) {
        <!-- Sobre el tinte del aviso, el gris apagado no llega al contraste que exige AA. -->
        <p class="border-b border-border bg-muted px-6 py-3 text-sm text-foreground" role="alert" i18n>
          Este tag está archivado: no se le pueden añadir valores nuevos. Reactívalo para volver a
          usarlo.
        </p>
      }

      <section class="border-b border-border px-6 py-5">
        <h3
          class="mb-3 text-xs font-semibold uppercase tracking-[0.6px] text-muted-foreground"
          i18n
        >
          Valores ({{ tag().valores.length }})
        </h3>

        @if (tag().valores.length === 0) {
          <p class="text-sm text-muted-foreground" i18n>
            Este tag aún no tiene valores. Añade el primero.
          </p>
        } @else {
          <ul class="m-0 flex list-none flex-col gap-1.5 p-0">
            @for (value of tag().valores; track value.id) {
              <!-- El valor archivado se tacha y se etiqueta en vez de atenuarse, por el mismo
                   motivo de contraste que en la lista de tags. -->
              <li class="flex items-center gap-3 rounded-lg border border-border px-3 py-2.5">
                <span class="min-w-0 flex-1 text-sm" [class.line-through]="value.archivadoEn">
                  {{ value.valor }}
                </span>

                @if (race(value); as carrera) {
                  <span class="text-[12.5px] text-muted-foreground">{{ carrera.fecha }}</span>
                  <span class="rounded-md bg-muted px-2 py-1 text-[11.5px] font-semibold">
                    {{ carrera.distancia }}
                  </span>
                  @if (carrera.past) {
                    <span
                      class="rounded-md bg-muted-foreground px-2 py-0.5 text-[10.5px] font-semibold text-card"
                      i18n
                    >
                      pasada
                    </span>
                  }
                }

                @if (value.archivadoEn) {
                  <span class="text-[11.5px] text-muted-foreground" i18n>archivado</span>
                }

                <button
                  hlmBtn
                  variant="ghost"
                  size="sm"
                  [hlmDropdownMenuTrigger]="valueMenu"
                  [attr.aria-label]="optionsLabel(value)"
                >
                  &hellip;
                </button>
                <ng-template #valueMenu>
                  <div hlmDropdownMenu class="w-48">
                    <button hlmDropdownMenuItem (triggered)="renameValue(value)" i18n>
                      Renombrar
                    </button>
                    @if (value.archivadoEn) {
                      <button hlmDropdownMenuItem (triggered)="reactivateValue(value)" i18n>
                        Reactivar
                      </button>
                    } @else {
                      <button
                        hlmDropdownMenuItem
                        variant="destructive"
                        (triggered)="archiveValue(value)"
                        i18n
                      >
                        Archivar
                      </button>
                    }
                  </div>
                </ng-template>
              </li>
            }
          </ul>
        }

        <button
          hlmBtn
          variant="outline"
          class="mt-3 w-full"
          [disabled]="archived()"
          (click)="addValue()"
          i18n
        >
          + Añadir valor
        </button>
      </section>

      <footer class="flex justify-end gap-2.5 bg-muted px-6 py-4">
        @if (archived()) {
          <button hlmBtn (click)="reactivateTag()" i18n>Reactivar tag</button>
        } @else {
          <button hlmBtn variant="outline" class="text-danger" (click)="archiveTag()" i18n>
            Archivar tag
          </button>
        }
      </footer>
    </div>
  `,
})
export class TagDetailComponent {
  private readonly taxonomyService = inject(TaxonomyService);
  private readonly dialogService = inject(HlmDialogService);
  private readonly toastService = inject(ToastService);

  readonly tag = input.required<TagKey>();

  readonly archived = () => this.tag().archivadoEn != null;

  /**
   * Datos de carrera de un valor, ya formateados, o `null` si no los tiene. Hoy ningún valor nace
   * con ellos —el contrato no expone cómo asignarlos— pero sí puede traerlos, y la maqueta los pinta.
   */
  race(value: TagValue): { fecha: string; distancia: string; past: boolean } | null {
    if (value.metadata.tipo !== 'RACE') return null;
    // Se parte la fecha a mano en vez de `new Date(iso)`: el contrato manda un día suelto sin hora
    // ('2026-12-06') y el constructor lo interpretaría como medianoche UTC, corriendo un día en los
    // husos al oeste de Greenwich.
    const [year, month, day] = value.metadata.fecha.split('-').map(Number);
    const fecha = new Date(year, month - 1, day);
    const hoy = new Date();
    return {
      fecha: fecha.toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' }),
      distancia: value.metadata.distancia,
      past: fecha < new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate()),
    };
  }

  optionsLabel(value: TagValue): string {
    return $localize`Opciones de ${value.valor}:valor:`;
  }

  renameTag(): void {
    const tag = this.tag();
    this.openLabelDialog({
      title: $localize`Renombrar tag`,
      label: $localize`Nombre`,
      confirmLabel: $localize`Guardar`,
      initialValue: tag.nombre,
      maxLength: MAX_TAG_LENGTH,
      field: 'nombre',
      submit: (value) => this.taxonomyService.renameTag(tag.id, value),
    }).subscribe((nombre) => this.toastService.success($localize`Tag renombrado a ${nombre}:nombre:.`));
  }

  addValue(): void {
    const tag = this.tag();
    this.openLabelDialog({
      title: $localize`Añadir valor`,
      label: $localize`Valor`,
      confirmLabel: $localize`Añadir`,
      initialValue: '',
      maxLength: MAX_VALUE_LENGTH,
      field: 'valor',
      submit: (value) => this.taxonomyService.createValue(tag.id, value),
    }).subscribe((valor) => this.toastService.success($localize`Valor ${valor}:valor: añadido.`));
  }

  renameValue(value: TagValue): void {
    this.openLabelDialog({
      title: $localize`Renombrar valor`,
      label: $localize`Valor`,
      confirmLabel: $localize`Guardar`,
      initialValue: value.valor,
      maxLength: MAX_VALUE_LENGTH,
      field: 'valor',
      submit: (nuevo) => this.taxonomyService.renameValue(value.id, nuevo),
    }).subscribe((valor) => this.toastService.success($localize`Valor renombrado a ${valor}:valor:.`));
  }

  archiveTag(): void {
    const tag = this.tag();
    this.confirm({
      title: $localize`Archivar tag`,
      message: $localize`${tag.nombre}:nombre: dejará de poder asignarse, pero se conserva en los alumnos que ya lo tienen.`,
      confirmLabel: $localize`Archivar`,
    }).subscribe(() =>
      this.run(this.taxonomyService.archiveTag(tag.id), $localize`Tag ${tag.nombre}:nombre: archivado.`),
    );
  }

  /** Reactivar no pide confirmación: no destruye nada y se deshace archivando otra vez. */
  reactivateTag(): void {
    const tag = this.tag();
    this.run(
      this.taxonomyService.reactivateTag(tag.id),
      $localize`Tag ${tag.nombre}:nombre: reactivado.`,
    );
  }

  archiveValue(value: TagValue): void {
    this.confirm({
      title: $localize`Archivar valor`,
      message: $localize`${value.valor}:valor: dejará de ofrecerse, pero se conserva en los alumnos que ya lo tienen.`,
      confirmLabel: $localize`Archivar`,
    }).subscribe(() =>
      this.run(
        this.taxonomyService.archiveValue(value.id),
        $localize`Valor ${value.valor}:valor: archivado.`,
      ),
    );
  }

  reactivateValue(value: TagValue): void {
    this.run(
      this.taxonomyService.reactivateValue(value.id),
      $localize`Valor ${value.valor}:valor: reactivado.`,
    );
  }

  private openLabelDialog(data: LabelDialogData): Observable<string> {
    return this.dialogService
      .open<string>(LabelDialogComponent, { context: data })
      .closed$.pipe(filter((label): label is string => !!label));
  }

  private confirm(data: ConfirmDialogData): Observable<true> {
    return this.dialogService
      .open<boolean>(ConfirmDialogComponent, { context: data })
      .closed$.pipe(filter((confirmed): confirmed is true => confirmed === true));
  }

  /**
   * Las operaciones sin diálogo (archivar y reactivar) no tienen dónde pintar un error de campo, así
   * que avisan por toast. El 403 lo cubre ya el interceptor global.
   */
  private run(operation: Observable<unknown>, successMessage: string): void {
    operation.subscribe({
      next: () => this.toastService.success(successMessage),
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 403) return;
        this.toastService.error(messageForError(err));
      },
    });
  }
}
