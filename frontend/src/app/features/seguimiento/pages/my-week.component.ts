import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmSkeleton } from '@spartan-ng/helm/skeleton';
import { MyPlanService, MyResolvedSession, MyWeek } from '../../../core/my-plan.service';
import { formatPace } from '../../planificacion/pace-format';
import { sessionTypeLabel } from '../../planificacion/session-types';
import { ReportDialogComponent, ReportDialogData } from '../components/report-dialog.component';
import { formatLongDateEs, formatWeekdayShortEs, todayIsoDate } from '../date-format-es';
import { sessionTypeColorClass, sessionTypeIcon } from '../session-type-color';

/** Icono del estado del reporte para la tira de días (✓/⚡/✗ del spec 07). */
const REPORT_STATUS_ICON: Record<'HECHO' | 'PARCIAL' | 'NO_HECHO', string> = {
  HECHO: '✓',
  PARCIAL: '⚡',
  NO_HECHO: '✗',
};

/** Suma [days] a una fecha ISO `YYYY-MM-DD`, en UTC para no depender de la zona del navegador —
 * mismo criterio que `plan-detail.component.ts`. */
function addDays(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

interface DaySlot {
  readonly day: string;
  readonly label: string;
  readonly session?: MyResolvedSession;
}

/**
 * Home del alumno (LAL-29): su plan semanal, con la sesión del día seleccionado (por defecto, hoy)
 * desplegada en detalle y el resto de la semana como tira navegable. Lee de
 * `seguimiento.plan_resuelto_por_alumno` vía `MyPlanService` — nunca resuelve nada en el cliente
 * más allá de qué día es "hoy" (`todayIsoDate`).
 *
 * Estados 4/5/7 de `docs/wireframes/06-student-today.md` ("sin sesión hoy" / "plan sin publicar" /
 * "sin grupo") son indistinguibles con el read model actual — todos son "sin fila ese día" — y
 * quedan colapsados en un único empty state hasta que exista una segunda proyección sobre la
 * membresía del alumno (ver el README del módulo backend).
 */
@Component({
  selector: 'rc-my-week',
  standalone: true,
  imports: [HlmButton, HlmSkeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-[560px]">
      @if (week(); as loaded) {
        <div class="mb-5">
          <p class="text-sm text-muted-foreground">{{ greetingDay() }}</p>
          <h1 class="text-2xl font-semibold tracking-[-0.3px]" i18n>Tu plan de esta semana</h1>
        </div>

        <div class="mb-5 flex justify-between gap-1">
          @for (slot of days(); track slot.day) {
            <button
              type="button"
              class="flex flex-1 flex-col items-center gap-1 rounded-xl border border-transparent p-2 text-center transition-colors hover:bg-muted"
              [class.border-primary]="slot.day === selectedDay()"
              [class.bg-primary-soft]="slot.day === selectedDay()"
              [attr.aria-current]="slot.day === selectedDay() ? 'date' : null"
              (click)="selectedDay.set(slot.day)"
            >
              <span
                class="text-[11px] font-medium"
                [class.text-primary]="slot.day === selectedDay()"
                [class.text-muted-foreground]="slot.day !== selectedDay()"
                >{{ weekdayLabel(slot.day) }}</span
              >
              <span
                class="text-xs"
                [class.text-primary]="slot.day === selectedDay()"
                [class.text-muted-foreground]="slot.day !== selectedDay()"
                >{{ slot.day.slice(8) }}</span
              >
              @if (slot.session; as session) {
                @if (session.reporte; as reporte) {
                  <span
                    class="mt-0.5 text-xs leading-none"
                    [attr.aria-label]="reportStatusLabel(reporte.estado)"
                    >{{ reportStatusIcon(reporte.estado) }}</span
                  >
                } @else {
                  <span
                    class="mt-0.5 size-2 rounded-full"
                    [class]="sessionColor(session)"
                    [attr.aria-label]="sessionTypeLabel(session.tipo)"
                  ></span>
                }
              } @else {
                <span class="mt-0.5 size-2 rounded-full border border-dashed border-border"></span>
              }
            </button>
          }
        </div>

        @if (selectedSession(); as session) {
          <article class="overflow-hidden rounded-2xl border border-border bg-card">
            <div class="flex items-center gap-2 px-5 py-4" [class]="sessionColor(session)">
              <span class="text-lg" aria-hidden="true">{{ sessionIcon(session) }}</span>
              <span class="text-base font-semibold uppercase tracking-wide text-white">
                {{ sessionTypeLabel(session.tipo) }}
              </span>
            </div>
            <div class="flex flex-col gap-4 p-5">
              @if (volumeText(session) || session.ritmo) {
                <div class="flex gap-6">
                  @if (volumeText(session); as vol) {
                    <div>
                      <p class="text-lg font-semibold">{{ vol }}</p>
                      <p class="text-xs text-muted-foreground" i18n>volumen</p>
                    </div>
                  }
                  @if (session.ritmo; as ritmo) {
                    <div>
                      @if (paceText(session); as pace) {
                        <p class="text-lg font-semibold" i18n>{{ pace }} /km</p>
                        @if (ritmo.referenciaDistancia) {
                          <p class="text-xs text-muted-foreground" i18n>
                            basado en tu {{ ritmo.referenciaDistancia }}
                          </p>
                        } @else {
                          <p class="text-xs text-muted-foreground" i18n>ritmo</p>
                        }
                      } @else if (ritmo.faltaMarca) {
                        <p class="text-sm font-medium text-primary" i18n
                          >Sin ritmo · Añade tu marca de {{ ritmo.faltaMarca }}</p
                        >
                      }
                    </div>
                  }
                </div>
              }

              @if (session.notas) {
                <p class="rounded-lg bg-muted px-3 py-2 text-sm text-foreground">
                  📝 {{ session.notas }}
                </p>
              }

              @if (session.mensajeDelEntrenador) {
                <p class="rounded-lg border border-primary-soft bg-primary-soft px-3 py-2 text-sm text-foreground">
                  ✉ {{ session.mensajeDelEntrenador }}
                </p>
              }

              @if (!isFutureDay(selectedDay())) {
                <button hlmBtn variant="outline" type="button" (click)="openReport(session)">
                  @if (session.reporte) {
                    <span i18n>Editar reporte</span>
                  } @else {
                    <span i18n>Marcar como hecho</span>
                  }
                </button>
              }
            </div>
          </article>
        } @else {
          <div class="rounded-xl border border-border bg-card p-8 text-center">
            <p class="font-medium" i18n>Hoy no hay sesión programada</p>
            <p class="mt-1 text-sm text-muted-foreground" i18n>
              Habla con tu entrenador si crees que es un error
            </p>
          </div>
        }
      } @else if (loadFailed()) {
        <div class="rounded-xl border border-border bg-card p-8 text-center">
          <p class="text-muted-foreground" role="alert" i18n>No se ha podido cargar tu plan.</p>
          <button hlmBtn variant="outline" class="mt-4" (click)="reload()" i18n>Reintentar</button>
        </div>
      } @else {
        <div class="flex flex-col gap-3">
          <hlm-skeleton class="h-6 w-40" />
          <hlm-skeleton class="h-16 w-full" />
          <hlm-skeleton class="h-40 w-full" />
        </div>
      }
    </div>
  `,
})
export class MyWeekComponent implements OnInit {
  private readonly myPlanService = inject(MyPlanService);
  private readonly dialogService = inject(HlmDialogService);

  readonly week = signal<MyWeek | undefined>(undefined);
  readonly loadFailed = signal(false);
  readonly selectedDay = signal(todayIsoDate());

  readonly greetingDay = computed(() => formatLongDateEs(this.selectedDay()));

  /** Los 7 días de la semana devuelta, cada uno con su sesión si el backend la resolvió. */
  readonly days = computed<DaySlot[]>(() => {
    const loaded = this.week();
    if (!loaded) return [];
    const sessionsByDay = new Map(loaded.sesiones.map((s) => [s.dia, s]));
    return Array.from({ length: 7 }, (_, i) => {
      const day = addDays(loaded.semana, i);
      return { day, label: formatWeekdayShortEs(day), session: sessionsByDay.get(day) };
    });
  });

  readonly selectedSession = computed(() => this.days().find((d) => d.day === this.selectedDay())?.session);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loadFailed.set(false);
    this.myPlanService.getWeek().subscribe({
      next: (week) => this.week.set(week),
      error: () => this.loadFailed.set(true),
    });
  }

  weekdayLabel(day: string): string {
    return formatWeekdayShortEs(day);
  }

  sessionTypeLabel(type: MyResolvedSession['tipo']): string {
    return sessionTypeLabel(type);
  }

  sessionColor(session: MyResolvedSession): string {
    return sessionTypeColorClass(session.tipo);
  }

  sessionIcon(session: MyResolvedSession): string {
    return sessionTypeIcon(session.tipo);
  }

  volumeText(session: MyResolvedSession): string | null {
    const volumen = session.volumen;
    if (!volumen) return null;
    return volumen.tipo === 'DISTANCIA' ? `${volumen.metros} m` : `${volumen.minutos} min`;
  }

  paceText(session: MyResolvedSession): string | null {
    const segundosPorKm = session.ritmo?.segundosPorKm;
    return segundosPorKm != null ? formatPace(segundosPorKm) : null;
  }

  reportStatusIcon(status: 'HECHO' | 'PARCIAL' | 'NO_HECHO'): string {
    return REPORT_STATUS_ICON[status];
  }

  reportStatusLabel(status: 'HECHO' | 'PARCIAL' | 'NO_HECHO'): string {
    switch (status) {
      case 'HECHO':
        return $localize`Hecho`;
      case 'PARCIAL':
        return $localize`Parcial`;
      case 'NO_HECHO':
        return $localize`No hecho`;
    }
  }

  /** Solo se reporta hoy o un día pasado — mismo invariante que `SubmitSessionReportCommand`. */
  isFutureDay(day: string): boolean {
    return day > todayIsoDate();
  }

  openReport(session: MyResolvedSession): void {
    const day = this.selectedDay();
    const data: ReportDialogData = { day, session };
    this.dialogService.open<boolean>(ReportDialogComponent, { context: data }).closed$.subscribe((changed) => {
      if (changed) this.reload();
    });
  }
}
