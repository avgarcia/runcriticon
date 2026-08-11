import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HlmBadge } from '@spartan-ng/helm/badge';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmSelectImports } from '@spartan-ng/helm/select';
import { HlmSkeleton } from '@spartan-ng/helm/skeleton';
import { Subject, debounceTime, distinctUntilChanged, filter, forkJoin, of, switchMap } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { messageForError } from '../../../core/api/error-codes';
import { PermissionsService } from '../../../core/permissions.service';
import { StudentService, StudentSummary } from '../../../core/student.service';
import { Taxonomy, TagKey, TaxonomyService } from '../../../core/taxonomy.service';
import { ToastService } from '../../../core/toast.service';
import { EditStudentTagsDialogComponent } from '../components/edit-student-tags-dialog.component';
import { InviteAlumnoDialogComponent } from '../../identidad/components/invite-alumno-dialog.component';

/** Margen entre la última edición del filtro y la consulta al servidor. */
const FILTER_DEBOUNCE_MS = 250;

/** Un valor de taxonomía ya traducido a lo que se pinta. */
interface TagLabel {
  readonly id: string;
  readonly label: string;
  readonly archivado: boolean;
}

/** Un alumno con sus tags ya traducidos. */
interface StudentRow {
  readonly summary: StudentSummary;
  readonly tags: readonly TagLabel[];
}

/** Un filtro activo tal y como se pinta en el chip. */
interface ActiveFilterChip {
  readonly tagId: string;
  readonly label: string;
}

/**
 * Alumnos del club (maqueta `docs/diseno/alta-alumnos.html`): tabla con sus tags y filtros por eje de
 * taxonomía.
 *
 * De la maqueta se dejan fuera las acciones bulk, la edición o eliminación por fila, la importación
 * CSV y el estado de "lesión" — el contrato solo distingue `INVITADO`/`ACTIVO` (LAL-87 y LAL-88 las
 * traen).
 *
 * **Un solo valor por eje en el filtro**, mismo criterio que ya fija `group-condition-row.component.ts`
 * para el constructor de grupos: el filtro solo sabe hacer «y», así que pedir dos valores del mismo eje
 * a la vez sería un filtro que nadie cumple.
 */
@Component({
  selector: 'rc-students-list',
  standalone: true,
  imports: [HlmBadge, HlmButton, HlmSkeleton, ...HlmSelectImports],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-6xl">
      <div class="mb-6 flex flex-wrap items-start justify-between gap-6">
        <div>
          <h1 class="text-2xl font-semibold tracking-[-0.3px]" i18n>Alumnos</h1>
          <p class="mt-1 max-w-[560px] text-sm text-muted-foreground" i18n>
            Los alumnos del club con sus tags. Filtra por lo que necesites revisar.
          </p>
        </div>
        @if (permissions.can('STUDENT', 'INVITE')) {
          <button hlmBtn (click)="openInviteDialog()" i18n>+ Dar de alta alumno</button>
        }
      </div>

      @if (rows(); as loaded) {
        @if (axes(); as loadedAxes) {
          @if (loadedAxes.length > 0) {
            <div
              class="mb-3 flex flex-wrap items-center gap-2.5"
              role="group"
              aria-label="Filtrar por tags"
              i18n-aria-label
            >
              @for (axis of loadedAxes; track axis.id) {
                <label class="sr-only" [for]="axisTriggerId(axis.id)">{{ axis.nombre }}</label>
                <hlm-select
                  class="min-w-[150px]"
                  [value]="selected().get(axis.id) ?? ''"
                  [itemToString]="axisValueToString(axis)"
                  (valueChange)="selectAxisValue(axis.id, $event)"
                >
                  <hlm-select-trigger [buttonId]="axisTriggerId(axis.id)">
                    <hlm-select-value [placeholder]="axis.nombre" />
                  </hlm-select-trigger>
                  <hlm-select-content [label]="axis.nombre">
                    @for (value of axis.valores; track value.id) {
                      <hlm-select-item [value]="value.id">{{ value.valor }}</hlm-select-item>
                    }
                  </hlm-select-content>
                </hlm-select>
              }
            </div>

            @if (activeChips().length > 0) {
              <div
                class="mb-4 flex flex-wrap items-center gap-2"
                role="group"
                aria-label="Filtros activos"
                i18n-aria-label
              >
                @for (chip of activeChips(); track chip.tagId) {
                  <span hlmBadge>
                    {{ chip.label }}
                    <button
                      type="button"
                      class="ml-1 cursor-pointer"
                      (click)="clearAxis(chip.tagId)"
                      [attr.aria-label]="removeFilterLabel(chip.label)"
                    >
                      ×
                    </button>
                  </span>
                }
                <button hlmBtn variant="ghost" size="sm" (click)="clearAllFilters()" i18n>
                  Limpiar filtros
                </button>
              </div>
            }
          }
        }

        <p class="mb-3 text-sm text-muted-foreground">{{ countLabel(loaded.length) }}</p>

        @if (loaded.length === 0) {
          <div class="rounded-xl border border-border bg-card p-8 text-center">
            @if (activeChips().length > 0) {
              <p class="text-muted-foreground" i18n>Ningún alumno cumple estos filtros.</p>
              <button hlmBtn variant="outline" class="mt-4" (click)="clearAllFilters()" i18n>
                Limpiar filtros
              </button>
            } @else {
              <p class="text-muted-foreground" i18n>Aún no tienes alumnos. Da de alta el primero.</p>
              @if (permissions.can('STUDENT', 'INVITE')) {
                <button hlmBtn class="mt-4" (click)="openInviteDialog()" i18n>+ Dar de alta alumno</button>
              }
            }
          </div>
        } @else {
          <table class="w-full border-collapse text-sm">
            <thead>
              <tr class="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
                <th class="py-2 pr-4" i18n>Nombre</th>
                <th class="py-2 pr-4" i18n>Email</th>
                <th class="py-2 pr-4" i18n>Estado</th>
                <th class="py-2 pr-4" i18n>Tags</th>
                @if (permissions.can('STUDENT', 'CLASSIFY')) {
                  <th class="py-2"><span class="sr-only" i18n>Acciones</span></th>
                }
              </tr>
            </thead>
            <tbody>
              @for (row of loaded; track row.summary.id) {
                <tr class="border-b border-border last:border-0">
                  <td class="py-2.5 pr-4 font-medium">{{ row.summary.nombre }}</td>
                  <td class="py-2.5 pr-4 text-muted-foreground">{{ row.summary.email }}</td>
                  <td class="py-2.5 pr-4">
                    <span class="inline-flex items-center gap-1.5">
                      <span
                        class="size-2 rounded-full"
                        [class.bg-success]="row.summary.estado === 'ACTIVO'"
                        [class.bg-muted-foreground]="row.summary.estado === 'INVITADO'"
                      ></span>
                      {{ statusLabel(row.summary.estado) }}
                    </span>
                  </td>
                  <td class="py-2.5 pr-4">
                    <div class="flex flex-wrap gap-1.5">
                      @for (tag of row.tags; track tag.id) {
                        <span hlmBadge variant="outline">{{ tag.label }}</span>
                      }
                    </div>
                  </td>
                  @if (permissions.can('STUDENT', 'CLASSIFY')) {
                    <td class="py-2.5 text-right">
                      <button hlmBtn variant="ghost" size="sm" (click)="openEditTagsDialog(row)" i18n>
                        Editar tags
                      </button>
                    </td>
                  }
                </tr>
              }
            </tbody>
          </table>
        }
      } @else if (loadFailed()) {
        <div class="rounded-xl border border-border bg-card p-8 text-center">
          <p class="text-muted-foreground" role="alert" i18n>No se han podido cargar los alumnos.</p>
          <button hlmBtn variant="outline" class="mt-4" (click)="reload()" i18n>Reintentar</button>
        </div>
      } @else {
        <div class="flex flex-col gap-3">
          <hlm-skeleton class="h-10 w-full" />
          <hlm-skeleton class="h-64 w-full" />
        </div>
      }
    </div>
  `,
})
export class StudentsListComponent implements OnInit {
  private readonly studentService = inject(StudentService);
  private readonly taxonomyService = inject(TaxonomyService);
  private readonly dialogService = inject(HlmDialogService);
  private readonly toastService = inject(ToastService);

  protected readonly permissions = inject(PermissionsService);

  readonly loadFailed = signal(false);

  /** Un valor seleccionado por eje: `tagId → valueId`. Cambiar de valor en un eje sustituye al anterior. */
  readonly selected = signal<ReadonlyMap<string, string>>(new Map());

  readonly axes = computed<readonly TagKey[] | undefined>(() =>
    this.taxonomyService.taxonomy()?.tags.filter((tag) => tag.valores.length > 0),
  );

  readonly selectedValueIds = computed(() => [...this.selected().values()]);

  readonly activeChips = computed<readonly ActiveFilterChip[]>(() => {
    const taxonomy = this.taxonomyService.taxonomy();
    if (!taxonomy) return [];
    return [...this.selected().entries()].map(([tagId, valueId]) => {
      const found = this.describeValue(valueId, taxonomy);
      return { tagId, label: found.label };
    });
  });

  readonly rows = computed<StudentRow[] | undefined>(() => {
    const students = this.studentService.students();
    const taxonomy = this.taxonomyService.taxonomy();
    if (!students || !taxonomy) return undefined;
    return students.map((summary) => ({
      summary,
      tags: summary.valores.map((valorId) => this.describeValue(valorId, taxonomy)),
    }));
  });

  private readonly filterChanges = new Subject<readonly string[]>();

  constructor() {
    this.filterChanges
      .pipe(
        debounceTime(FILTER_DEBOUNCE_MS),
        distinctUntilChanged((a, b) => filterKey(a) === filterKey(b)),
        switchMap((valores) =>
          this.studentService.load(valores).pipe(
            catchError((err: unknown) => {
              this.handleFilterError(err);
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loadFailed.set(false);
    forkJoin([this.studentService.load(this.selectedValueIds()), this.taxonomyService.load()]).subscribe({
      error: () => this.loadFailed.set(true),
    });
  }

  selectAxisValue(tagId: string, valueId: string | null | undefined): void {
    if (!valueId) return;
    this.selected.update((current) => new Map(current).set(tagId, valueId));
    this.filterChanges.next(this.selectedValueIds());
  }

  clearAxis(tagId: string): void {
    this.selected.update((current) => {
      const next = new Map(current);
      next.delete(tagId);
      return next;
    });
    this.filterChanges.next(this.selectedValueIds());
  }

  clearAllFilters(): void {
    this.selected.set(new Map());
    this.filterChanges.next([]);
  }

  /** No hace falta `reload()` al cerrar: `replaceTags` ya parchea la fila en `StudentService`. */
  openEditTagsDialog(row: StudentRow): void {
    const axes = this.axes();
    if (!axes) return;
    this.dialogService.open<boolean>(EditStudentTagsDialogComponent, {
      context: {
        studentId: row.summary.id,
        studentName: row.summary.nombre,
        currentValueIds: row.summary.valores,
        axes,
      },
    });
  }

  openInviteDialog(): void {
    this.dialogService
      .open<string>(InviteAlumnoDialogComponent)
      .closed$.pipe(filter(Boolean))
      .subscribe((email) => {
        this.toastService.success($localize`Invitación enviada a ${email}:email:`);
        this.reload();
      });
  }

  countLabel(total: number): string {
    if (total === 0) return $localize`Sin alumnos`;
    return total === 1 ? $localize`1 alumno` : $localize`${total}:total: alumnos`;
  }

  statusLabel(estado: StudentSummary['estado']): string {
    return estado === 'ACTIVO' ? $localize`Activo` : $localize`Invitado`;
  }

  axisTriggerId(tagId: string): string {
    return `filtro-alumnos-${tagId}`;
  }

  readonly axisValueToString =
    (axis: TagKey) =>
    (valueId: unknown): string =>
      axis.valores.find((value) => value.id === valueId)?.valor ?? '';

  removeFilterLabel(label: string): string {
    return $localize`Quitar filtro ${label}:filtro:`;
  }

  private handleFilterError(err: unknown): void {
    if (err instanceof HttpErrorResponse && err.status === 403) return;
    this.toastService.error(messageForError(err));
  }

  /** Busca un valor en todos los ejes: su id es único en todo el club, no solo dentro de su eje. */
  private describeValue(valorId: string, taxonomy: Taxonomy): TagLabel {
    for (const tag of taxonomy.tags) {
      const valor = tag.valores.find((item) => item.id === valorId);
      if (valor) {
        return {
          id: valorId,
          label: `${tag.nombre}: ${valor.valor}`,
          archivado: Boolean(valor.archivadoEn || tag.archivadoEn),
        };
      }
    }
    return { id: valorId, label: $localize`un valor que ya no existe`, archivado: false };
  }
}

/** Dos filtros con los mismos valores son el mismo filtro, aunque se hayan compuesto en otro orden. */
function filterKey(valores: readonly string[]): string {
  return [...valores].sort().join('|');
}
