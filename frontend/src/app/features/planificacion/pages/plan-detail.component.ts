import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HlmBadge } from '@spartan-ng/helm/badge';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmSkeleton } from '@spartan-ng/helm/skeleton';
import { PlanDetail, PlanService, PlanSession } from '../../../core/plan.service';
import {
  PersonalizationsDialogComponent,
  PersonalizationsDialogData,
} from '../components/personalizations-dialog.component';
import { PublishPlanDialogComponent, PublishPlanDialogData } from '../components/publish-plan-dialog.component';
import { SessionEditorDialogComponent, SessionEditorDialogData } from '../components/session-editor-dialog.component';
import { formatPace } from '../pace-format';
import { sessionTypeLabel } from '../session-types';

const DAY_LABELS = [
  $localize`Dom`,
  $localize`Lun`,
  $localize`Mar`,
  $localize`Mié`,
  $localize`Jue`,
  $localize`Vie`,
  $localize`Sáb`,
];

interface DaySlot {
  readonly day: string;
  readonly label: string;
  readonly session?: PlanSession;
}

/** Suma [days] a una fecha ISO `YYYY-MM-DD` en UTC, para no depender de la zona horaria del navegador. */
function addDays(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

function volumeText(session: PlanSession): string | null {
  const volumen = session.volumen;
  if (!volumen) return null;
  return volumen.tipo === 'DISTANCIA' ? `${volumen.metros} m` : `${volumen.minutos} min`;
}

function paceText(session: PlanSession): string | null {
  const ritmo = session.ritmo;
  if (ritmo?.tipo === 'ABSOLUTO' && ritmo.segundosPorKm != null) {
    return `${formatPace(ritmo.segundosPorKm)} /km`;
  }
  return null;
}

/**
 * Detalle de un plan semanal: rejilla de 7 días derivada de `plan.semana`, con una tarjeta por sesión y un
 * hueco vacío ("+ Añadir sesión") en los días sin ella (LAL-24, decisión 6 del ticket). No es el side sheet
 * animado del wireframe hi-fi (`docs/diseno/editor-sesion.html`) — esa vista semanal completa no existe
 * todavía en el frontend y no hay ticket que la cubra; esta rejilla mínima es lo que hace verificable AC1
 * ("crear una sesión en <30 s").
 */
@Component({
  selector: 'rc-plan-detail',
  standalone: true,
  imports: [HlmBadge, HlmButton, HlmSkeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-4xl">
      @if (plan(); as loaded) {
        <div class="mb-6 flex items-start justify-between">
          <div>
            <h1 class="text-2xl font-semibold tracking-[-0.3px]" i18n>Plan semanal</h1>
            <p class="mt-1 text-sm text-muted-foreground">
              <span i18n>Semana del</span> {{ loaded.semana }} · <span hlmBadge variant="outline">{{ loaded.estado }}</span>
            </p>
          </div>
          @if (loaded.estado === 'BORRADOR') {
            <button hlmBtn (click)="openPublish(loaded)" i18n>Publicar al grupo</button>
          }
        </div>

        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-7">
          @for (slot of days(); track slot.day) {
            <article class="flex min-h-32 flex-col rounded-xl border border-border bg-card p-3">
              <div class="mb-2 flex items-baseline justify-between">
                <span class="text-xs font-semibold text-muted-foreground">{{ slot.label }}</span>
                <span class="text-xs text-muted-foreground">{{ slot.day.slice(5) }}</span>
              </div>
              @if (slot.session; as session) {
                <button
                  type="button"
                  class="flex flex-1 flex-col items-start gap-1 rounded-lg text-left transition-opacity hover:opacity-80"
                  (click)="openSession(loaded, slot.day, session)"
                >
                  <span hlmBadge>{{ typeLabel(session.tipo) }}</span>
                  @if (volume(session); as vol) {
                    <span class="text-sm font-medium">{{ vol }}</span>
                  }
                  @if (pace(session); as ritmo) {
                    <span class="text-xs text-muted-foreground">{{ ritmo }}</span>
                  }
                  @if (session.notas) {
                    <span class="line-clamp-2 text-xs text-muted-foreground">{{ session.notas }}</span>
                  }
                  @if (personalizationCount(session.id); as count) {
                    <span class="mt-auto text-xs text-primary" i18n>👥 {{ count }} ajuste(s)</span>
                  }
                </button>
              } @else if (loaded.estado === 'BORRADOR') {
                <button
                  type="button"
                  class="flex flex-1 items-center justify-center rounded-lg border border-dashed border-border text-xs text-muted-foreground transition-colors hover:border-primary hover:text-primary"
                  (click)="openEditor(slot.day)"
                  i18n
                >
                  + Añadir sesión
                </button>
              }
            </article>
          }
        </div>
      } @else if (loadFailed()) {
        <div class="rounded-xl border border-border bg-card p-8 text-center">
          <p class="text-muted-foreground" role="alert" i18n>No se ha podido cargar el plan.</p>
          <button hlmBtn variant="outline" class="mt-4" (click)="reload()" i18n>Reintentar</button>
        </div>
      } @else {
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-7">
          @for (i of skeletonSlots; track i) {
            <hlm-skeleton class="h-32 w-full" />
          }
        </div>
      }
    </div>
  `,
})
export class PlanDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly planService = inject(PlanService);
  private readonly dialogService = inject(HlmDialogService);

  private readonly planId = this.route.snapshot.paramMap.get('planId')!;

  readonly plan = signal<PlanDetail | undefined>(undefined);
  readonly loadFailed = signal(false);
  readonly skeletonSlots = [0, 1, 2, 3, 4, 5, 6];

  /** Los 7 días de la semana del plan, cada uno con su sesión si existe (LAL-24, decisión 2: como mucho una
   * sesión por día). */
  readonly days = computed<DaySlot[]>(() => {
    const loaded = this.plan();
    if (!loaded) return [];
    const sessionsByDay = new Map(loaded.sesiones.map((s) => [s.dia, s]));
    return Array.from({ length: 7 }, (_, i) => {
      const day = addDays(loaded.semana, i);
      return { day, label: DAY_LABELS[new Date(`${day}T00:00:00Z`).getUTCDay()], session: sessionsByDay.get(day) };
    });
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loadFailed.set(false);
    this.planService.get(this.planId).subscribe({
      next: (plan) => this.plan.set(plan),
      error: () => this.loadFailed.set(true),
    });
  }

  typeLabel(type: PlanSession['tipo']): string {
    return sessionTypeLabel(type);
  }

  volume(session: PlanSession): string | null {
    return volumeText(session);
  }

  pace(session: PlanSession): string | null {
    return paceText(session);
  }

  /** El día vacío solo abre el editor de alta (BORRADOR); una sesión existente abre el editor si el
   * plan sigue en BORRADOR, o directamente las personalizaciones si ya está PUBLICADO — no hay nada
   * más que hacer con la sesión base una vez publicada (LAL-26). */
  openSession(plan: PlanDetail, day: string, session: PlanSession): void {
    if (plan.estado === 'BORRADOR') {
      this.openEditor(day, session);
    } else {
      this.openPersonalizations(plan, session);
    }
  }

  openEditor(day: string, session?: PlanSession): void {
    const data: SessionEditorDialogData = {
      planId: this.planId,
      day,
      session,
      personalizationCount: session ? this.personalizationCount(session.id) : undefined,
    };
    this.dialogService
      .open<boolean | 'manage-personalizations'>(SessionEditorDialogComponent, { context: data })
      .closed$.subscribe((result) => {
        if (result === 'manage-personalizations' && session) {
          this.openPersonalizations(this.plan()!, session);
        } else if (result) {
          this.reload();
        }
      });
  }

  openPersonalizations(plan: PlanDetail, session: PlanSession): void {
    const data: PersonalizationsDialogData = {
      planId: this.planId,
      grupoId: plan.grupoId,
      sesionId: session.id,
      sesionLabel: `${this.typeLabel(session.tipo)} · ${session.dia}`,
    };
    this.dialogService
      .open<boolean>(PersonalizationsDialogComponent, { context: data })
      .closed$.subscribe((changed) => {
        if (changed) this.reload();
      });
  }

  personalizationCount(sesionId: string): number {
    return this.plan()?.personalizaciones?.filter((p) => p.sesionId === sesionId).length ?? 0;
  }

  openPublish(plan: PlanDetail): void {
    const data: PublishPlanDialogData = { plan };
    this.dialogService
      .open<boolean>(PublishPlanDialogComponent, { context: data })
      .closed$.subscribe((published) => {
        if (published) this.reload();
      });
  }
}
