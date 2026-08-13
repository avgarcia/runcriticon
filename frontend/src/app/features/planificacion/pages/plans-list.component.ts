import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HlmBadge } from '@spartan-ng/helm/badge';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmSkeleton } from '@spartan-ng/helm/skeleton';
import { Plan, PlanService } from '../../../core/plan.service';

/**
 * Planes en borrador de un grupo (LAL-114, arranque del módulo): la pantalla mínima del AC7, sin editor de
 * sesión (LAL-24) ni publicación (LAL-25) — solo ver los borradores y crear uno nuevo.
 *
 * `grupoId` llega por la URL, no por selector: no hay todavía un punto de entrada desde el listado de grupos
 * de `club_taxonomia` (fuera de alcance de este ticket), así que la ruta se teclea o se enlaza directamente.
 */
@Component({
  selector: 'rc-plans-list',
  standalone: true,
  imports: [HlmBadge, HlmButton, HlmSkeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-3xl">
      <div class="mb-6 flex flex-wrap items-start justify-between gap-6">
        <div>
          <h1 class="text-2xl font-semibold tracking-[-0.3px]" i18n>Planes en borrador</h1>
          <p class="mt-1 max-w-[560px] text-sm text-muted-foreground" i18n>
            Los planes semanales de este grupo que todavía no se han publicado.
          </p>
        </div>
        <button hlmBtn type="button" [disabled]="creating()" (click)="createDraft()" i18n>
          + Nuevo plan (esta semana)
        </button>
      </div>

      @if (plans(); as loaded) {
        @if (loaded.length === 0) {
          <div class="rounded-xl border border-border bg-card p-8 text-center">
            <p class="text-muted-foreground" i18n>
              Todavía no hay planes en borrador para este grupo.
            </p>
          </div>
        } @else {
          <ul class="m-0 flex list-none flex-col gap-3 p-0">
            @for (plan of loaded; track plan.id) {
              <li class="flex items-center justify-between gap-4 rounded-xl border border-border bg-card p-5">
                <p class="text-base font-semibold">{{ plan.semana }}</p>
                <span hlmBadge variant="outline">{{ plan.estado }}</span>
              </li>
            }
          </ul>
        }
      } @else if (loadFailed()) {
        <div class="rounded-xl border border-border bg-card p-8 text-center">
          <p class="text-muted-foreground" role="alert" i18n>No se han podido cargar los planes.</p>
          <button hlmBtn variant="outline" class="mt-4" (click)="reload()" i18n>Reintentar</button>
        </div>
      } @else {
        <div class="flex flex-col gap-3">
          <hlm-skeleton class="h-16 w-full" />
          <hlm-skeleton class="h-16 w-full" />
        </div>
      }

      @if (createFailed()) {
        <p class="mt-4 text-sm text-danger" role="alert" i18n>
          No se ha podido crear el plan. ¿Tienes relación con este grupo?
        </p>
      }
    </div>
  `,
})
export class PlansListComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly planService = inject(PlanService);

  private readonly groupId = this.route.snapshot.paramMap.get('grupoId')!;

  readonly plans = signal<Plan[] | undefined>(undefined);
  readonly loadFailed = signal(false);
  readonly creating = signal(false);
  readonly createFailed = signal(false);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loadFailed.set(false);
    this.planService.listDrafts(this.groupId).subscribe({
      next: (plans) => this.plans.set(plans),
      error: () => this.loadFailed.set(true),
    });
  }

  /** Crea un borrador para el lunes de la semana en curso (o la próxima, si hoy ya es lunes en adelante). */
  createDraft(): void {
    this.creating.set(true);
    this.createFailed.set(false);
    this.planService.create(this.groupId, nextMonday()).subscribe({
      next: () => {
        this.creating.set(false);
        this.reload();
      },
      error: () => {
        this.creating.set(false);
        this.createFailed.set(true);
      },
    });
  }
}

/** El lunes de la semana en curso: `getDay()` es 0 (domingo) a 6 (sábado), lunes es 1. */
function nextMonday(): string {
  const today = new Date();
  const dayOfWeek = today.getDay();
  const daysSinceMonday = dayOfWeek === 0 ? 6 : dayOfWeek - 1;
  const monday = new Date(today);
  monday.setDate(today.getDate() - daysSinceMonday);
  return monday.toISOString().slice(0, 10);
}
