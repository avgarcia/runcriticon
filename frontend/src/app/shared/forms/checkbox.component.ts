import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/**
 * Casilla de verificación accesible, a mano (sin helm): `@spartan-ng/helm/checkbox` no está copiado
 * en el proyecto todavía y el presupuesto de bundle inicial va muy justo (LAL-113 abierto para
 * adelgazarlo) — copiar un helm nuevo lo empeoraría por las variantes Tailwind que arrastra en
 * `styles.css`. Un `<input type="checkbox">` nativo con `<label>` asociado por `for`/`id` da foco,
 * navegación por teclado y accesible-name gratis, mejor punto de partida para axe-core que un
 * `div[role=checkbox]` hecho a mano.
 *
 * Sin `ControlValueAccessor`: se usa en formularios ([[ActivateComponent]]) con un `signal<boolean>`
 * propio en vez de un `FormControl` — mismo criterio que `selectedType`/`volumeType` en
 * `session-editor-dialog.component.ts` para selecciones que no son texto/número — y en la cabecera de
 * una tabla con selección parcial, donde no hay ningún formulario detrás. Si aparece un consumidor que
 * sí necesite integrarse con un `FormGroup`, añadir `ControlValueAccessor` entonces.
 *
 * `indeterminate` es una propiedad del IDL del `<input>` nativo, no un atributo — de ahí el binding de
 * propiedad `[indeterminate]` y no `[attr.aria-checked]="'mixed'"`: el navegador ya expone el estado
 * "mixed" al árbol de accesibilidad a partir de esa propiedad, y duplicarlo con un `aria-checked`
 * manual sería redundante.
 */
@Component({
  selector: 'rc-checkbox',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-start gap-2.5">
      <input
        type="checkbox"
        [id]="inputId()"
        [checked]="checked()"
        [indeterminate]="indeterminate()"
        [attr.aria-describedby]="describedBy() || null"
        (change)="onChange($event)"
        class="mt-0.5 size-4 shrink-0 cursor-pointer rounded border-border text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
      />
      <label [for]="inputId()" class="cursor-pointer text-[13px] leading-relaxed text-foreground">
        <ng-content />
      </label>
    </div>
  `,
})
export class CheckboxComponent {
  readonly inputId = input.required<string>();
  readonly checked = input<boolean>(false);
  readonly indeterminate = input<boolean>(false);
  readonly describedBy = input<string | undefined>(undefined);
  readonly checkedChange = output<boolean>();

  onChange(event: Event): void {
    this.checkedChange.emit((event.target as HTMLInputElement).checked);
  }
}
