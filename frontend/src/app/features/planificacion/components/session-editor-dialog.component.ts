import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogFooter, HlmDialogHeader, HlmDialogTitle } from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { firstValueFrom } from 'rxjs';
import { messageForError } from '../../../core/api/error-codes';
import { PlanService, PlanSession, UpdateSessionData } from '../../../core/plan.service';
import { formatPace, parsePace } from '../pace-format';
import { SESSION_TYPES, SessionType } from '../session-types';

type VolumeType = 'DISTANCIA' | 'TIEMPO';

/** Datos que necesita el diálogo: el día (siempre, se abre para un hueco concreto de la rejilla) y,
 * si se está editando, la sesión ya existente. Sin `session`, es un alta. */
export interface SessionEditorDialogData {
  readonly planId: string;
  readonly day: string;
  readonly session?: PlanSession;
  /** Nº de alumnos con un ajuste vigente en esta sesión (LAL-26) — `undefined` en una sesión de alta. */
  readonly personalizationCount?: number;
}

/**
 * Editor de sesión (LAL-24): tipo, volumen (distancia o tiempo), ritmo y notas. Solo `ABSOLUTO` en el
 * selector de ritmo (AC2) — el modelo ya admite `RELATIVO` desde LAL-114, pero su UI llega con LAL-27.
 *
 * Sin campos de repeticiones/recuperación/calentamiento del wireframe hi-fi (`docs/diseno/editor-sesion.html`):
 * la tarjeta de la vista semanal (`docs/diseno/editor-plan-semanal.html`) solo pinta tipo, volumen, ritmo y
 * notas — es el mismo recorte deliberado documentado en el README del módulo.
 *
 * El diálogo llama al backend directamente (como `group-coaches-dialog`), no la pantalla: así el error de
 * validación se pinta sin cerrar el diálogo ni perder lo tecleado. Devuelve `true` si algo cambió (alta,
 * edición o borrado), para que la rejilla recargue el plan.
 */
@Component({
  selector: 'rc-session-editor-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    HlmButton,
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogFooter,
    HlmInput,
    HlmLabel,
    HlmSpinner,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div hlmDialogHeader>
      <h2 hlmDialogTitle>
        @if (data.session) {
          <span i18n>Editar sesión</span>
        } @else {
          <span i18n>Nueva sesión</span>
        }
        · {{ data.day }}
      </h2>
    </div>

    <form [formGroup]="form" id="session-form" (ngSubmit)="submit()" class="flex min-w-96 max-w-lg flex-col gap-4 pt-2">
      <section>
        <p class="mb-2 text-sm font-medium" i18n>Tipo de sesión</p>
        <div class="flex flex-wrap gap-2">
          @for (t of sessionTypes; track t.value) {
            <button
              type="button"
              class="rounded-full border px-3 py-1.5 text-sm font-medium transition-colors"
              [class.border-primary]="selectedType() === t.value"
              [class.bg-primary]="selectedType() === t.value"
              [class.text-primary-foreground]="selectedType() === t.value"
              [class.border-border]="selectedType() !== t.value"
              (click)="selectType(t.value)"
            >
              {{ t.label }}
            </button>
          }
        </div>
      </section>

      @if (selectedType() && selectedType() !== 'DESCANSO') {
        <section>
          <p class="mb-2 text-sm font-medium" i18n>Volumen</p>
          <div class="mb-2 flex gap-2">
            <button
              hlmBtn
              type="button"
              size="sm"
              [variant]="volumeType() === 'DISTANCIA' ? 'default' : 'outline'"
              (click)="setVolumeType('DISTANCIA')"
              i18n
            >
              Distancia
            </button>
            <button
              hlmBtn
              type="button"
              size="sm"
              [variant]="volumeType() === 'TIEMPO' ? 'default' : 'outline'"
              (click)="setVolumeType('TIEMPO')"
              i18n
            >
              Tiempo
            </button>
          </div>
          @if (volumeType(); as tipo) {
            <div class="flex flex-col gap-1.5">
              <label hlmLabel for="volumeValue">
                {{ tipo === 'DISTANCIA' ? textoMetros : textoMinutos }}
              </label>
              <input hlmInput id="volumeValue" type="number" min="1" formControlName="volumeValue" />
              @if (form.controls.volumeValue.touched && form.controls.volumeValue.hasError('min')) {
                <p class="text-xs text-danger" i18n>Debe ser mayor que cero.</p>
              }
            </div>
          }
        </section>

        <section>
          <p class="mb-2 text-sm font-medium" i18n>Ritmo objetivo (absoluto)</p>
          <div class="flex flex-col gap-1.5">
            <label hlmLabel for="paceText" i18n>Ritmo (m:ss /km)</label>
            <input hlmInput id="paceText" formControlName="paceText" placeholder="3:45" autocomplete="off" />
            @if (form.controls.paceText.hasError('pace')) {
              <p class="text-xs text-danger" i18n>Usa el formato m:ss, por ejemplo 3:45.</p>
            }
          </div>
        </section>
      }

      <div class="flex flex-col gap-1.5">
        <label hlmLabel for="notes" i18n>Notas para el alumno</label>
        <textarea
          id="notes"
          formControlName="notes"
          rows="3"
          maxlength="1000"
          class="dark:bg-input/30 border-input focus-visible:border-ring focus-visible:ring-ring/20 min-h-20 w-full rounded-lg border bg-card px-3 py-2 text-base shadow-xs outline-none placeholder:text-muted-foreground md:text-sm"
        ></textarea>
      </div>

      @if (data.session) {
        <div class="border-t border-border pt-3">
          <div class="flex items-center justify-between gap-2">
            <div>
              <p class="text-sm font-medium">
                @if (data.personalizationCount) {
                  <span i18n>{{ data.personalizationCount }} alumno(s) con un ajuste personalizado</span>
                } @else {
                  <span i18n>Sin personalizaciones</span>
                }
              </p>
              <p class="text-xs text-muted-foreground" i18n>
                Sobrescriben esta sesión solo para alumnos concretos.
              </p>
            </div>
            <button hlmBtn variant="ghost" size="sm" type="button" (click)="manage()" i18n>Gestionar →</button>
          </div>
        </div>
      }

      @if (errorMessage()) {
        <p class="text-sm text-danger" role="alert">{{ errorMessage() }}</p>
      }
    </form>

    <div hlmDialogFooter class="flex items-center justify-between">
      @if (data.session) {
        <button
          hlmBtn
          variant="ghost"
          type="button"
          class="text-danger"
          [disabled]="saving()"
          (click)="deleteSession()"
          i18n
        >
          Eliminar sesión
        </button>
      }
      <div class="ml-auto flex gap-2">
        <button hlmBtn variant="outline" type="button" (click)="close()" i18n>Cancelar</button>
        <button hlmBtn type="submit" form="session-form" [disabled]="!canSubmit() || saving()">
          @if (saving()) {
            <hlm-spinner aria-label="Guardando" i18n-aria-label />
          }
          <span i18n>Guardar sesión</span>
        </button>
      </div>
    </div>
  `,
})
export class SessionEditorDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly planService = inject(PlanService);
  private readonly dialogRef = inject(BrnDialogRef<boolean | 'manage-personalizations'>);

  readonly data = injectBrnDialogContext<SessionEditorDialogData>();

  readonly sessionTypes = SESSION_TYPES;
  readonly textoMetros = $localize`Metros`;
  readonly textoMinutos = $localize`Minutos`;

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedType = signal<SessionType | null>(this.data.session?.tipo ?? null);
  readonly volumeType = signal<VolumeType | null>((this.data.session?.volumen?.tipo as VolumeType) ?? null);

  readonly form = this.fb.nonNullable.group({
    volumeValue: [
      this.data.session?.volumen?.metros ?? this.data.session?.volumen?.minutos ?? null,
      [Validators.min(1)],
    ],
    paceText: [
      this.data.session?.ritmo?.tipo === 'ABSOLUTO' ? formatPace(this.data.session.ritmo.segundosPorKm ?? 0) : '',
      [paceValidator],
    ],
    notes: [this.data.session?.notas ?? '', [Validators.maxLength(1000)]],
  });

  /** Sesión válida para guardar: un tipo elegido y (si aplica) un ritmo con formato correcto. */
  canSubmit(): boolean {
    return this.selectedType() !== null && this.form.valid;
  }

  selectType(type: SessionType): void {
    this.selectedType.set(type);
    if (type === 'DESCANSO') {
      this.volumeType.set(null);
      this.form.patchValue({ volumeValue: null, paceText: '' });
    }
  }

  setVolumeType(type: VolumeType): void {
    this.volumeType.set(type);
  }

  async submit(): Promise<void> {
    const type = this.selectedType();
    if (!type || !this.canSubmit()) return;
    this.saving.set(true);
    this.errorMessage.set(null);
    this.form.controls.paceText.setErrors(null);
    const body = this.buildBody(type);
    try {
      if (this.data.session) {
        await firstValueFrom(this.planService.updateSession(this.data.planId, this.data.session.id, body));
      } else {
        await firstValueFrom(this.planService.addSession(this.data.planId, { ...body, dia: this.data.day }));
      }
      this.dialogRef.close(true);
    } catch (err) {
      this.handleError(err);
    }
  }

  deleteSession(): void {
    const session = this.data.session;
    if (!session) return;
    this.saving.set(true);
    this.errorMessage.set(null);
    this.planService.deleteSession(this.data.planId, session.id).subscribe({
      next: () => this.dialogRef.close(true),
      error: (err: unknown) => this.handleError(err),
    });
  }

  close(): void {
    this.dialogRef.close(false);
  }

  /** Cierra este editor y le pide a `PlanDetailComponent` que abra las personalizaciones como diálogo
   * hermano (LAL-26) — nunca anidado sobre este. */
  manage(): void {
    this.dialogRef.close('manage-personalizations');
  }

  private buildBody(type: SessionType): UpdateSessionData {
    const { volumeValue, paceText, notes } = this.form.getRawValue();
    const volumeKind = this.volumeType();
    const seconds = paceText.trim() ? parsePace(paceText) : null;
    return {
      tipo: type,
      volumen:
        volumeKind && volumeValue
          ? volumeKind === 'DISTANCIA'
            ? { tipo: 'DISTANCIA', metros: volumeValue }
            : { tipo: 'TIEMPO', minutos: volumeValue }
          : undefined,
      ritmo: seconds !== null ? { tipo: 'ABSOLUTO', segundosPorKm: seconds } : undefined,
      notas: notes.trim() ? notes.trim() : undefined,
    };
  }

  /** Banner general, no error de campo: `dia` no es editable aquí y `volumen`/`ritmo` no son un único
   * control, así que no hay un mapeo 1:1 limpio campo→control como en `club-settings`/`label-dialog`. */
  private handleError(err: unknown): void {
    this.saving.set(false);
    // El 403 ya lo avisa el interceptor global con su toast; no duplicamos el mensaje.
    if (err instanceof HttpErrorResponse && err.status === 403) return;
    this.errorMessage.set(messageForError(err));
  }
}

/** Rechaza un texto que no tenga el formato `m:ss`. Vacío se acepta: el ritmo es opcional. */
function paceValidator(control: AbstractControl): { pace: true } | null {
  const value: string = control.value ?? '';
  if (!value.trim()) return null;
  return parsePace(value) === null ? { pace: true } : null;
}
