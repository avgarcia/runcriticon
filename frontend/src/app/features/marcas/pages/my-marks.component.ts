import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmSkeleton } from '@spartan-ng/helm/skeleton';
import { MarkDistance, MyMark, MyMarks, MyMarksService } from '../../../core/my-marks.service';
import { MarkDialogComponent, MarkDialogData } from '../components/mark-dialog.component';
import { MARK_DISTANCES } from '../mark-distance-labels';
import { freshnessLabel, isStale } from '../mark-freshness';
import { formatMarkTime } from '../mark-time';

/** Una fila de la pantalla: la distancia, sus etiquetas y la marca ya registrada, si la hay. */
export interface MarkRow {
  readonly distance: MarkDistance;
  readonly short: string;
  readonly full: string | null;
  readonly mark?: MyMark;
}

/**
 * "Mis marcas" (LAL-31): las cuatro distancias estándar del alumno, con el banner de privacidad como
 * elemento visualmente prioritario, siguiendo `docs/diseno/mis-marcas.html`. Pantalla de autogestión
 * secundaria — no forma parte del *loop* entrenador↔alumno, así que no lleva un E2E crítico propio.
 */
@Component({
  selector: 'rc-my-marks',
  standalone: true,
  imports: [HlmButton, HlmSkeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-[560px]">
      <h1 class="mb-4 text-2xl font-semibold tracking-[-0.3px]" i18n>Mis marcas</h1>

      <div class="mb-5 flex items-start gap-3 rounded-2xl border border-primary-soft bg-primary-soft px-4 py-3.5">
        <span aria-hidden="true">🔒</span>
        <p class="text-[13.5px] leading-relaxed text-foreground">
          <strong i18n>Tus marcas son privadas.</strong>
          <span i18n>Solo tú las ves. Tu entrenador no las conoce — las usa solo como referencia para escribir tu plan.</span>
        </p>
      </div>

      @if (marks(); as loaded) {
        <div class="flex flex-col gap-3">
          @for (row of rows(loaded); track row.distance) {
            <article
              class="grid grid-cols-[1fr_auto] items-center gap-3.5 rounded-2xl border p-4"
              [class.border-border]="row.mark?.tiempoSegundos !== undefined"
              [class.border-dashed]="row.mark?.tiempoSegundos === undefined"
            >
              <div class="min-w-0">
                <div class="text-xs font-bold uppercase tracking-wide text-muted-foreground">
                  {{ row.short }}
                  @if (row.full) {
                    <span class="ml-1 text-[11px] font-medium normal-case tracking-normal">{{ row.full }}</span>
                  }
                </div>
                @if (row.mark?.tiempoSegundos !== undefined) {
                  <div class="mt-1 text-[26px] font-semibold tabular-nums tracking-[-0.5px]">
                    {{ formatTime(row.mark!.tiempoSegundos!) }}
                  </div>
                  <div class="mt-1.5 flex items-center gap-1.5 text-[11.5px] text-muted-foreground">
                    @if (isMarkStale(row.mark!)) {
                      <span aria-hidden="true">ⓘ</span>
                      <span>{{ freshness(row.mark!) }} · <span i18n>quizá ya la has mejorado</span></span>
                    } @else {
                      <span>{{ freshness(row.mark!) }}</span>
                    }
                  </div>
                } @else {
                  <div class="mt-1 text-base italic text-muted-foreground" i18n>Sin marca</div>
                }
              </div>
              <button hlmBtn variant="outline" size="sm" (click)="openMark(row)">
                @if (row.mark?.tiempoSegundos !== undefined) {
                  <span i18n>✎ Editar</span>
                } @else {
                  <span i18n>+ Añadir</span>
                }
              </button>
            </article>
          }
        </div>

        <p class="mt-6 text-center text-[12.5px] leading-relaxed text-muted-foreground">
          <span i18n>Tus marcas habilitan los ritmos personalizados.</span><br />
          <span i18n>Cuando tu plan dice «10K + 10s/km», lo verás traducido a tu ritmo.</span>
        </p>
      } @else if (loadFailed()) {
        <div class="rounded-xl border border-border bg-card p-8 text-center">
          <p class="text-muted-foreground" role="alert" i18n>No se han podido cargar tus marcas.</p>
          <button hlmBtn variant="outline" class="mt-4" (click)="reload()" i18n>Reintentar</button>
        </div>
      } @else {
        <div class="flex flex-col gap-3">
          <hlm-skeleton class="h-24 w-full" />
          <hlm-skeleton class="h-24 w-full" />
          <hlm-skeleton class="h-24 w-full" />
          <hlm-skeleton class="h-24 w-full" />
        </div>
      }
    </div>
  `,
})
export class MyMarksComponent implements OnInit {
  private readonly myMarksService = inject(MyMarksService);
  private readonly dialogService = inject(HlmDialogService);

  readonly marks = signal<MyMarks | undefined>(undefined);
  readonly loadFailed = signal(false);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.marks.set(undefined);
    this.loadFailed.set(false);
    this.myMarksService.getMarks().subscribe({
      next: (marks) => this.marks.set(marks),
      error: () => this.loadFailed.set(true),
    });
  }

  /** Las cuatro filas en el orden fijo del wireframe, con la etiqueta y la marca (si la hay) ya
   * emparejadas — el backend ya las devuelve en este orden, [MARK_DISTANCES] solo aporta el `full`. */
  rows(loaded: MyMarks): MarkRow[] {
    const byDistance = new Map(loaded.marcas.map((m) => [m.distancia, m]));
    return MARK_DISTANCES.map((d) => ({
      distance: d.value,
      short: d.short,
      full: d.full,
      mark: byDistance.get(d.value),
    }));
  }

  formatTime(totalSeconds: number): string {
    return formatMarkTime(totalSeconds);
  }

  freshness(mark: MyMark): string {
    return mark.modificadoEn ? freshnessLabel(mark.modificadoEn) : '';
  }

  isMarkStale(mark: MyMark): boolean {
    return mark.modificadoEn != null && isStale(mark.modificadoEn);
  }

  openMark(row: MarkRow): void {
    const data: MarkDialogData = {
      distance: row.distance,
      label: row.full ? `${row.short} · ${row.full}` : row.short,
      existingSeconds: row.mark?.tiempoSegundos ?? null,
    };
    this.dialogService.open<boolean>(MarkDialogComponent, { context: data }).closed$.subscribe((changed) => {
      if (changed) this.reload();
    });
  }
}
