import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const LABELS = ['Muy débil', 'Débil', 'Aceptable', 'Buena', 'Excelente'] as const;
const COLORS = [
  'var(--mat-sys-error)',
  'var(--mat-sys-error)',
  'var(--rc-strength-medium)',
  'var(--rc-strength-good)',
];

/**
 * Medidor visual de fortaleza de contraseña (barras + checklist) para las pantallas de identidad y
 * acceso. Es únicamente feedback de UX en cliente — la política real (ADR-0003 D6) la valida el
 * backend; este componente no la sustituye ni la duplica.
 */
@Component({
  selector: 'rc-password-strength',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (password().length > 0) {
      <div class="password-strength">
        <div class="password-strength__bars">
          @for (i of [0, 1, 2, 3]; track i) {
            <div
              class="password-strength__bar"
              [style.background]="i < strength() ? color() : null"
            ></div>
          }
        </div>
        <div class="password-strength__label" [style.color]="color()">{{ label() }}</div>
        <ul class="password-strength__rules">
          <li [class.password-strength__rule--ok]="lengthOk()">
            <span aria-hidden="true">{{ lengthOk() ? '✓' : '○' }}</span> Al menos 12 caracteres
          </li>
          <li [class.password-strength__rule--ok]="matchOk()">
            <span aria-hidden="true">{{ matchOk() ? '✓' : '○' }}</span> Las contraseñas coinciden
          </li>
        </ul>
      </div>
    }
  `,
  styles: [
    `
      .password-strength {
        display: flex;
        flex-direction: column;
        gap: 0.4rem;
        margin: -0.25rem 0 0.25rem;
      }
      .password-strength__bars {
        display: flex;
        gap: 0.25rem;
      }
      .password-strength__bar {
        flex: 1;
        height: 4px;
        border-radius: 2px;
        background: var(--mat-sys-surface-variant);
      }
      .password-strength__label {
        font-size: 0.72rem;
        font-weight: 600;
      }
      .password-strength__rules {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.15rem;
        font-size: 0.72rem;
        color: var(--mat-sys-on-surface-variant);
      }
      .password-strength__rule--ok {
        color: var(--rc-strength-good);
      }
    `,
  ],
})
export class PasswordStrengthComponent {
  readonly password = input('');
  readonly confirm = input('');

  readonly strength = computed(() => {
    const p = this.password();
    let s = 0;
    if (p.length >= 8) s++;
    if (p.length >= 12) s++;
    if (/[0-9]/.test(p) && /[a-z]/.test(p)) s++;
    if (/[^a-z0-9]/i.test(p) || p.length >= 16) s++;
    return Math.min(s, 4);
  });

  readonly label = computed(() => LABELS[this.strength()]);
  readonly color = computed(() => COLORS[Math.max(this.strength() - 1, 0)]);
  readonly lengthOk = computed(() => this.password().length >= 12);
  readonly matchOk = computed(() => this.confirm().length > 0 && this.password() === this.confirm());
}
