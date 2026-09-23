import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogFooter, HlmDialogHeader, HlmDialogTitle } from '@spartan-ng/helm/dialog';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { firstValueFrom } from 'rxjs';
import { messageForError } from '../../../core/api/error-codes';
import { MyPlanService, MyResolvedSession } from '../../../core/my-plan.service';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { sessionTypeLabel } from '../../planificacion/session-types';
import { ADJUSTMENT_REASONS, AdjustmentReason } from '../adjustment-reasons';
import { DaySlot } from '../pages/my-week.component';
import { todayIsoDate } from '../date-format-es';

type AdjustmentAction = 'MOVIDA' | 'SALTADA';
type ConflictResolution = 'REEMPLAZAR' | 'INTERCAMBIAR';

/** Datos que necesita el diálogo: el día EFECTIVO de la sesión de origen, la sesión resuelta ese
 * día (para el contexto no editable), y los días de la semana ya cargada (para construir el
 * selector de destino y detectar conflictos sin una segunda ida a la API — reajuste de día por el
 * alumno, imprevistos). */
export interface RescheduleDialogData {
  readonly day: string;
  readonly session: MyResolvedSession;
  readonly days: DaySlot[];
}

/**
 * Reajuste de día del alumno: mueve la sesión de origen a otro día de la semana visible
 * (hasta 7 días vista, wireframe 07 §Flujo B) o la marca como saltada, con motivo. Construido desde
 * el lo-fi de `docs/wireframes/07-student-report.md` — sin maqueta hi-fi todavía, desviación
 * consciente de `frontend/CLAUDE.md` documentada en la PR del reajuste de día por el alumno
 * (imprevistos).
 *
 * El selector de día destino se limita a los días ya cargados en la tira semanal de `MyWeekComponent`
 * (`data.days`): evita una segunda petición y un helm nuevo de calendario, a costa de no ofrecer
 * destinos de la semana siguiente aunque el backend los acepte — simplificación documentada.
 *
 * Mismo patrón que `ReportDialogComponent`: el diálogo llama al backend directamente, para pintar el
 * error de validación sin cerrar el diálogo ni perder lo tecleado. El conflicto de día ocupado
 * (wireframe: "Ese día tiene [Series]. ¿Reemplazar / Intercambiar / Cancelar?") se resuelve leyendo
 * `data.days` en el propio cliente, sin esperar al 409 del backend — el backend lo revalida igual.
 *
 * **"🤕 Avisar de lesión"** es su propia tarjeta, no una píldora más de motivo: fija
 * `accion`/`reason` a `SALTADA`/`LESION` y abre `ConfirmDialogComponent` para decidir si además
 * cambia el tag `estado` del alumno — ver {@link selectInjury}.
 */
@Component({
  selector: 'rc-reschedule-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, HlmButton, HlmDialogHeader, HlmDialogTitle, HlmDialogFooter, HlmSpinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div hlmDialogHeader>
      <h2 hlmDialogTitle i18n>Reajustar día</h2>
      <p class="text-sm text-muted-foreground">{{ sessionLabel }} · {{ data.day }}</p>
    </div>

    <form
      [formGroup]="form"
      id="reschedule-form"
      (ngSubmit)="submit()"
      class="flex min-w-96 max-w-lg flex-col gap-4 pt-2"
    >
      <section>
        <p class="mb-2 text-sm font-medium" i18n>¿Qué quieres hacer?</p>
        <div class="flex flex-col gap-2">
          <button
            type="button"
            class="flex items-center gap-2 rounded-lg border px-3 py-2 text-left text-sm font-medium transition-colors"
            [class.border-primary]="action() === 'MOVIDA'"
            [class.bg-primary-soft]="action() === 'MOVIDA'"
            [class.border-border]="action() !== 'MOVIDA'"
            (click)="selectAction('MOVIDA')"
          >
            <span aria-hidden="true">📅</span>
            <span i18n>Mover a otro día</span>
          </button>
          <button
            type="button"
            class="flex items-center gap-2 rounded-lg border px-3 py-2 text-left text-sm font-medium transition-colors"
            [class.border-primary]="action() === 'SALTADA' && reason() !== 'LESION'"
            [class.bg-primary-soft]="action() === 'SALTADA' && reason() !== 'LESION'"
            [class.border-border]="!(action() === 'SALTADA' && reason() !== 'LESION')"
            (click)="selectAction('SALTADA')"
          >
            <span aria-hidden="true">✗</span>
            <span i18n>Saltarla (sin recuperar)</span>
          </button>
          <button
            type="button"
            class="flex items-center gap-2 rounded-lg border px-3 py-2 text-left text-sm font-medium transition-colors"
            [class.border-danger]="reason() === 'LESION'"
            [class.bg-danger-soft]="reason() === 'LESION'"
            [class.border-border]="reason() !== 'LESION'"
            (click)="selectInjury()"
          >
            <span aria-hidden="true">🤕</span>
            <span i18n>Avisar de lesión</span>
          </button>
        </div>
      </section>

      @if (action() === 'MOVIDA') {
        <section>
          <p class="mb-2 text-sm font-medium" i18n>¿A qué día?</p>
          <div class="flex flex-wrap gap-2" role="radiogroup" aria-label="Día destino" i18n-aria-label>
            @for (option of targetOptions(); track option.day) {
              <button
                type="button"
                role="radio"
                [attr.aria-checked]="targetDay() === option.day"
                class="rounded-full border px-3 py-1.5 text-sm font-medium transition-colors"
                [class.border-primary]="targetDay() === option.day"
                [class.bg-primary]="targetDay() === option.day"
                [class.text-primary-foreground]="targetDay() === option.day"
                [class.border-border]="targetDay() !== option.day"
                (click)="selectTargetDay(option.day)"
              >
                {{ option.label }} {{ option.day.slice(8) }}
                @if (option.session) {
                  <span class="text-xs" i18n>(ocupado)</span>
                }
              </button>
            }
          </div>

          @if (targetDay() && occupantAt(targetDay()); as occupant) {
            <div class="mt-3 rounded-lg bg-muted p-3 text-sm">
              <p i18n>Ese día ya tiene {{ occupantLabel(occupant) }}. ¿Qué quieres hacer?</p>
              <div class="mt-2 flex flex-col gap-2">
                <button
                  type="button"
                  class="flex items-center gap-2 rounded-lg border px-3 py-2 text-left text-sm font-medium transition-colors"
                  [class.border-primary]="conflictResolution() === 'REEMPLAZAR'"
                  [class.bg-primary-soft]="conflictResolution() === 'REEMPLAZAR'"
                  [class.border-border]="conflictResolution() !== 'REEMPLAZAR'"
                  (click)="conflictResolution.set('REEMPLAZAR')"
                >
                  <span i18n>Reemplazar — la otra sesión pasa a saltada</span>
                </button>
                <button
                  type="button"
                  class="flex items-center gap-2 rounded-lg border px-3 py-2 text-left text-sm font-medium transition-colors"
                  [class.border-primary]="conflictResolution() === 'INTERCAMBIAR'"
                  [class.bg-primary-soft]="conflictResolution() === 'INTERCAMBIAR'"
                  [class.border-border]="conflictResolution() !== 'INTERCAMBIAR'"
                  (click)="conflictResolution.set('INTERCAMBIAR')"
                >
                  <span i18n>Intercambiar los dos días</span>
                </button>
              </div>
            </div>
          }
        </section>
      }

      @if (reason() !== 'LESION') {
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
      } @else {
        <p class="rounded-lg bg-muted p-3 text-sm">
          @if (confirmaCambioEstado()) {
            <span i18n
              >Tu estado pasará a "lesión" y tu entrenador lo verá enseguida. Puedes cambiarlo luego desde tu
              perfil.</span
            >
          } @else {
            <span i18n
              >Avisaremos a tu entrenador ahora mismo. No cambiaremos tu estado visible — puedes hacerlo tú
              cuando quieras.</span
            >
          }
        </p>
      }

      <div class="flex flex-col gap-1.5">
        <label for="message" class="text-sm font-medium" i18n>Cuéntaselo a tu entrenador (opcional)</label>
        <textarea
          id="message"
          formControlName="message"
          rows="2"
          maxlength="1000"
          class="dark:bg-input/30 border-input focus-visible:border-ring focus-visible:ring-ring/20 min-h-16 w-full rounded-lg border bg-card px-3 py-2 text-base shadow-xs outline-none placeholder:text-muted-foreground md:text-sm"
        ></textarea>
      </div>

      @if (errorMessage()) {
        <p class="text-sm text-danger" role="alert">{{ errorMessage() }}</p>
      }
    </form>

    <div hlmDialogFooter>
      <button hlmBtn variant="outline" type="button" (click)="close()" i18n>Cancelar</button>
      <button hlmBtn type="submit" form="reschedule-form" [disabled]="!canSubmit() || saving()">
        @if (saving()) {
          <hlm-spinner aria-label="Guardando" i18n-aria-label />
        }
        <span i18n>Aplicar</span>
      </button>
    </div>
  `,
})
export class RescheduleDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly myPlanService = inject(MyPlanService);
  private readonly dialogService = inject(HlmDialogService);
  private readonly dialogRef = inject(BrnDialogRef<boolean>);

  readonly data = injectBrnDialogContext<RescheduleDialogData>();

  readonly reasons = ADJUSTMENT_REASONS;
  readonly sessionLabel = sessionTypeLabel(this.data.session.tipo);

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly action = signal<AdjustmentAction | null>(null);
  readonly targetDay = signal<string | null>(null);
  readonly conflictResolution = signal<ConflictResolution | null>(null);
  readonly reason = signal<AdjustmentReason | null>(null);

  /** Solo relevante con `reason() === 'LESION'` (aviso de lesión desde el reajuste de día): si el alumno confirmó en el modal que su
   * tag `estado` pase a "lesión". El aviso al entrenador se dispara igual aunque sea `false`. */
  readonly confirmaCambioEstado = signal(false);

  readonly form = this.fb.nonNullable.group({
    message: ['', [Validators.maxLength(1000)]],
  });

  /** Días de la semana ya cargada que se pueden elegir como destino: ni el día de origen, ni un día
   * pasado — mismo invariante que `RescheduleDayCommand.ensureWithinRescheduleWindow` en el backend. */
  readonly targetOptions = computed<DaySlot[]>(() =>
    this.data.days.filter((d) => d.day !== this.data.day && d.day >= todayIsoDate()),
  );

  occupantAt(day: string | null): MyResolvedSession | undefined {
    return day ? this.data.days.find((d) => d.day === day)?.session : undefined;
  }

  occupantLabel(session: MyResolvedSession): string {
    return sessionTypeLabel(session.tipo);
  }

  selectAction(action: AdjustmentAction): void {
    this.action.set(action);
    if (action === 'SALTADA') {
      this.targetDay.set(null);
      this.conflictResolution.set(null);
    }
    // "Avisar de lesión" es su propia tarjeta, no una de estas dos: elegir cualquiera de ellas
    // abandona ese flujo, aunque el motivo ya estuviera fijado en LESION.
    if (this.reason() === 'LESION') {
      this.reason.set(null);
      this.confirmaCambioEstado.set(false);
    }
  }

  selectTargetDay(day: string): void {
    this.targetDay.set(day);
    this.conflictResolution.set(null);
  }

  /** "🤕 Avisar de lesión" (wireframe 07 §Flujo B opción 4): fija `accion` a `SALTADA` — el
   * backend rechaza `LESION` con `MOVIDA` — y abre el modal que decide si además cambia el tag
   * `estado`. El aviso al entrenador y la marca de dolor se disparan siempre, confirme o no. */
  async selectInjury(): Promise<void> {
    this.action.set('SALTADA');
    this.reason.set('LESION');
    this.targetDay.set(null);
    this.conflictResolution.set(null);
    const confirmed = await firstValueFrom(
      this.dialogService.open<boolean>(ConfirmDialogComponent, {
        context: {
          title: $localize`¿Cambiar tu estado a "lesión"?`,
          message: $localize`Tu entrenador y el club te verán como "lesión" hasta que lo cambies tú. Esto puede
            afectar a tus grupos y a los próximos planes.`,
          confirmLabel: $localize`Sí, cambiar mi estado`,
        },
      }).closed$,
    );
    this.confirmaCambioEstado.set(confirmed === true);
  }

  /** Obligatorio: una acción y un motivo; si Mover, además un día destino, y si ese día está
   * ocupado, una resolución de conflicto — mismo invariante que `RescheduleDayCommand`, replicado
   * aquí solo para habilitar el botón. */
  canSubmit(): boolean {
    const action = this.action();
    if (!action || !this.reason() || !this.form.valid) return false;
    if (action === 'SALTADA') return true;
    const target = this.targetDay();
    if (!target) return false;
    return !this.occupantAt(target) || this.conflictResolution() !== null;
  }

  async submit(): Promise<void> {
    const action = this.action();
    const reason = this.reason();
    if (!action || !reason || !this.canSubmit()) return;
    this.saving.set(true);
    this.errorMessage.set(null);
    const message = this.form.getRawValue().message.trim();
    try {
      await firstValueFrom(
        this.myPlanService.rescheduleDay(this.data.day, {
          accion: action,
          diaDestino: action === 'MOVIDA' ? (this.targetDay() ?? undefined) : undefined,
          motivo: reason,
          mensaje: message ? message : undefined,
          resolucionConflicto: this.conflictResolution() ?? undefined,
          confirmaCambioEstado: reason === 'LESION' ? this.confirmaCambioEstado() : undefined,
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
    this.errorMessage.set(messageForError(err));
  }
}
