import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogFooter, HlmDialogHeader, HlmDialogTitle } from '@spartan-ng/helm/dialog';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { firstValueFrom } from 'rxjs';
import { messageForError } from '../../../core/api/error-codes';
import { MarkDistance, MyMarksService } from '../../../core/my-marks.service';
import { partsToSeconds, secondsToParts } from '../mark-time';

/** Datos que necesita el diálogo: la distancia, su etiqueta corta para el título, y el tiempo ya
 * registrado (en segundos) si lo había — `null` para el estado "+ Añadir". */
export interface MarkDialogData {
  readonly distance: MarkDistance;
  readonly label: string;
  readonly existingSeconds: number | null;
}

/**
 * Registrar o editar la propia marca de una distancia (LAL-31): un único modal para "+ Añadir" y
 * "✎ Editar", igual que en el wireframe `mis-marcas.html`. Mismo patrón que `ReportDialogComponent`:
 * el diálogo llama al backend directamente, para pintar el error sin cerrar el diálogo ni perder lo
 * tecleado.
 *
 * **Campo de horas añadido al wireframe** (decisión explícita, ver plan): el modal de referencia solo
 * tiene min/seg, pero `tiempoSegundos` no tiene tope y 21K/42K reales suelen superar la hora — sin este
 * campo esas dos distancias quedarían rotas el primer día. Se muestra siempre, con valor por defecto 0.
 */
@Component({
  selector: 'rc-mark-dialog',
  standalone: true,
  imports: [FormsModule, HlmButton, HlmDialogHeader, HlmDialogTitle, HlmDialogFooter, HlmSpinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div hlmDialogHeader>
      <p class="text-xs font-semibold uppercase tracking-wide text-muted-foreground" i18n>Tu marca de</p>
      <h2 hlmDialogTitle>{{ data.label }}</h2>
    </div>

    <form id="mark-form" (ngSubmit)="submit()" class="flex flex-col items-center gap-2 py-2">
      <div class="flex items-center justify-center gap-1.5">
        <input
          class="h-16 w-[76px] rounded-xl border border-input bg-card text-center text-3xl font-semibold tabular-nums text-foreground focus-visible:border-ring focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/20"
          type="number"
          min="0"
          [(ngModel)]="hours"
          name="hours"
          aria-label="horas"
          i18n-aria-label
        />
        <span class="text-2xl font-semibold text-muted-foreground">:</span>
        <input
          class="h-16 w-[76px] rounded-xl border border-input bg-card text-center text-3xl font-semibold tabular-nums text-foreground focus-visible:border-ring focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/20"
          type="number"
          min="0"
          max="59"
          [(ngModel)]="minutes"
          name="minutes"
          aria-label="minutos"
          i18n-aria-label
        />
        <span class="text-2xl font-semibold text-muted-foreground">:</span>
        <input
          class="h-16 w-[76px] rounded-xl border border-input bg-card text-center text-3xl font-semibold tabular-nums text-foreground focus-visible:border-ring focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/20"
          type="number"
          min="0"
          max="59"
          [(ngModel)]="seconds"
          name="seconds"
          aria-label="segundos"
          i18n-aria-label
        />
      </div>
      <div class="flex justify-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
        <span class="w-[76px] text-center" i18n>h</span>
        <span class="w-4"></span>
        <span class="w-[76px] text-center" i18n>min</span>
        <span class="w-4"></span>
        <span class="w-[76px] text-center" i18n>seg</span>
      </div>

      @if (errorMessage()) {
        <p class="text-sm text-danger" role="alert">{{ errorMessage() }}</p>
      }
    </form>

    <div hlmDialogFooter class="justify-between">
      @if (data.existingSeconds !== null) {
        <button hlmBtn variant="ghost" type="button" class="text-danger" (click)="withdraw()" i18n>
          Borrar marca
        </button>
      } @else {
        <span></span>
      }
      <div class="flex gap-2">
        <button hlmBtn variant="outline" type="button" (click)="close()" i18n>Cancelar</button>
        <button hlmBtn type="submit" form="mark-form" [disabled]="!canSubmit() || saving()">
          @if (saving()) {
            <hlm-spinner aria-label="Guardando" i18n-aria-label />
          }
          <span i18n>Guardar</span>
        </button>
      </div>
    </div>
  `,
})
export class MarkDialogComponent {
  private readonly myMarksService = inject(MyMarksService);
  private readonly dialogRef = inject(BrnDialogRef<boolean>);

  readonly data = injectBrnDialogContext<MarkDialogData>();

  private readonly initial = secondsToParts(this.data.existingSeconds ?? 0);
  hours = this.initial.hours;
  minutes = this.initial.minutes;
  seconds = this.initial.seconds;

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  canSubmit(): boolean {
    return partsToSeconds({ hours: this.hours, minutes: this.minutes, seconds: this.seconds }) > 0;
  }

  async submit(): Promise<void> {
    if (!this.canSubmit()) return;
    const totalSeconds = partsToSeconds({ hours: this.hours, minutes: this.minutes, seconds: this.seconds });
    this.saving.set(true);
    this.errorMessage.set(null);
    try {
      await firstValueFrom(this.myMarksService.recordMark(this.data.distance, totalSeconds));
      this.dialogRef.close(true);
    } catch (err) {
      this.saving.set(false);
      this.errorMessage.set(messageForError(err));
    }
  }

  async withdraw(): Promise<void> {
    this.saving.set(true);
    this.errorMessage.set(null);
    try {
      await firstValueFrom(this.myMarksService.withdrawMark(this.data.distance));
      this.dialogRef.close(true);
    } catch (err) {
      this.saving.set(false);
      this.errorMessage.set(messageForError(err));
    }
  }

  close(): void {
    this.dialogRef.close(false);
  }
}
