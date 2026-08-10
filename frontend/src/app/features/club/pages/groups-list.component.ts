import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmSkeleton } from '@spartan-ng/helm/skeleton';
import { forkJoin } from 'rxjs';
import { GroupService, GroupSummary } from '../../../core/group.service';
import { Taxonomy, TaxonomyService } from '../../../core/taxonomy.service';

/** Un grupo con su filtro ya traducido a algo legible. */
interface GroupCard {
  readonly summary: GroupSummary;
  readonly filtro: string;
}

/**
 * Grupos del club (maqueta `docs/diseno/constructor-grupos.html`): una tarjeta por grupo con su
 * filtro y cuánta gente cae dentro.
 *
 * El filtro llega como identificadores y se traduce aquí cruzándolo con la taxonomía, que esta
 * pantalla necesita igualmente: el conector se escribe en el idioma de quien mira, y el rótulo de
 * cada valor vive en un solo sitio en vez de repetirse dentro de cada grupo.
 *
 * De la maqueta se dejan fuera el entrenador asignado, la última actividad, las sugerencias de
 * fusión y el menú de editar, duplicar o archivar: no hay con qué sostenerlos todavía. El botón de
 * crear llega con el constructor, para no ofrecer aquí una ruta que aún no existe.
 */
@Component({
  selector: 'rc-groups-list',
  standalone: true,
  imports: [HlmButton, HlmSkeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-5xl">
      <div class="mb-6 flex flex-wrap items-start justify-between gap-6">
        <div>
          <h1 class="text-2xl font-semibold tracking-[-0.3px]" i18n>Grupos</h1>
          <p class="mt-1 max-w-[560px] text-sm text-muted-foreground" i18n>
            Un grupo es una consulta sobre los tags de tus alumnos: quien los cumple, entra.
          </p>
        </div>
      </div>

      @if (cards(); as loaded) {
        @if (loaded.length === 0) {
          <div class="rounded-xl border border-border bg-card p-8 text-center">
            <p class="text-muted-foreground" i18n>
              Aún no tienes grupos. Crea el primero para empezar a planificar por grupo.
            </p>
          </div>
        } @else {
          <ul class="m-0 flex list-none flex-col gap-3 p-0">
            @for (card of loaded; track card.summary.id) {
              <li class="rounded-xl border border-border bg-card p-5">
                <h2 class="text-base font-semibold">{{ card.summary.nombre }}</h2>
                <p class="mt-1 text-sm text-muted-foreground">{{ card.filtro }}</p>
                <p class="mt-2 text-sm">{{ membersLabel(card.summary.totalAlumnos) }}</p>
                @if (card.summary.totalAlumnos === 0) {
                  <p class="mt-2 text-sm text-danger" role="status" i18n>
                    ⚠ Ningún alumno cumple este filtro ahora mismo.
                  </p>
                }
              </li>
            }
          </ul>
        }
      } @else if (loadFailed()) {
        <div class="rounded-xl border border-border bg-card p-8 text-center">
          <p class="text-muted-foreground" role="alert" i18n>No se han podido cargar los grupos.</p>
          <button hlmBtn variant="outline" class="mt-4" (click)="reload()" i18n>Reintentar</button>
        </div>
      } @else {
        <div class="flex flex-col gap-3">
          <hlm-skeleton class="h-24 w-full" />
          <hlm-skeleton class="h-24 w-full" />
        </div>
      }
    </div>
  `,
})
export class GroupsListComponent implements OnInit {
  private readonly groupService = inject(GroupService);
  private readonly taxonomyService = inject(TaxonomyService);

  readonly loadFailed = signal(false);

  readonly cards = computed<GroupCard[] | undefined>(() => {
    const groups = this.groupService.groups();
    const taxonomy = this.taxonomyService.taxonomy();
    if (!groups || !taxonomy) return undefined;
    return groups.map((summary) => ({ summary, filtro: this.describeFilter(summary, taxonomy) }));
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loadFailed.set(false);
    forkJoin([this.groupService.load(), this.taxonomyService.load()]).subscribe({
      error: () => this.loadFailed.set(true),
    });
  }

  membersLabel(total: number): string {
    if (total === 0) return $localize`Sin alumnos`;
    return total === 1 ? $localize`1 alumno` : $localize`${total}:total: alumnos`;
  }

  /**
   * Compone el filtro en palabras. Las condiciones se ordenan por el nombre del eje: el orden en que
   * llegan los valores es estable, pero no significa nada para quien lo lee.
   */
  private describeFilter(summary: GroupSummary, taxonomy: Taxonomy): string {
    if (summary.valores.length === 0) return $localize`Sin filtro: solo entra quien se añada a mano.`;

    const partes = summary.valores
      .map((valorId) => {
        for (const tag of taxonomy.tags) {
          const valor = tag.valores.find((item) => item.id === valorId);
          if (valor) {
            const archivado = valor.archivadoEn || tag.archivadoEn ? $localize` · archivado` : '';
            return { eje: tag.nombre, texto: `${tag.nombre} = ${valor.valor}${archivado}` };
          }
        }
        return { eje: '', texto: $localize`un valor que ya no existe` };
      })
      .sort((a, b) => a.eje.localeCompare(b.eje));

    return partes.map((parte) => parte.texto).join($localize` y `);
  }
}
