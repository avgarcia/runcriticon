import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/**
 * Casilla de verificación accesible, a mano (sin helm): `@spartan-ng/helm/checkbox` no está copiado
 * en el proyecto todavía y el presupuesto de bundle inicial va muy justo (LAL-113 abierto para
 * adelgazarlo) — copiar un helm nuevo lo empeoraría por las variantes Tailwind que arrastra en
 * `styles.css`. Un `<input type="checkbox">` nativo con `<label>` asociado por `for`/`id` da foco,
 * navegación por teclado y accesible-name gratis, mejor punto de partida para axe-core que un
 * `div[role=checkbox]` hecho a mano.
 *
 * Sin `ControlValueAccessor`: por ahora se usa en un único formulario ([[ActivateComponent]]) con un
 * `signal<boolean>` propio en vez de un `FormControl` — mismo criterio que `selectedType`/`volumeType`
 * en `session-editor-dialog.component.ts` para selecciones que no son texto/número. Si aparece un
 * segundo consumidor que sí necesite integrarse con un `FormGroup`, añadir `ControlValueAccessor`
 * entonces.
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
  readonly describedBy = input<string | undefined>(undefined);
  readonly checkedChange = output<boolean>();

  onChange(event: Event): void {
    this.checkedChange.emit((event.target as HTMLInputElement).checked);
  }
}
