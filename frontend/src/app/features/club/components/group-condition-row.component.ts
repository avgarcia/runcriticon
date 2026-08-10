import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { HlmBadge } from '@spartan-ng/helm/badge';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmSelectImports } from '@spartan-ng/helm/select';
import { TagKey, TagValue } from '../../../core/taxonomy.service';

/** Una condición del filtro: un eje de la taxonomía y el valor que se exige en él. */
export interface GroupCondition {
  readonly tagId: string;
  readonly valueId: string | null;
}

/**
 * Una fila del constructor de filtros: `[eje ▾] incluye [valor ×]`.
 *
 * **Un solo valor por eje, y elegir otro sustituye al anterior.** El filtro solo sabe hacer «y»:
 * pertenece al grupo quien tenga *todos* los valores exigidos. Dos valores del mismo eje se leerían
 * como «medio-alto o alto» —que es lo que insinúa la maqueta— pero significarían «los dos a la vez»,
 * y ningún alumno los tiene. Por eso también se oculta un eje que otra condición ya está usando.
 * Cuando el filtro sepa expresar «o», esta restricción se levanta.
 *
 * Solo se ofrecen valores **asignables**: ni archivados, ni vivos colgando de un eje archivado. Ese
 * segundo caso el servidor lo rechaza con un conflicto que quien está delante no podría explicarse.
 */
@Component({
  selector: 'rc-group-condition-row',
  standalone: true,
  imports: [HlmBadge, HlmButton, ...HlmSelectImports],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="flex flex-wrap items-center gap-2.5 rounded-lg border border-border bg-muted px-3 py-2.5"
      role="group"
      [attr.aria-label]="rowLabel()"
    >
      <!-- La etiqueta va oculta a la vista pero asociada al botón del desplegable: un combobox no
           toma su nombre del contenido, así que sin ella se anunciaría sin decir de qué es.
           El traductor de valor a rótulo es lo que evita, además, que enseñe el id del eje. -->
      <label class="sr-only" [for]="axisTriggerId()" i18n>Eje de la condición</label>
      <hlm-select
        class="min-w-[150px]"
        [value]="condition().tagId"
        [itemToString]="axisToString"
        (valueChange)="changeAxis($event)"
      >
        <hlm-select-trigger [buttonId]="axisTriggerId()">
          <hlm-select-value placeholder="Elige un eje" i18n-placeholder />
        </hlm-select-trigger>
        <hlm-select-content label="Ejes disponibles" i18n-label>
          @for (axis of selectableAxes(); track axis.id) {
            <hlm-select-item [value]="axis.id">{{ axis.nombre }}</hlm-select-item>
          }
        </hlm-select-content>
      </hlm-select>

      <!-- El gris apagado sobre el tinte de la fila y a este tamaño se queda por debajo del
           contraste que exige AA; el conector va en el color de texto normal. -->
      <span class="text-xs text-foreground" i18n>es</span>

      @if (selectedValue(); as value) {
        <span hlmBadge>
          {{ value.valor }}
          <button
            type="button"
            class="ml-1 cursor-pointer"
            (click)="clearValue()"
            [attr.aria-label]="removeValueLabel(value.valor)"
          >
            ×
          </button>
        </span>
      }

      <label class="sr-only" [for]="valueTriggerId()" i18n>Valor de la condición</label>
      <hlm-select
        class="min-w-[170px]"
        [value]="condition().valueId ?? ''"
        [itemToString]="valueToString"
        (valueChange)="changeValue($event)"
      >
        <hlm-select-trigger [buttonId]="valueTriggerId()">
          <hlm-select-value placeholder="Elige un valor" i18n-placeholder />
        </hlm-select-trigger>
        <hlm-select-content label="Valores del eje" i18n-label>
          @for (value of assignableValues(); track value.id) {
            <hlm-select-item [value]="value.id">{{ value.valor }}</hlm-select-item>
          }
        </hlm-select-content>
      </hlm-select>

      <button
        hlmBtn
        variant="ghost"
        size="sm"
        type="button"
        class="ml-auto"
        (click)="remove.emit()"
        [attr.aria-label]="removeConditionLabel()"
      >
        ✕
      </button>
    </div>

    @if (!condition().valueId) {
      <p class="mt-1 text-xs text-muted-foreground" i18n>
        Elige un valor: mientras esté vacía, esta condición no cuenta.
      </p>
    }
  `,
})
export class GroupConditionRowComponent {
  readonly condition = input.required<GroupCondition>();
  /** Ejes con al menos un valor asignable. */
  readonly axes = input.required<readonly TagKey[]>();
  /** Ejes que ya usa otra condición; no se ofrecen aquí. */
  readonly takenTagIds = input<readonly string[]>([]);

  readonly conditionChange = output<GroupCondition>();
  readonly remove = output<void>();

  readonly selectableAxes = computed(() =>
    this.axes().filter(
      (axis) => axis.id === this.condition().tagId || !this.takenTagIds().includes(axis.id),
    ),
  );

  readonly assignableValues = computed<readonly TagValue[]>(
    () => this.axes().find((axis) => axis.id === this.condition().tagId)?.valores ?? [],
  );

  readonly selectedValue = computed<TagValue | undefined>(() =>
    this.assignableValues().find((value) => value.id === this.condition().valueId),
  );

  /**
   * Cambiar de eje deja la condición sin valor: los de un eje no valen para otro.
   *
   * El desplegable emite además `null` al vaciarse, así que la firma acepta lo que de verdad llega.
   */
  changeAxis(tagId: string | null | undefined): void {
    if (!tagId || tagId === this.condition().tagId) return;
    this.conditionChange.emit({ tagId, valueId: null });
  }

  changeValue(valueId: string | null | undefined): void {
    if (!valueId) return;
    this.conditionChange.emit({ tagId: this.condition().tagId, valueId });
  }

  clearValue(): void {
    this.conditionChange.emit({ tagId: this.condition().tagId, valueId: null });
  }

  /** Un eje no se repite entre condiciones, así que sirve para dar identificadores únicos a la fila. */
  readonly axisTriggerId = computed(() => `condicion-eje-${this.condition().tagId}`);
  readonly valueTriggerId = computed(() => `condicion-valor-${this.condition().tagId}`);

  /** Del identificador del eje al rótulo que se lee en el desplegable. */
  readonly axisToString = (tagId: unknown): string =>
    this.axes().find((axis) => axis.id === tagId)?.nombre ?? '';

  readonly valueToString = (valueId: unknown): string =>
    this.assignableValues().find((value) => value.id === valueId)?.valor ?? '';

  rowLabel(): string {
    const axis = this.axes().find((item) => item.id === this.condition().tagId);
    return axis ? $localize`Condición sobre ${axis.nombre}:eje:` : $localize`Condición`;
  }

  removeValueLabel(nombre: string): string {
    return $localize`Quitar ${nombre}:valor: de la condición`;
  }

  removeConditionLabel(): string {
    return $localize`Quitar condición`;
  }
}
