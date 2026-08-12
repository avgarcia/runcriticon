import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HlmBadge } from '@spartan-ng/helm/badge';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmSkeleton } from '@spartan-ng/helm/skeleton';
import { CoachService, CoachWorkload } from '../../../core/coach.service';

/**
 * Entrenadores del club con su carga (LAL-89): quién lleva qué grupos y cuántos alumnos suman, para
 * repartir el trabajo y detectar a quién falta asignar.
 *
 * Vista distinta de `features/identidad/pages/coaches.component.ts` (gestión de sesión: revocar,
 * desactivar) — esta lee la proyección local de `club_taxonomia`, no `identidad`.
 *
 * `grupos` sale vacía y `totalAlumnos` a 0 para todos los entrenadores hasta que exista la asignación
 * entrenador↔grupo (LAL-93): todas las filas se pintan hoy con el distintivo "Sin grupos asignados",
 * que es el estado correcto, no un error.
 */
@Component({
  selector: 'rc-coaches-list',
  standalone: true,
  imports: [HlmBadge, HlmButton, HlmSkeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-5xl">
      <div class="mb-6">
        <h1 class="text-2xl font-semibold tracking-[-0.3px]" i18n>Carga de entrenadores</h1>
        <p class="mt-1 max-w-[560px] text-sm text-muted-foreground" i18n>
          Quién lleva qué grupos y cuántos alumnos suma, para repartir el trabajo.
        </p>
      </div>

      @if (coaches(); as loaded) {
        @if (loaded.length === 0) {
          <div class="rounded-xl border border-border bg-card p-8 text-center">
            <p class="text-muted-foreground" i18n>Este club todavía no tiene entrenadores.</p>
          </div>
        } @else {
          <ul class="m-0 flex list-none flex-col gap-3 p-0">
            @for (coach of loaded; track coach.id) {
              <li class="rounded-xl border border-border bg-card p-5">
                <div class="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <h2 class="text-base font-semibold">{{ coach.nombre }}</h2>
                    <p class="mt-1 text-sm text-muted-foreground">{{ coach.email }}</p>
                    <span class="mt-2 inline-flex items-center gap-1.5 text-sm">
                      <span
                        class="size-2 rounded-full"
                        [class.bg-success]="coach.estado === 'ACTIVO'"
                        [class.bg-muted-foreground]="coach.estado === 'INVITADO'"
                      ></span>
                      {{ statusLabel(coach.estado) }}
                    </span>
                  </div>
                  <div class="text-right">
                    @if (coach.grupos.length === 0) {
                      <span hlmBadge variant="outline" i18n>Sin grupos asignados</span>
                    } @else {
                      <p class="text-sm">{{ totalAlumnosLabel(coach.totalAlumnos) }}</p>
                      <div class="mt-1.5 flex flex-wrap justify-end gap-1.5">
                        @for (grupo of coach.grupos; track grupo.id) {
                          <span hlmBadge variant="outline">{{ grupo.nombre }}</span>
                        }
                      </div>
                    }
                  </div>
                </div>
              </li>
            }
          </ul>
        }
      } @else if (loadFailed()) {
        <div class="rounded-xl border border-border bg-card p-8 text-center">
          <p class="text-muted-foreground" role="alert" i18n>
            No se han podido cargar los entrenadores.
          </p>
          <button hlmBtn variant="outline" class="mt-4" (click)="reload()" i18n>Reintentar</button>
        </div>
      } @else {
        <div class="flex flex-col gap-3">
          <hlm-skeleton class="h-20 w-full" />
          <hlm-skeleton class="h-20 w-full" />
        </div>
      }
    </div>
  `,
})
export class CoachesListComponent implements OnInit {
  private readonly coachService = inject(CoachService);

  readonly loadFailed = signal(false);
  readonly coaches = this.coachService.coaches;

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loadFailed.set(false);
    this.coachService.load().subscribe({
      error: () => this.loadFailed.set(true),
    });
  }

  statusLabel(estado: CoachWorkload['estado']): string {
    return estado === 'ACTIVO' ? $localize`Activo` : $localize`Invitado`;
  }

  totalAlumnosLabel(total: number): string {
    if (total === 0) return $localize`Sin alumnos`;
    return total === 1 ? $localize`1 alumno` : $localize`${total}:total: alumnos`;
  }
}
