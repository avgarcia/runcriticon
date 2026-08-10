import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmSkeleton } from '@spartan-ng/helm/skeleton';
import { GroupMembers } from '../../../core/group.service';

/** Alumnos que se enseñan antes de plegar la lista tras «+N más». */
const VISIBLE_MEMBERS = 10;

/**
 * Panel de vista previa del constructor: cuántos alumnos caen en el filtro y quiénes son.
 *
 * Mientras se recalcula **se conserva el número anterior** en vez de vaciarlo, para que el contador no
 * parpadee cada vez que se toca una condición; solo la primera carga enseña esqueletos.
 *
 * De la maqueta se dejan fuera las acciones de excluir y restaurar alumnos y el aviso de que alguien
 * ya está en otro grupo con plan activo: las excepciones manuales de pertenencia y los planes todavía
 * no existen, y pintarlos sería prometer algo que no se puede guardar.
 */
@Component({
  selector: 'rc-group-preview',
  standalone: true,
  imports: [HlmButton, HlmSkeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside class="self-start rounded-xl border border-border bg-card p-5">
      <h2 class="text-[11.5px] font-semibold uppercase tracking-[0.6px] text-foreground" i18n>
        Vista previa
      </h2>

      @if (members(); as loaded) {
        <!-- Región viva: el contador cambia solo al editar el filtro, sin que nadie pulse nada. -->
        <div aria-live="polite" [attr.aria-busy]="loading() ? 'true' : null">
          <p class="mt-2 text-3xl font-semibold text-primary">{{ loaded.total }}</p>
          <p class="text-sm text-muted-foreground">{{ countLabel(loaded.total) }}</p>

          @if (loaded.total === 0) {
            <p class="mt-4 text-sm text-muted-foreground">{{ emptyLabel() }}</p>
          } @else {
            <ul class="mt-4 flex list-none flex-col gap-1 p-0">
              @for (member of visibleMembers(); track member.id) {
                <li class="flex items-center gap-2.5 border-b border-border py-2 last:border-0">
                  <span
                    class="grid size-7 shrink-0 place-items-center rounded-full bg-primary-soft text-[11px] font-semibold text-primary"
                    aria-hidden="true"
                    >{{ initials(member.nombre) }}</span
                  >
                  <span class="min-w-0 flex-1 truncate text-sm">{{ member.nombre }}</span>
                </li>
              }
            </ul>

            @if (hiddenCount() > 0) {
              <button hlmBtn variant="ghost" size="sm" class="mt-2" (click)="expand()">
                {{ moreLabel(hiddenCount()) }}
              </button>
            }
          }
        </div>
      } @else {
        <hlm-skeleton class="mt-3 h-20 w-full" />
      }

      @if (error(); as message) {
        <div class="mt-4 rounded-lg border border-border bg-muted p-3">
          <p class="text-sm text-danger" role="alert">{{ message }}</p>
          <button hlmBtn variant="outline" size="sm" class="mt-2" (click)="reload.emit()" i18n>
            Recargar taxonomía
          </button>
        </div>
      }
    </aside>
  `,
})
export class GroupPreviewComponent {
  readonly members = input<GroupMembers | undefined>(undefined);
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  /** Hay al menos una condición completa: cambia el texto del estado vacío. */
  readonly filtered = input(false);

  readonly reload = output<void>();

  private readonly expanded = signal(false);

  readonly visibleMembers = computed(() => {
    const all = this.members()?.alumnos ?? [];
    return this.expanded() ? all : all.slice(0, VISIBLE_MEMBERS);
  });

  readonly hiddenCount = computed(() => {
    const total = this.members()?.alumnos.length ?? 0;
    return this.expanded() ? 0 : Math.max(0, total - VISIBLE_MEMBERS);
  });

  expand(): void {
    this.expanded.set(true);
  }

  countLabel(total: number): string {
    return total === 1 ? $localize`alumno cumple este filtro` : $localize`alumnos cumplen este filtro`;
  }

  emptyLabel(): string {
    return this.filtered()
      ? $localize`Ningún alumno cumple este filtro. Quizá sea demasiado estricto.`
      : $localize`Añade una condición para ver quién entra en el grupo.`;
  }

  moreLabel(hidden: number): string {
    return $localize`+ ${hidden}:ocultos: más`;
  }

  initials(name: string): string {
    return name
      .split(' ')
      .filter((part) => part.length > 0)
      .slice(0, 2)
      .map((part) => part[0].toUpperCase())
      .join('');
  }
}
