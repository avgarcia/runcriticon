import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogFooter, HlmDialogHeader, HlmDialogTitle } from '@spartan-ng/helm/dialog';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { firstValueFrom } from 'rxjs';
import { messageForError } from '../../../core/api/error-codes';
import { MyPlanService, MyResolvedSession } from '../../../core/my-plan.service';
import { sessionTypeLabel } from '../../planificacion/session-types';
import { formatRelativeShortEs } from '../date-format-es';
import { NOT_DONE_REASONS, NotDoneReason } from '../not-done-reasons';

type ReportStatus = 'HECHO' | 'PARCIAL' | 'NO_HECHO';

/** RPE simplificado de 5 niveles (spec 07 `region:effort`): 1 peor, 5 mejor — mismo sentido que las
 * tres maquetas hi-fi (el spec 08 lo invierte en dos sitios, anotado como deuda para LAL-116). */
const RATING_SCALE: { value: number; emoji: string; label: string }[] = [
  { value: 1, emoji: '😩', label: $localize`Muy mal` },
  { value: 2, emoji: '😕', label: $localize`Mal` },
  { value: 3, emoji: '😐', label: $localize`Normal` },
  { value: 4, emoji: '🙂', label: $localize`Bien` },
  { value: 5, emoji: '💪', label: $localize`Genial` },
];

/** Datos que necesita el diálogo: el día que se reporta, la sesión resuelta ese día (para el
 * contexto no editable y el reporte ya existente, si lo hay). */
export interface ReportDialogData {
  readonly day: string;
  readonly session: MyResolvedSession;
}

/**
 * Reporte de sesión del alumno (LAL-30): estado, valoración 1-5 (si `HECHO`/`PARCIAL`), motivo (si
 * `NO_HECHO`) y notas — side sheet / modal sobre `/mi-plan` (decisión explícita del usuario, no la
 * pantalla aparte que documenta el wireframe de referencia).
 *
 * Sin campo de dolor independiente: `marcaDolor` la activa el backend solo al elegir `MOLESTIAS`
 * como motivo (glosario §Seguimiento) — no hay checkbox ni textarea de descripción del dolor en
 * este formulario, ambos fuera de alcance (ver README del módulo backend).
 *
 * Mismo patrón que `SessionEditorDialogComponent`: el diálogo llama al backend directamente, para
 * pintar el error de validación sin cerrar el diálogo ni perder lo tecleado.
 */
@Component({
  selector: 'rc-report-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, HlmButton, HlmDialogHeader, HlmDialogTitle, HlmDialogFooter, HlmSpinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div hlmDialogHeader>
      <h2 hlmDialogTitle i18n>Reportar sesión</h2>
      <p class="text-sm text-muted-foreground">{{ sessionLabel }} · {{ data.day }}</p>
    </div>

    <form
      [formGroup]="form"
      id="report-form"
      (ngSubmit)="submit()"
      class="flex min-w-96 max-w-lg flex-col gap-4 pt-2"
    >
      @if (data.session.reporte; as existing) {
        <p class="rounded-lg bg-muted px-3 py-2 text-xs text-muted-foreground" i18n>
          Editando reporte enviado {{ relativeSentAt(existing.reportadoEn) }}
        </p>
      }

      <section>
        <p class="mb-2 text-sm font-medium" i18n>¿Cómo ha ido?</p>
        <div class="flex flex-col gap-2">
          <button
            type="button"
            class="flex items-center gap-2 rounded-lg border px-3 py-2 text-left text-sm font-medium transition-colors"
            [class.border-primary]="status() === 'HECHO'"
            [class.bg-primary-soft]="status() === 'HECHO'"
            [class.border-border]="status() !== 'HECHO'"
            (click)="selectStatus('HECHO')"
          >
            <span aria-hidden="true">✓</span>
            <span i18n>Hecho (tal cual)</span>
          </button>
          <button
            type="button"
            class="flex items-center gap-2 rounded-lg border px-3 py-2 text-left text-sm font-medium transition-colors"
            [class.border-primary]="status() === 'PARCIAL'"
            [class.bg-primary-soft]="status() === 'PARCIAL'"
            [class.border-border]="status() !== 'PARCIAL'"
            (click)="selectStatus('PARCIAL')"
          >
            <span aria-hidden="true">⚡</span>
            <span i18n>Parcial (no completo)</span>
          </button>
          <button
            type="button"
            class="flex items-center gap-2 rounded-lg border px-3 py-2 text-left text-sm font-medium transition-colors"
            [class.border-primary]="status() === 'NO_HECHO'"
            [class.bg-primary-soft]="status() === 'NO_HECHO'"
            [class.border-border]="status() !== 'NO_HECHO'"
            (click)="selectStatus('NO_HECHO')"
          >
            <span aria-hidden="true">✗</span>
            <span i18n>No hecho</span>
          </button>
        </div>
      </section>

      @if (status() === 'HECHO' || status() === 'PARCIAL') {
        <section>
          <p class="mb-2 text-sm font-medium" i18n>Cómo te has sentido</p>
          <div class="flex justify-between" role="radiogroup" aria-label="Cómo te has sentido" i18n-aria-label>
            @for (level of ratingScale; track level.value) {
              <button
                type="button"
                role="radio"
                [attr.aria-checked]="rating() === level.value"
                [attr.title]="level.label"
                class="flex flex-col items-center gap-1 rounded-lg px-2 py-1 text-2xl transition-transform"
                [class.scale-125]="rating() === level.value"
                (click)="rating.set(level.value)"
              >
                <span aria-hidden="true">{{ level.emoji }}</span>
                <span class="text-[11px] text-muted-foreground">{{ level.value }}</span>
              </button>
            }
          </div>
        </section>
      }

      @if (status() === 'NO_HECHO') {
        <section>
          <p class="mb-2 text-sm font-medium" i18n>¿Por qué?</p>
          <div class="flex flex-wrap gap-2">
            @for (r of reasons; track r.value) {
              <button
                type="button"
                class="rounded-full border px-3 py-1.5 text-sm font-medium transition-colors"
                [class.border-primary]="reason() === r.value"
                [class.bg-primary]="reason() === r.value"
                [class.text-primary-foreground]="reason() === r.value"
                [class.border-border]="reason() !== r.value"
                (click)="reason.set(r.value)"
              >
                {{ r.label }}
              </button>
            }
          </div>
        </section>
      }

      <div class="flex flex-col gap-1.5">
        <label for="notes" class="text-sm font-medium" i18n>Notas para tu entrenador (opcional)</label>
        <textarea
          id="notes"
          formControlName="notes"
          rows="3"
          maxlength="1000"
          placeholder="Algo que quieras contarle a tu entrenador..."
          i18n-placeholder
          class="dark:bg-input/30 border-input focus-visible:border-ring focus-visible:ring-ring/20 min-h-20 w-full rounded-lg border bg-card px-3 py-2 text-base shadow-xs outline-none placeholder:text-muted-foreground md:text-sm"
        ></textarea>
      </div>

      @if (errorMessage()) {
        <p class="text-sm text-danger" role="alert">{{ errorMessage() }}</p>
      }
    </form>

    <div hlmDialogFooter>
      <button hlmBtn variant="outline" type="button" (click)="close()" i18n>Cancelar</button>
      <button hlmBtn type="submit" form="report-form" [disabled]="!canSubmit() || saving()">
        @if (saving()) {
          <hlm-spinner aria-label="Guardando" i18n-aria-label />
        }
        @if (data.session.reporte) {
          <span i18n>Actualizar</span>
        } @else {
          <span i18n>Enviar</span>
        }
      </button>
    </div>
  `,
})
export class ReportDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly myPlanService = inject(MyPlanService);
  private readonly dialogRef = inject(BrnDialogRef<boolean>);

  readonly data = injectBrnDialogContext<ReportDialogData>();

  readonly ratingScale = RATING_SCALE;
  readonly reasons = NOT_DONE_REASONS;
  readonly sessionLabel = sessionTypeLabel(this.data.session.tipo);

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly status = signal<ReportStatus | null>((this.data.session.reporte?.estado as ReportStatus) ?? null);
  readonly rating = signal<number | null>(this.data.session.reporte?.valoracion ?? null);
  readonly reason = signal<NotDoneReason | null>(
    (this.data.session.reporte?.motivo as NotDoneReason | undefined) ?? null,
  );

  readonly form = this.fb.nonNullable.group({
    notes: [this.data.session.reporte?.notas ?? '', [Validators.maxLength(1000)]],
  });

  /** Obligatorio: un estado, más valoración si Hecho/Parcial o motivo si No hecho — mismo invariante
   * que `SessionReport.create` en el backend, replicado aquí solo para habilitar el botón. */
  canSubmit(): boolean {
    const status = this.status();
    if (!status || !this.form.valid) return false;
    if (status === 'NO_HECHO') return this.reason() !== null;
    return this.rating() !== null;
  }

  selectStatus(status: ReportStatus): void {
    this.status.set(status);
    if (status === 'NO_HECHO') {
      this.rating.set(null);
    } else {
      this.reason.set(null);
    }
  }

  relativeSentAt(reportadoEn: string): string {
    return formatRelativeShortEs(reportadoEn);
  }

  async submit(): Promise<void> {
    const status = this.status();
    if (!status || !this.canSubmit()) return;
    this.saving.set(true);
    this.errorMessage.set(null);
    const notes = this.form.getRawValue().notes.trim();
    try {
      await firstValueFrom(
        this.myPlanService.submitReport(this.data.day, {
          estado: status,
          valoracion: status === 'NO_HECHO' ? undefined : (this.rating() ?? undefined),
          motivo: status === 'NO_HECHO' ? (this.reason() ?? undefined) : undefined,
          notas: notes ? notes : undefined,
        }),
      );
      this.dialogRef.close(true);
    } catch (err) {
      this.handleError(err);
    }
  }

  close(): void {
    this.dialogRef.close(false);
  }

  private handleError(err: unknown): void {
    this.saving.set(false);
    // El 403 ya lo avisa el interceptor global con su toast; no duplicamos el mensaje.
    if (err instanceof HttpErrorResponse && err.status === 403) return;
    this.errorMessage.set(messageForError(err));
  }
}
