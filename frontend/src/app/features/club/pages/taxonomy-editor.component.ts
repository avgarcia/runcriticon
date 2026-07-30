import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmSkeleton } from '@spartan-ng/helm/skeleton';
import { filter, tap } from 'rxjs';
import { TagDetailComponent } from '../components/tag-detail.component';
import { LabelDialogComponent, LabelDialogData } from '../components/label-dialog.component';
import { TaxonomyService } from '../../../core/taxonomy.service';
import { ToastService } from '../../../core/toast.service';

/** Límite del contrato para el nombre de un tag. */
const MAX_TAG_LENGTH = 40;

/**
 * Editor de la taxonomía del club (maqueta `docs/diseno/editor-taxonomia.html`): lista de tags a la
 * izquierda, detalle del seleccionado a la derecha.
 *
 * De la maqueta se dejan fuera el tipo del tag, el interruptor de varios valores por alumno, la
 * reordenación por arrastre y los contadores de alumnos: hoy nada de eso existe en el contrato, y
 * pintarlo sería prometer algo que no se puede guardar.
 *
 * Los archivados llegan en la misma respuesta que los activos y se pintan atenuados con opción de
 * reactivarlos, en vez de esconderlos: es la única forma de recuperarlos.
 *
 * No se comprueban permisos aquí porque la ruta ya es solo de admin y el único rol con
 * `TAXONOMY:MANAGE` es ese; condicionar además cada botón dejaría la pantalla en blanco durante el
 * instante en que `/me/permissions` aún no ha respondido. La barrera real la pone el backend.
 */
@Component({
  selector: 'rc-taxonomy-editor',
  standalone: true,
  imports: [HlmButton, HlmSkeleton, TagDetailComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-5xl">
      <div class="mb-6 flex flex-wrap items-start justify-between gap-6">
        <div>
          <h1 class="text-2xl font-semibold tracking-[-0.3px]" i18n>Taxonomía del club</h1>
          <p class="mt-1 max-w-[560px] text-sm text-muted-foreground" i18n>
            Los tags definen cómo agrupas a tus alumnos. Cada tag tiene una lista de valores
            posibles; los grupos se construyen filtrando por ellos.
          </p>
        </div>
        @if (taxonomy(); as loaded) {
          @if (loaded.tags.length > 0) {
            <button hlmBtn (click)="createTag()" i18n>+ Nuevo tag</button>
          }
        }
      </div>

      @if (taxonomy(); as loaded) {
        @if (loaded.tags.length === 0) {
          <div class="rounded-xl border border-border bg-card p-8 text-center">
            <p class="text-muted-foreground" i18n>
              Aún no tienes tags. Crea el primero para empezar a clasificar a tus alumnos.
            </p>
            <button hlmBtn class="mt-4" (click)="createTag()" i18n>+ Nuevo tag</button>
          </div>
        } @else {
          <div class="grid gap-5 lg:grid-cols-[280px_1fr]">
            <nav
              class="self-start overflow-hidden rounded-xl border border-border bg-card"
              aria-label="Tags del club"
              i18n-aria-label
            >
              <!-- Texto en el color principal y no en el gris apagado: sobre el tinte de la cabecera
                   y a este tamaño, el gris se queda por debajo del contraste que exige AA. -->
              <h2
                class="border-b border-border bg-muted px-3.5 py-3 text-[11.5px] font-semibold uppercase tracking-[0.6px] text-foreground"
                i18n
              >
                Tags del club
              </h2>
              <ul class="m-0 flex list-none flex-col p-0">
                @for (tag of loaded.tags; track tag.id) {
                  <li>
                    <!-- Lo archivado se distingue en cursiva y con su etiqueta, no atenuándolo: la
                         opacidad de la maqueta hunde el contraste por debajo de AA. -->
                    <button
                      type="button"
                      class="flex w-full items-center gap-2.5 border-b border-border px-3.5 py-3 text-left last:border-0 hover:bg-muted"
                      [class]="tag.id === selectedTag()?.id ? 'bg-primary-soft' : ''"
                      [class.italic]="tag.archivadoEn"
                      [attr.aria-current]="tag.id === selectedTag()?.id ? 'true' : null"
                      (click)="select(tag.id)"
                    >
                      <span
                        class="min-w-0 flex-1 truncate text-sm"
                        [class]="tag.id === selectedTag()?.id ? 'font-semibold text-primary' : ''"
                      >
                        {{ tag.nombre }}
                        @if (tag.archivadoEn) {
                          <span class="text-[11px] font-normal" i18n>· archivado</span>
                        }
                      </span>
                      <!-- El gris apagado no llega al contraste AA sobre el tinte de la fila
                           seleccionada; ahí el contador pasa al color de marca. -->
                      <span
                        class="shrink-0 text-[11.5px]"
                        [class]="
                          tag.id === selectedTag()?.id ? 'text-primary' : 'text-muted-foreground'
                        "
                      >
                        {{ valuesLabel(tag.valores.length) }}
                      </span>
                    </button>
                  </li>
                }
              </ul>
            </nav>

            @if (selectedTag(); as tag) {
              <rc-tag-detail [tag]="tag" />
            }
          </div>
        }
      } @else {
        <div class="grid gap-5 lg:grid-cols-[280px_1fr]">
          <hlm-skeleton class="h-64 w-full" />
          <hlm-skeleton class="h-64 w-full" />
        </div>
      }
    </div>
  `,
})
export class TaxonomyEditorComponent implements OnInit {
  private readonly taxonomyService = inject(TaxonomyService);
  private readonly dialogService = inject(HlmDialogService);
  private readonly toastService = inject(ToastService);

  readonly taxonomy = this.taxonomyService.taxonomy;

  private readonly selectedId = signal<string | null>(null);

  /**
   * Tag abierto en el detalle. Cae al primero de la lista cuando no hay ninguno elegido, en vez de
   * sincronizar la selección con un `effect`: así el primer render ya trae detalle y la selección no
   * queda apuntando a un tag que ya no existe.
   */
  readonly selectedTag = computed(() => {
    const loaded = this.taxonomy();
    if (!loaded) return null;
    return loaded.tags.find((tag) => tag.id === this.selectedId()) ?? loaded.tags[0] ?? null;
  });

  ngOnInit(): void {
    // Esta pantalla es la única que usa la taxonomía, así que la carga ella y no el shell.
    this.taxonomyService.load().subscribe({ error: () => undefined });
  }

  select(tagId: string): void {
    this.selectedId.set(tagId);
  }

  valuesLabel(count: number): string {
    return count === 1 ? $localize`1 valor` : $localize`${count}:count: valores`;
  }

  createTag(): void {
    let createdId: string | null = null;
    const data: LabelDialogData = {
      title: $localize`Nuevo tag`,
      label: $localize`Nombre`,
      confirmLabel: $localize`Crear`,
      initialValue: '',
      maxLength: MAX_TAG_LENGTH,
      field: 'nombre',
      submit: (value) =>
        this.taxonomyService.createTag(value).pipe(tap((tag) => (createdId = tag.id))),
    };

    this.dialogService
      .open<string>(LabelDialogComponent, { context: data })
      .closed$.pipe(filter((nombre): nombre is string => !!nombre))
      .subscribe((nombre) => {
        // Se abre el tag recién creado: viene vacío y lo siguiente es darle valores.
        if (createdId) this.selectedId.set(createdId);
        this.toastService.success($localize`Tag ${nombre}:nombre: creado.`);
      });
  }
}
