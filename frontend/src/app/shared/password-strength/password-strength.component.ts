import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';

type ZxcvbnFn = (password: string) => { score: number };

const LABELS = ['Muy débil', 'Débil', 'Aceptable', 'Buena', 'Excelente'] as const;
const BAR_COLORS = [
  'bg-red-600',
  'bg-red-600',
  'bg-amber-600',
  'bg-lime-600',
  'bg-green-600',
] as const;
const TEXT_COLORS = [
  'text-red-600',
  'text-red-600',
  'text-amber-600',
  'text-lime-600',
  'text-green-600',
] as const;

/**
 * Medidor de fortaleza de contraseña (ADR-0003 D6: zxcvbn como sugerencia, no bloqueo; la política
 * real la valida el backend). zxcvbn y sus diccionarios en español se cargan con import() dinámico
 * al primer uso: quedan en un chunk propio fuera del bundle inicial (nota en ADR-0012).
 */
@Component({
  selector: 'rc-password-strength',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (password().length > 0) {
      <div class="flex flex-col gap-2" aria-live="polite">
        <div class="flex gap-1" aria-hidden="true">
          @for (bar of bars; track bar) {
            <div
              class="h-[5px] flex-1 rounded-[3px]"
              [class]="bar < filledBars() ? barColor() : 'bg-border'"
            ></div>
          }
        </div>
        <p class="text-[11.5px] font-medium" [class]="textColor()">{{ label() }}</p>
        <ul class="m-0 flex list-none flex-col gap-[3px] p-0">
          <li
            class="flex items-center gap-1.5 text-[11.5px]"
            [class]="lengthOk() ? 'text-green-600' : 'text-muted-foreground'"
          >
            <span class="font-bold" aria-hidden="true">{{ lengthOk() ? '✓' : '○' }}</span>
            Al menos 12 caracteres
          </li>
          <li
            class="flex items-center gap-1.5 text-[11.5px]"
            [class]="matchOk() ? 'text-green-600' : 'text-muted-foreground'"
          >
            <span class="font-bold" aria-hidden="true">{{ matchOk() ? '✓' : '○' }}</span>
            Las contraseñas coinciden
          </li>
        </ul>
      </div>
    }
  `,
})
export class PasswordStrengthComponent {
  readonly password = input.required<string>();
  readonly confirm = input('');

  protected readonly bars = [0, 1, 2, 3];

  /** Puntuación zxcvbn 0-4; 0 mientras carga el chunk o con contraseña vacía. */
  readonly score = signal(0);

  readonly lengthOk = computed(() => this.password().length >= 12);
  readonly matchOk = computed(
    () => this.confirm().length > 0 && this.confirm() === this.password(),
  );
  readonly filledBars = computed(() =>
    this.password().length > 0 ? Math.max(this.score(), 1) : 0,
  );
  readonly label = computed(() => LABELS[this.score()]);
  readonly barColor = computed(() => BAR_COLORS[this.score()]);
  readonly textColor = computed(() => TEXT_COLORS[this.score()]);

  private zxcvbn: ZxcvbnFn | null = null;

  constructor() {
    effect(() => {
      const pw = this.password();
      if (!pw) {
        this.score.set(0);
        return;
      }
      void this.evaluate(pw);
    });
  }

  private async evaluate(pw: string): Promise<void> {
    const zxcvbn = await this.loadZxcvbn();
    // Descarta respuestas obsoletas si el usuario siguió tecleando durante la carga.
    if (pw !== this.password()) {
      return;
    }
    this.score.set(zxcvbn(pw).score);
  }

  private async loadZxcvbn(): Promise<ZxcvbnFn> {
    if (this.zxcvbn) {
      return this.zxcvbn;
    }
    const [{ ZxcvbnFactory }, common, es] = await Promise.all([
      import('@zxcvbn-ts/core'),
      import('@zxcvbn-ts/language-common'),
      import('@zxcvbn-ts/language-es-es'),
    ]);
    const factory = new ZxcvbnFactory({
      translations: es.translations,
      graphs: common.adjacencyGraphs,
      dictionary: { ...common.dictionary, ...es.dictionary },
    });
    this.zxcvbn = (p: string) => factory.check(p);
    return this.zxcvbn;
  }
}
