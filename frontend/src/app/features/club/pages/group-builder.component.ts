import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmSkeleton } from '@spartan-ng/helm/skeleton';
import { Subject, debounceTime, distinctUntilChanged, filter, of, switchMap, tap } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/confirm-dialog/confirm-dialog.component';
import {
  GroupCondition,
  GroupConditionRowComponent,
} from '../components/group-condition-row.component';
import { GroupPreviewComponent } from '../components/group-preview.component';
import { GroupMembers, GroupService } from '../../../core/group.service';
import { PermissionsService } from '../../../core/permissions.service';
import { TagKey, TaxonomyService } from '../../../core/taxonomy.service';
import { ToastService } from '../../../core/toast.service';
import { messageForError } from '../../../core/api/error-codes';

/** Límite del contrato para el nombre de un grupo. */
const MAX_GROUP_NAME_LENGTH = 80;

/** Margen entre la última edición del filtro y la consulta al servidor. */
const PREVIEW_DEBOUNCE_MS = 250;

/**
 * Constructor de grupos (maqueta `docs/diseno/constructor-grupos.html`): nombre, condiciones sobre
 * tags y vista previa en vivo de quién entra.
 *
 * De la maqueta se dejan fuera el entrenador asignado, los ajustes manuales de pertenencia, el aviso
 * de alumnos que ya están en otro grupo con plan activo y el aparcamiento de cambios sin guardar: hoy
 * nada de eso existe en el contrato.
 *
 * El nombre no se comprueba contra los que ya existen: dos grupos pueden llamarse igual, y es el
 * servidor quien lo dice.
 */
@Component({
  selector: 'rc-group-builder',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    HlmButton,
    HlmInput,
    HlmSkeleton,
    GroupConditionRowComponent,
    GroupPreviewComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-5xl">
      <a class="text-sm text-primary" routerLink="/club/grupos" i18n>← Volver a grupos</a>

      <div class="mb-6 mt-2 flex flex-wrap items-start justify-between gap-6">
        <div>
          <h1 class="text-2xl font-semibold tracking-[-0.3px]" i18n>Nuevo grupo</h1>
          <p class="mt-1 max-w-[560px] text-sm text-muted-foreground" i18n>
            Elige a quién quieres dentro y la vista previa se actualiza sola.
          </p>
        </div>
        @if (axes(); as loaded) {
          @if (loaded.length > 0) {
            <button hlmBtn [disabled]="!canSave() || saving()" (click)="save()" i18n>Guardar</button>
          }
        }
      </div>

      @if (axes(); as loaded) {
        @if (loaded.length === 0) {
          <div class="rounded-xl border border-border bg-card p-8 text-center">
            <p class="text-muted-foreground" i18n>
              No hay tags con valores que asignar, así que todavía no se puede construir un filtro.
            </p>
            @if (permissions.can('TAXONOMY', 'MANAGE')) {
              <a hlmBtn class="mt-4 inline-block" routerLink="/club/taxonomia" i18n>
                Ir a la taxonomía
              </a>
            } @else {
              <p class="mt-2 text-sm text-muted-foreground" i18n>
                Pídele al administrador del club que cree tags y valores.
              </p>
            }
          </div>
        } @else {
          <div class="grid gap-5 lg:grid-cols-[1fr_320px]">
            <div class="flex flex-col gap-5">
              <div class="rounded-xl border border-border bg-card p-5">
                <label class="text-sm font-semibold" for="group-name" i18n>Nombre del grupo</label>
                <input
                  hlmInput
                  id="group-name"
                  class="mt-2 w-full"
                  type="text"
                  [maxlength]="maxNameLength"
                  [ngModel]="name()"
                  (ngModelChange)="name.set($event)"
                  placeholder="Maratón Valencia avanzado"
                  i18n-placeholder
                />
              </div>

              <div class="rounded-xl border border-border bg-card p-5">
                <h2 class="text-base font-semibold" i18n>¿Quién entra en este grupo?</h2>
                <p class="mt-1 text-sm text-muted-foreground" i18n>
                  Los alumnos que cumplan <strong>todas</strong> estas condiciones.
                </p>

                <div class="mt-4 flex flex-col gap-3">
                  @for (condition of conditions(); track condition.tagId; let i = $index) {
                    <rc-group-condition-row
                      [condition]="condition"
                      [axes]="loaded"
                      [takenTagIds]="takenTagIds()"
                      (conditionChange)="updateCondition(i, $event)"
                      (remove)="removeCondition(i)"
                    />
                  }
                </div>

                @if (canAddCondition()) {
                  <button hlmBtn variant="outline" class="mt-4" (click)="addCondition()" i18n>
                    + Añadir condición
                  </button>
                }
              </div>
            </div>

            <rc-group-preview
              [members]="members()"
              [loading]="previewing()"
              [error]="previewError()"
              [filtered]="selectedValueIds().length > 0"
              (reload)="reloadTaxonomy()"
            />
          </div>
        }
      } @else if (loadFailed()) {
        <div class="rounded-xl border border-border bg-card p-8 text-center">
          <p class="text-muted-foreground" role="alert" i18n>No se ha podido cargar la taxonomía.</p>
          <button hlmBtn variant="outline" class="mt-4" (click)="reloadTaxonomy()" i18n>
            Reintentar
          </button>
        </div>
      } @else {
        <div class="grid gap-5 lg:grid-cols-[1fr_320px]">
          <hlm-skeleton class="h-64 w-full" />
          <hlm-skeleton class="h-64 w-full" />
        </div>
      }
    </div>
  `,
})
export class GroupBuilderComponent implements OnInit {
  private readonly groupService = inject(GroupService);
  private readonly taxonomyService = inject(TaxonomyService);
  private readonly dialogService = inject(HlmDialogService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);

  protected readonly permissions = inject(PermissionsService);

  readonly maxNameLength = MAX_GROUP_NAME_LENGTH;

  readonly name = signal('');
  readonly conditions = signal<readonly GroupCondition[]>([]);
  readonly members = signal<GroupMembers | undefined>(undefined);
  readonly previewing = signal(false);
  readonly previewError = signal<string | null>(null);
  readonly loadFailed = signal(false);
  readonly saving = signal(false);

  /** Ejes con al menos un valor asignable: un eje archivado, o sin valores vivos, no sirve de filtro. */
  readonly axes = computed<readonly TagKey[] | undefined>(() => {
    const taxonomy = this.taxonomyService.taxonomy();
    if (!taxonomy) return undefined;
    return taxonomy.tags
      .filter((tag) => !tag.archivadoEn)
      .map((tag) => ({ ...tag, valores: tag.valores.filter((value) => !value.archivadoEn) }))
      .filter((tag) => tag.valores.length > 0);
  });

  readonly takenTagIds = computed(() => this.conditions().map((condition) => condition.tagId));

  readonly selectedValueIds = computed(() =>
    this.conditions()
      .map((condition) => condition.valueId)
      .filter((valueId): valueId is string => valueId !== null),
  );

  readonly canAddCondition = computed(
    () => this.conditions().length < (this.axes()?.length ?? 0),
  );

  readonly canSave = computed(() => this.name().trim().length > 0);

  private readonly filterChanges = new Subject<readonly string[]>();

  constructor() {
    this.filterChanges
      .pipe(
        debounceTime(PREVIEW_DEBOUNCE_MS),
        distinctUntilChanged((a, b) => filterKey(a) === filterKey(b)),
        tap(() => {
          this.previewing.set(true);
          this.previewError.set(null);
        }),
        // El catchError va DENTRO de la proyección: si el error llega al Subject de fuera, la
        // suscripción se completa y la vista previa deja de responder el resto de la sesión, con
        // pinta de contador colgado.
        switchMap((valores) =>
          this.groupService.previewMembers(valores).pipe(
            catchError((err: unknown) => {
              this.handlePreviewError(err);
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((preview) => {
        this.previewing.set(false);
        if (preview) this.members.set(preview);
      });
  }

  ngOnInit(): void {
    this.reloadTaxonomy();
    // Se consulta también sin condiciones: quién entra en un filtro vacío lo decide el servidor.
    this.filterChanges.next([]);
  }

  reloadTaxonomy(): void {
    this.loadFailed.set(false);
    this.taxonomyService.load().subscribe({
      next: () => this.dropUnassignableConditions(),
      error: () => this.loadFailed.set(true),
    });
  }

  addCondition(): void {
    const used = this.takenTagIds();
    const next = this.axes()?.find((axis) => !used.includes(axis.id));
    if (!next) return;
    this.conditions.update((current) => [...current, { tagId: next.id, valueId: null }]);
  }

  updateCondition(index: number, condition: GroupCondition): void {
    this.conditions.update((current) => current.map((item, i) => (i === index ? condition : item)));
    this.filterChanges.next(this.selectedValueIds());
  }

  removeCondition(index: number): void {
    this.conditions.update((current) => current.filter((_, i) => i !== index));
    this.filterChanges.next(this.selectedValueIds());
  }

  save(): void {
    if (!this.canSave()) return;
    if ((this.members()?.total ?? 0) > 0) {
      this.create();
      return;
    }

    const data: ConfirmDialogData = {
      title: $localize`Un grupo sin alumnos`,
      message: $localize`Ningún alumno cumple este filtro ahora mismo. ¿Quieres crear el grupo igualmente?`,
      confirmLabel: $localize`Crear igualmente`,
    };
    this.dialogService
      .open<boolean>(ConfirmDialogComponent, { context: data })
      .closed$.pipe(filter((confirmed): confirmed is boolean => confirmed === true))
      .subscribe(() => this.create());
  }

  private create(): void {
    const nombre = this.name().trim();
    this.saving.set(true);
    this.groupService.create(nombre, this.selectedValueIds()).subscribe({
      next: () => {
        this.saving.set(false);
        this.toastService.success($localize`Grupo ${nombre}:nombre: creado.`);
        void this.router.navigate(['/club/grupos']);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        // El 403 ya lo avisa el interceptor global con su toast; no duplicamos el mensaje.
        if (err instanceof HttpErrorResponse && err.status === 403) return;
        this.toastService.error(messageForError(err));
      },
    });
  }

  private handlePreviewError(err: unknown): void {
    if (err instanceof HttpErrorResponse && err.status === 403) return;
    // Banner en el panel, no toast: en cada edición del filtro saldría uno nuevo.
    this.previewError.set(messageForError(err));
  }

  /** Tras recargar la taxonomía, las condiciones que apuntan a algo ya no asignable se caen solas. */
  private dropUnassignableConditions(): void {
    const axes = this.axes();
    if (!axes) return;
    const before = this.conditions();
    const after = before.filter((condition) => {
      const axis = axes.find((item) => item.id === condition.tagId);
      if (!axis) return false;
      return condition.valueId === null || axis.valores.some((value) => value.id === condition.valueId);
    });
    if (after.length === before.length) return;
    this.conditions.set(after);
    this.previewError.set(null);
    this.filterChanges.next(this.selectedValueIds());
  }
}

/** Dos filtros con los mismos valores son el mismo filtro, aunque se hayan compuesto en otro orden. */
function filterKey(valores: readonly string[]): string {
  return [...valores].sort().join('|');
}
