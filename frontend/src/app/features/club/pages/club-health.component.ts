import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmSkeleton } from '@spartan-ng/helm/skeleton';
import { forkJoin, tap } from 'rxjs';
import { ClubHealthService, GroupActivity } from '../../../core/club-health.service';
import { GroupService } from '../../../core/group.service';

/** `"2026-09-10T09:00:00Z"` → `"hace 2 h"`. Sin depender de `DatePipe`/locale (ADR-0012 D9: sin
 * `registerLocaleData` en el proyecto), igual que `date-format-es.ts` de seguimiento — pero esta
 * pantalla no importa de esa feature (frontera entre features), así que se formatea aquí mismo. */
function formatRelativeShortEs(iso: string): string {
  const elapsedMs = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(elapsedMs / 60_000);
  if (minutes < 1) return 'hace un momento';
  if (minutes < 60) return `hace ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `hace ${hours} h`;
  const days = Math.floor(hours / 24);
  return `hace ${days} d`;
}

/** Un grupo con su entrenador y su última actividad ya unidos, para pintar una fila de la tabla. */
interface HealthRow {
  readonly id: string;
  readonly nombre: string;
  readonly totalAlumnos: number;
  readonly tieneEntrenador: boolean;
  readonly ultimaActividadEn: string | undefined;
}

/**
 * Vista de salud del club (admin, M16; maqueta `docs/diseno/salud-del-club.html`): por
 * grupo, sus alumnos, si tiene entrenador asignado y su última actividad reportada.
 *
 * Compone dos respuestas de dos módulos distintos por `grupoId`, en vez de que el backend las una:
 * `clubtaxonomia` (`GET /grupos`) no puede consumir eventos de `seguimiento` sin crear un ciclo entre
 * módulos (`seguimiento` ya depende de `club_taxonomia`), así que el cruce vive aquí. `/grupos` es la
 * lista maestra — un `grupoId` que solo aparezca en la actividad (carrera con un grupo borrado) no se
 * pinta, sin caso especial. Un grupo ausente del mapa de actividad no es un error: es "sin actividad",
 * el estado normal de un grupo en el que nadie ha reportado nunca.
 *
 * `forkJoin` falla entero si falla cualquiera de las dos llamadas a propósito: un fallo parcial
 * pintaría columnas en blanco indistinguibles de "sin dato".
 */
@Component({
  selector: 'rc-club-health',
  standalone: true,
  imports: [HlmButton, HlmSkeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-5xl">
      <div class="mb-6">
        <h1 class="text-2xl font-semibold tracking-[-0.3px]" i18n>Salud del club</h1>
        <p class="mt-1 text-sm text-muted-foreground" i18n>
          Alumnos, entrenador asignado y última actividad de cada grupo.
        </p>
      </div>

      @if (rows(); as loaded) {
        @if (loaded.length === 0) {
          <div class="rounded-xl border border-border bg-card p-8 text-center">
            <p class="text-muted-foreground" i18n>Aún no hay grupos.</p>
          </div>
        } @else {
          <div class="overflow-x-auto rounded-xl border border-border bg-card">
            <table class="w-full border-collapse text-sm">
              <caption class="sr-only" i18n>
                Grupos del club con su número de alumnos, si tienen entrenador asignado y su última
                actividad reportada
              </caption>
              <thead>
                <tr
                  class="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground"
                >
                  <th scope="col" class="py-2 pl-4 pr-4" i18n>Grupo</th>
                  <th scope="col" class="py-2 pr-4" i18n>Alumnos</th>
                  <th scope="col" class="py-2 pr-4" i18n>Entrenador</th>
                  <th scope="col" class="py-2 pr-4" i18n>Última actividad</th>
                </tr>
              </thead>
              <tbody>
                @for (row of loaded; track row.id) {
                  <tr class="border-b border-border last:border-0">
                    <td class="py-2.5 pl-4 pr-4 font-medium">{{ row.nombre }}</td>
                    <td class="py-2.5 pr-4">{{ row.totalAlumnos }}</td>
                    <td class="py-2.5 pr-4">
                      @if (row.tieneEntrenador) {
                        <span class="text-muted-foreground" i18n>Asignado</span>
                      } @else {
                        <span
                          class="inline-flex items-center gap-1 rounded-full bg-danger-soft px-2 py-0.5 text-xs font-semibold text-danger"
                          i18n
                        >
                          ⚠ Sin entrenador
                        </span>
                      }
                    </td>
                    <td class="py-2.5 pr-4">
                      @if (row.ultimaActividadEn; as reportedAt) {
                        <span [attr.title]="reportedAt">{{ relativeTime(reportedAt) }}</span>
                      } @else {
                        <span class="text-muted-foreground" i18n>Sin actividad</span>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      } @else if (loadFailed()) {
        <div class="rounded-xl border border-border bg-card p-8 text-center">
          <p class="text-muted-foreground" role="alert" i18n>
            No pudimos cargar la salud del club.
          </p>
          <button hlmBtn variant="outline" class="mt-4" (click)="reload()" i18n>Reintentar</button>
        </div>
      } @else {
        <div class="flex flex-col gap-3">
          <hlm-skeleton class="h-12 w-full" />
          <hlm-skeleton class="h-12 w-full" />
          <hlm-skeleton class="h-12 w-full" />
        </div>
      }
    </div>
  `,
})
export class ClubHealthComponent implements OnInit {
  private readonly groupService = inject(GroupService);
  private readonly clubHealthService = inject(ClubHealthService);

  readonly loadFailed = signal(false);
  private readonly activity = signal<GroupActivity[] | undefined>(undefined);

  readonly rows = computed<HealthRow[] | undefined>(() => {
    const groups = this.groupService.groups();
    const activity = this.activity();
    if (!groups || !activity) return undefined;
    const lastByGroup = new Map(activity.map((item) => [item.grupoId, item.ultimaActividadEn]));
    return groups.map((group) => ({
      id: group.id,
      nombre: group.nombre,
      totalAlumnos: group.totalAlumnos,
      tieneEntrenador: group.tieneEntrenador,
      ultimaActividadEn: lastByGroup.get(group.id),
    }));
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loadFailed.set(false);
    this.activity.set(undefined);
    forkJoin([
      this.groupService.load(),
      this.clubHealthService.getGroupActivity().pipe(tap((items) => this.activity.set(items))),
    ]).subscribe({ error: () => this.loadFailed.set(true) });
  }

  relativeTime(iso: string): string {
    return formatRelativeShortEs(iso);
  }
}
