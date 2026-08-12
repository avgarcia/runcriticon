import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogFooter, HlmDialogHeader, HlmDialogTitle } from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { forkJoin } from 'rxjs';
import { messageForError } from '../../../core/api/error-codes';
import { GroupDetail, GroupMemberOrigin, GroupService } from '../../../core/group.service';
import { StudentService, StudentSummary } from '../../../core/student.service';

/** Datos que necesita el diálogo: el grupo sobre el que se ajusta la pertenencia a mano. */
export interface GroupMembershipDialogData {
  readonly grupoId: string;
  readonly nombre: string;
}

/**
 * Ajuste manual de pertenencia a un grupo (LAL-92): incluir o excluir alumnos sin tocar sus tags.
 *
 * `ajusteManual` decide qué botón sale en cada miembro, **no** `origen`: un alumno puede cumplir el
 * filtro (`origen: FILTRO`) y tener además una inclusión manual guardada debajo (`ajusteManual:
 * true`), y en ese caso "Quitar excepción" tiene que seguir ofreciéndose — es justo la fila que hay
 * que poder limpiar. `origen` es solo el texto explicativo.
 *
 * "Quitar excepción" y "Restaurar" son la misma llamada (DELETE del override): en ambos casos la
 * pertenencia vuelve a decidirla el filtro, cambie el alumno de lado o no.
 *
 * Recorte respecto a la maqueta (`docs/diseno/constructor-grupos.html`): sin motivo de texto libre,
 * sin selector de entrenador, sin indicador de conflicto con otro grupo — el contrato de
 * `PUT .../overrides/{alumnoId}` solo acepta `{ incluido: boolean }`.
 */
@Component({
  selector: 'rc-group-membership-dialog',
  standalone: true,
  imports: [HlmButton, HlmDialogHeader, HlmDialogTitle, HlmDialogFooter, HlmInput, HlmSpinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div hlmDialogHeader>
      <h2 hlmDialogTitle>{{ data.nombre }}</h2>
    </div>

    <div class="flex min-w-96 max-w-lg flex-col gap-4 pt-2">
      @if (loading()) {
        <hlm-spinner aria-label="Cargando" i18n-aria-label />
      } @else if (loadFailed()) {
        <p class="text-sm text-danger" role="alert" i18n>No se ha podido cargar el grupo.</p>
      } @else if (detail(); as grupo) {
        <section>
          <h3 class="text-sm font-medium" i18n>Miembros ({{ grupo.miembros.length }})</h3>
          <ul class="mt-2 flex flex-col gap-1.5">
            @for (miembro of grupo.miembros; track miembro.id) {
              <li
                class="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2"
              >
                <div>
                  <p class="text-sm font-medium">{{ miembro.nombre }}</p>
                  <p class="text-xs text-muted-foreground">{{ origenLabel(miembro.origen) }}</p>
                </div>
                <div class="flex gap-2">
                  @if (miembro.ajusteManual) {
                    <button
                      hlmBtn
                      variant="ghost"
                      size="sm"
                      type="button"
                      [disabled]="saving()"
                      (click)="quitarExcepcion(miembro.id)"
                      i18n
                    >
                      Quitar excepción
                    </button>
                  }
                  <button
                    hlmBtn
                    variant="outline"
                    size="sm"
                    type="button"
                    [disabled]="saving()"
                    (click)="excluir(miembro.id)"
                    i18n
                  >
                    Excluir
                  </button>
                </div>
              </li>
            }
          </ul>
        </section>

        <section>
          <h3 class="text-sm font-medium" i18n>
            Excluidos manualmente ({{ grupo.excluidos.length }})
          </h3>
          @if (grupo.excluidos.length === 0) {
            <p class="mt-2 text-sm text-muted-foreground" i18n>Nadie excluido a mano.</p>
          } @else {
            <ul class="mt-2 flex flex-col gap-1.5">
              @for (excluido of grupo.excluidos; track excluido.id) {
                <li
                  class="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2"
                >
                  <p class="text-sm font-medium">{{ excluido.nombre }}</p>
                  <button
                    hlmBtn
                    variant="ghost"
                    size="sm"
                    type="button"
                    [disabled]="saving()"
                    (click)="restaurar(excluido.id)"
                    i18n
                  >
                    Restaurar
                  </button>
                </li>
              }
            </ul>
          }
        </section>

        <section>
          <h3 class="text-sm font-medium" i18n>Añadir alumno por excepción</h3>
          <input
            hlmInput
            class="mt-2 w-full"
            type="search"
            placeholder="Buscar alumno"
            i18n-placeholder
            [value]="search()"
            (input)="onSearchInput($event)"
          />
          @if (search().trim() && candidates().length === 0) {
            <p class="mt-2 text-sm text-muted-foreground" i18n>Nadie coincide con la búsqueda.</p>
          }
          @if (candidates().length > 0) {
            <ul class="mt-2 flex max-h-40 flex-col gap-1.5 overflow-y-auto">
              @for (alumno of candidates(); track alumno.id) {
                <li
                  class="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2"
                >
                  <p class="text-sm">{{ alumno.nombre }}</p>
                  <button
                    hlmBtn
                    variant="outline"
                    size="sm"
                    type="button"
                    [disabled]="saving()"
                    (click)="incluir(alumno.id)"
                    i18n
                  >
                    Incluir
                  </button>
                </li>
              }
            </ul>
          }
        </section>

        @if (errorMessage()) {
          <p class="text-sm text-danger" role="alert">{{ errorMessage() }}</p>
        }
      }
    </div>

    <div hlmDialogFooter>
      <button hlmBtn variant="outline" type="button" (click)="close()" i18n>Cerrar</button>
    </div>
  `,
})
export class GroupMembershipDialogComponent implements OnInit {
  private readonly groupService = inject(GroupService);
  private readonly studentService = inject(StudentService);
  private readonly dialogRef = inject(BrnDialogRef<boolean>);

  readonly data = injectBrnDialogContext<GroupMembershipDialogData>();

  readonly loading = signal(true);
  readonly loadFailed = signal(false);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly detail = signal<GroupDetail | null>(null);
  readonly search = signal('');

  /** Cambia si el grupo tiene overrides — se marca como avisado en `close`. */
  private changed = false;

  private readonly students = signal<readonly StudentSummary[] | undefined>(undefined);

  /** Alumnos que no son ya miembros y coinciden con la búsqueda. Vacío si no se ha escrito nada. */
  readonly candidates = computed<StudentSummary[]>(() => {
    const texto = this.search().trim().toLowerCase();
    const grupo = this.detail();
    const alumnos = this.students();
    if (!texto || !grupo || !alumnos) return [];
    const miembroIds = new Set(grupo.miembros.map((miembro) => miembro.id));
    return alumnos.filter(
      (alumno) => !miembroIds.has(alumno.id) && alumno.nombre.toLowerCase().includes(texto),
    );
  });

  ngOnInit(): void {
    this.load();
  }

  onSearchInput(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  origenLabel(origen: GroupMemberOrigin): string {
    return origen === 'FILTRO' ? $localize`Por filtro` : $localize`Por excepción`;
  }

  excluir(alumnoId: string): void {
    this.applyOverride(this.groupService.setOverride(this.data.grupoId, alumnoId, false));
  }

  incluir(alumnoId: string): void {
    this.applyOverride(this.groupService.setOverride(this.data.grupoId, alumnoId, true));
  }

  /** "Quitar excepción" en un miembro y "Restaurar" en un excluido son la misma llamada. */
  quitarExcepcion(alumnoId: string): void {
    this.clearOverride(alumnoId);
  }

  restaurar(alumnoId: string): void {
    this.clearOverride(alumnoId);
  }

  close(): void {
    this.dialogRef.close(this.changed);
  }

  private load(): void {
    this.loading.set(true);
    this.loadFailed.set(false);
    forkJoin([this.groupService.getDetail(this.data.grupoId), this.studentService.load()]).subscribe({
      next: ([grupo, alumnos]) => {
        this.detail.set(grupo);
        this.students.set(alumnos);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadFailed.set(true);
      },
    });
  }

  private applyOverride(request: ReturnType<GroupService['setOverride']>): void {
    this.saving.set(true);
    this.errorMessage.set(null);
    request.subscribe({
      next: (grupo) => {
        this.saving.set(false);
        this.changed = true;
        this.detail.set(grupo);
        this.search.set('');
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  private clearOverride(alumnoId: string): void {
    this.saving.set(true);
    this.errorMessage.set(null);
    this.groupService.clearOverride(this.data.grupoId, alumnoId).subscribe({
      next: () => {
        this.changed = true;
        // El DELETE no devuelve el detalle recalculado: hay que volver a pedirlo.
        this.groupService.getDetail(this.data.grupoId).subscribe({
          next: (grupo) => {
            this.saving.set(false);
            this.detail.set(grupo);
          },
          error: (err: unknown) => this.handleError(err),
        });
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  private handleError(err: unknown): void {
    this.saving.set(false);
    // El 403 ya lo avisa el interceptor global con su toast; no duplicamos el mensaje.
    if (err instanceof HttpErrorResponse && err.status === 403) return;
    this.errorMessage.set(messageForError(err));
  }
}
