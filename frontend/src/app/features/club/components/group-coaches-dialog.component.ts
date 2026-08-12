import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogFooter, HlmDialogHeader, HlmDialogTitle } from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { forkJoin } from 'rxjs';
import { messageForError } from '../../../core/api/error-codes';
import { CoachService, CoachWorkload } from '../../../core/coach.service';
import { GroupCoaches, GroupService } from '../../../core/group.service';

/** Datos que necesita el diálogo: el grupo al que se asignan entrenadores. */
export interface GroupCoachesDialogData {
  readonly grupoId: string;
  readonly nombre: string;
}

/**
 * Asignar entrenadores a un grupo (LAL-93, recortado): vincular o desvincular, sin tocar la
 * autorización de publicación (AC2/AC3, pendiente de Planificación).
 *
 * Solo el ADMIN llega a abrir este diálogo — el botón que lo abre ya está gateado por
 * `GROUP:ASSIGN_COACH` en `groups-list.component.ts` — pero el 403 se maneja igual que el resto de
 * diálogos por si el rol cambia entre que se pinta el botón y se completa la acción.
 *
 * Reutiliza `CoachService.load()` (LAL-89) para el buscador: es la misma lista de entrenadores del
 * club que ya pinta la pantalla de carga, sin paginar — mismo criterio que
 * `group-membership-dialog.component.ts` reutiliza `StudentService.load()`.
 */
@Component({
  selector: 'rc-group-coaches-dialog',
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
      } @else if (coaches(); as entrenadores) {
        <section>
          <h3 class="text-sm font-medium" i18n>Entrenadores asignados ({{ entrenadores.length }})</h3>
          @if (entrenadores.length === 0) {
            <p class="mt-2 text-sm text-muted-foreground" i18n>Sin entrenadores asignados.</p>
          } @else {
            <ul class="mt-2 flex flex-col gap-1.5">
              @for (entrenador of entrenadores; track entrenador.id) {
                <li
                  class="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2"
                >
                  <p class="text-sm font-medium">{{ entrenador.nombre }}</p>
                  <button
                    hlmBtn
                    variant="ghost"
                    size="sm"
                    type="button"
                    [disabled]="saving()"
                    (click)="quitar(entrenador.id)"
                    i18n
                  >
                    Quitar
                  </button>
                </li>
              }
            </ul>
          }
        </section>

        <section>
          <h3 class="text-sm font-medium" i18n>Añadir entrenador</h3>
          <input
            hlmInput
            class="mt-2 w-full"
            type="search"
            placeholder="Buscar entrenador"
            i18n-placeholder
            [value]="search()"
            (input)="onSearchInput($event)"
          />
          @if (search().trim() && candidates().length === 0) {
            <p class="mt-2 text-sm text-muted-foreground" i18n>Nadie coincide con la búsqueda.</p>
          }
          @if (candidates().length > 0) {
            <ul class="mt-2 flex max-h-40 flex-col gap-1.5 overflow-y-auto">
              @for (candidato of candidates(); track candidato.id) {
                <li
                  class="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2"
                >
                  <p class="text-sm">{{ candidato.nombre }}</p>
                  <button
                    hlmBtn
                    variant="outline"
                    size="sm"
                    type="button"
                    [disabled]="saving()"
                    (click)="asignar(candidato.id)"
                    i18n
                  >
                    Asignar
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
export class GroupCoachesDialogComponent implements OnInit {
  private readonly groupService = inject(GroupService);
  private readonly coachService = inject(CoachService);
  private readonly dialogRef = inject(BrnDialogRef<boolean>);

  readonly data = injectBrnDialogContext<GroupCoachesDialogData>();

  readonly loading = signal(true);
  readonly loadFailed = signal(false);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly coaches = signal<GroupCoaches['entrenadores'] | null>(null);
  readonly search = signal('');

  /** Cambia si se asignó o quitó algún entrenador — se marca como avisado en `close`. */
  private changed = false;

  private readonly allCoaches = signal<readonly CoachWorkload[] | undefined>(undefined);

  /** Entrenadores del club que no están ya asignados y coinciden con la búsqueda. */
  readonly candidates = computed<CoachWorkload[]>(() => {
    const texto = this.search().trim().toLowerCase();
    const asignados = this.coaches();
    const todos = this.allCoaches();
    if (!texto || !asignados || !todos) return [];
    const asignadoIds = new Set(asignados.map((entrenador) => entrenador.id));
    return todos.filter(
      (entrenador) => !asignadoIds.has(entrenador.id) && entrenador.nombre.toLowerCase().includes(texto),
    );
  });

  ngOnInit(): void {
    this.load();
  }

  onSearchInput(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  asignar(entrenadorId: string): void {
    this.saving.set(true);
    this.errorMessage.set(null);
    this.groupService.assignCoach(this.data.grupoId, entrenadorId).subscribe({
      next: (respuesta) => {
        this.saving.set(false);
        this.changed = true;
        this.coaches.set(respuesta.entrenadores);
        this.search.set('');
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  quitar(entrenadorId: string): void {
    this.saving.set(true);
    this.errorMessage.set(null);
    this.groupService.unassignCoach(this.data.grupoId, entrenadorId).subscribe({
      next: () => {
        this.changed = true;
        // El DELETE no devuelve la lista recalculada: hay que volver a pedirla.
        this.groupService.getCoaches(this.data.grupoId).subscribe({
          next: (respuesta) => {
            this.saving.set(false);
            this.coaches.set(respuesta.entrenadores);
          },
          error: (err: unknown) => this.handleError(err),
        });
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  close(): void {
    this.dialogRef.close(this.changed);
  }

  private load(): void {
    this.loading.set(true);
    this.loadFailed.set(false);
    forkJoin([this.groupService.getCoaches(this.data.grupoId), this.coachService.load()]).subscribe({
      next: ([respuesta, todos]) => {
        this.coaches.set(respuesta.entrenadores);
        this.allCoaches.set(todos);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadFailed.set(true);
      },
    });
  }

  private handleError(err: unknown): void {
    this.saving.set(false);
    // El 403 ya lo avisa el interceptor global con su toast; no duplicamos el mensaje.
    if (err instanceof HttpErrorResponse && err.status === 403) return;
    this.errorMessage.set(messageForError(err));
  }
}
