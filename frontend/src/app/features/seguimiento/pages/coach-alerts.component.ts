import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { HlmSkeleton } from '@spartan-ng/helm/skeleton';
import { Alert, CoachAlertService } from '../../../core/coach-alert.service';
import { GroupService } from '../../../core/group.service';
import { RitmoFueraDeObjetivoAlert } from '../../../api/generated/models/ritmo-fuera-de-objetivo-alert';
import { formatRelativeShortEs } from '../date-format-es';

/** Las dos secciones del panel (recorte del AC de LAL-116, no las 3 de `docs/wireframes/08-coach-alerts.md`):
 * dolor y ausencia prolongada son urgentes; ritmo fuera de objetivo es informativo. Predicado de tipo (no
 * un `boolean` simple) para que la plantilla acceda a `notas` sin `$any()`. */
function isPaceOffTarget(alert: Alert): alert is RitmoFueraDeObjetivoAlert {
  return alert.tipo === 'RITMO_FUERA_DE_OBJETIVO';
}

/**
 * Panel de alertas del entrenador (LAL-116, M17), construido desde `docs/diseno/panel-alertas-entrenador.html`.
 * Solo lectura: sin "Descartar" ni CTA de detalle de alumno (no existe todavía ninguna pantalla a la que
 * llevar — el AC del ticket pide ver las alertas, no gestionarlas).
 *
 * El filtro de grupo es una fila de píldoras ("Todos" + un botón por grupo), mismo patrón que el selector
 * de tipo de sesión (`session-editor-dialog.component.ts`), no un `hlm-select`: evita construir el estado de
 * "sin selección" que un select necesitaría para volver a "todos".
 */
@Component({
  selector: 'rc-coach-alerts',
  standalone: true,
  imports: [HlmSkeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-3xl">
      <div class="mb-2 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 class="text-2xl font-semibold tracking-[-0.3px]" i18n>Alertas</h1>
          <p class="mt-1 max-w-[560px] text-sm text-muted-foreground" i18n>
            Solo lo accionable: molestias, alumnos sin reportar y ritmo muy fuera del objetivo.
          </p>
        </div>
      </div>

      @if (groups(); as loadedGroups) {
        @if (loadedGroups.length > 0) {
          <div class="mb-6 flex flex-wrap gap-2">
            <button
              type="button"
              class="rounded-full border px-3 py-1.5 text-sm font-medium transition-colors"
              [class.border-primary]="selectedGroupId() === ''"
              [class.bg-primary]="selectedGroupId() === ''"
              [class.text-primary-foreground]="selectedGroupId() === ''"
              [class.border-border]="selectedGroupId() !== ''"
              (click)="selectGroup('')"
              i18n
            >
              Todos mis grupos
            </button>
            @for (group of loadedGroups; track group.id) {
              <button
                type="button"
                class="rounded-full border px-3 py-1.5 text-sm font-medium transition-colors"
                [class.border-primary]="selectedGroupId() === group.id"
                [class.bg-primary]="selectedGroupId() === group.id"
                [class.text-primary-foreground]="selectedGroupId() === group.id"
                [class.border-border]="selectedGroupId() !== group.id"
                (click)="selectGroup(group.id)"
              >
                {{ group.nombre }}
              </button>
            }
          </div>
        }
      }

      @if (alerts(); as loaded) {
        @if (loaded.length === 0) {
          <div class="rounded-xl border border-border bg-card p-8 text-center">
            <p class="text-base font-semibold" i18n>Todo en orden</p>
            <p class="mt-1 text-sm text-muted-foreground" i18n>
              Ningún alumno de tus grupos tiene una alerta activa ahora mismo.
            </p>
          </div>
        } @else {
          <p class="mb-4 text-sm text-muted-foreground">
            <strong class="text-foreground">{{ loaded.length }}</strong>
            <span i18n> alertas activas</span>
            @if (urgentAlerts().length > 0) {
              ·
              <strong class="text-danger">{{ urgentAlerts().length }}</strong>
              <span class="text-danger" i18n> urgentes</span>
            }
          </p>

          @if (urgentAlerts().length > 0) {
            <section class="mb-7">
              <div class="mb-3 flex items-center gap-2">
                <span class="text-xs font-bold uppercase tracking-[0.8px] text-danger" i18n>Urgente</span>
                <span class="rounded-full bg-muted px-2 py-0.5 text-xs font-semibold text-muted-foreground">{{
                  urgentAlerts().length
                }}</span>
              </div>
              <ul class="m-0 flex list-none flex-col gap-2.5 p-0">
                @for (alert of urgentAlerts(); track $index) {
                  <li
                    class="grid grid-cols-[40px_1fr] gap-3.5 rounded-lg border border-border border-l-4 border-l-danger bg-card p-4"
                  >
                    <div class="flex size-10 items-center justify-center rounded-full bg-danger-soft text-lg">
                      {{ alert.tipo === 'DOLOR_REPORTADO' ? '🤕' : '⚠' }}
                    </div>
                    <div>
                      @if (alert.tipo === 'DOLOR_REPORTADO') {
                        <p class="text-sm font-semibold" i18n>Dolor reportado hoy en la sesión</p>
                        <p class="mt-0.5 text-xs text-muted-foreground">
                          {{ groupName(alert.grupoId) }} · {{ relativeTime(alert.reportadoEn) }}
                        </p>
                        @if (alert.notas) {
                          <p class="mt-2 rounded-md bg-muted px-2.5 py-2 text-sm italic">"{{ alert.notas }}"</p>
                        }
                      } @else {
                        <p class="text-sm font-semibold">
                          <span i18n>{{ alert.diasSinReportar }} días sin reportar</span>
                        </p>
                        <p class="mt-0.5 text-xs text-muted-foreground">
                          {{ groupName(alert.grupoId) }}
                        </p>
                      }
                    </div>
                  </li>
                }
              </ul>
            </section>
          }

          @if (infoAlerts().length > 0) {
            <section>
              <div class="mb-3 flex items-center gap-2">
                <span class="text-xs font-bold uppercase tracking-[0.8px] text-primary" i18n>Informativo</span>
                <span class="rounded-full bg-muted px-2 py-0.5 text-xs font-semibold text-muted-foreground">{{
                  infoAlerts().length
                }}</span>
              </div>
              <ul class="m-0 flex list-none flex-col gap-2.5 p-0">
                @for (alert of infoAlerts(); track $index) {
                  <li
                    class="grid grid-cols-[40px_1fr] gap-3.5 rounded-lg border border-border border-l-4 border-l-primary bg-card p-4"
                  >
                    <div class="flex size-10 items-center justify-center rounded-full bg-primary-soft text-lg">
                      ⚡
                    </div>
                    <div>
                      <p class="text-sm font-semibold" i18n>Entrenó fuera del ritmo objetivo</p>
                      <p class="mt-0.5 text-xs text-muted-foreground">{{ groupName(alert.grupoId) }}</p>
                      <p class="mt-2 rounded-md bg-muted px-2.5 py-2 text-sm italic">"{{ alert.notas }}"</p>
                    </div>
                  </li>
                }
              </ul>
            </section>
          }
        }
      } @else if (loadFailed()) {
        <div class="rounded-xl border border-border bg-card p-8 text-center">
          <p class="text-muted-foreground" role="alert" i18n>No pudimos cargar las alertas.</p>
          <button type="button" class="mt-4 text-sm font-semibold text-primary" (click)="reload()" i18n>
            Reintentar
          </button>
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
export class CoachAlertsComponent implements OnInit {
  private readonly alertService = inject(CoachAlertService);
  private readonly groupService = inject(GroupService);

  readonly groups = this.groupService.groups;

  readonly alerts = signal<Alert[] | undefined>(undefined);
  readonly loadFailed = signal(false);
  readonly selectedGroupId = signal('');

  readonly urgentAlerts = computed(() => (this.alerts() ?? []).filter((alert) => !isPaceOffTarget(alert)));
  readonly infoAlerts = computed(() => (this.alerts() ?? []).filter(isPaceOffTarget));

  ngOnInit(): void {
    this.groupService.load().subscribe();
    this.reload();
  }

  reload(): void {
    this.loadFailed.set(false);
    this.alerts.set(undefined);
    this.alertService.getAlerts(this.selectedGroupId() || undefined).subscribe({
      next: (alerts) => this.alerts.set(alerts),
      error: () => this.loadFailed.set(true),
    });
  }

  selectGroup(grupoId: string): void {
    this.selectedGroupId.set(grupoId);
    this.reload();
  }

  groupName(grupoId: string): string {
    return this.groups()?.find((group) => group.id === grupoId)?.nombre ?? grupoId;
  }

  relativeTime(iso: string): string {
    return formatRelativeShortEs(iso);
  }
}
