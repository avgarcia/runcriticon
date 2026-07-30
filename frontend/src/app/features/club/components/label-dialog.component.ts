import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import {
  HlmDialogClose,
  HlmDialogFooter,
  HlmDialogHeader,
  HlmDialogTitle,
} from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { Observable, firstValueFrom } from 'rxjs';
import { fieldOf, messageForError } from '../../../core/api/error-codes';

/**
 * Datos del diálogo de texto de la taxonomía. `submit` lo aporta quien lo abre: las cuatro
 * operaciones que piden un texto (crear y renombrar un tag, crear y renombrar un valor) solo se
 * diferencian en qué llaman y con qué límite, así que el diálogo recibe la llamada en vez de
 * conocerlas.
 */
export interface LabelDialogData {
  readonly title: string;
  readonly label: string;
  readonly confirmLabel: string;
  readonly initialValue: string;
  readonly maxLength: number;
  /** El `field` que devuelve el backend en sus errores: `nombre` para tags, `valor` para valores. */
  readonly field: 'nombre' | 'valor';
  readonly submit: (value: string) => Observable<unknown>;
}

/**
 * Diálogo de una sola línea de texto para la taxonomía. Devuelve el texto guardado al confirmar y
 * `undefined` al cancelar.
 *
 * Es el diálogo quien llama al backend y no la pantalla, para poder pintar el error **dentro del
 * campo**: un nombre duplicado o demasiado largo se corrige aquí mismo, sin cerrar el diálogo y
 * perder lo tecleado.
 */
@Component({
  selector: 'rc-label-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogFooter,
    HlmDialogClose,
    HlmButton,
    HlmInput,
    HlmLabel,
    HlmSpinner,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div hlmDialogHeader>
      <h2 hlmDialogTitle>{{ data.title }}</h2>
    </div>

    <form
      [formGroup]="form"
      id="label-form"
      (ngSubmit)="submit()"
      class="flex min-w-80 flex-col gap-4 pt-2"
    >
      <div class="flex flex-col gap-1.5">
        <label hlmLabel for="label">{{ data.label }}</label>
        <input
          hlmInput
          id="label"
          formControlName="label"
          autocomplete="off"
          [attr.maxlength]="data.maxLength"
        />
        @if (form.controls.label.hasError('backend')) {
          <p class="text-xs text-danger">{{ form.controls.label.getError('backend') }}</p>
        } @else if (form.controls.label.touched && form.controls.label.hasError('required')) {
          <p class="text-xs text-danger" i18n>No puede quedar vacío.</p>
        }
      </div>
      @if (errorMessage()) {
        <p class="text-sm text-danger" role="alert">{{ errorMessage() }}</p>
      }
    </form>

    <div hlmDialogFooter>
      <button hlmBtn variant="outline" type="button" hlmDialogClose i18n>Cancelar</button>
      <button hlmBtn type="submit" form="label-form" [disabled]="form.invalid || loading()">
        @if (loading()) {
          <hlm-spinner aria-label="Guardando" i18n-aria-label />
        }
        <span>{{ data.confirmLabel }}</span>
      </button>
    </div>
  `,
})
export class LabelDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject(BrnDialogRef<string>);
  readonly data = injectBrnDialogContext<LabelDialogData>();

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    // `notBlank` cubre el hueco que deja `required`: un texto de solo espacios pasaría la validación
    // de cliente y volvería del backend como LABEL_BLANK. El máximo no se valida aquí porque el
    // `maxlength` del input ya impide teclear de más.
    label: [this.data.initialValue, [Validators.required, notBlank]],
  });

  async submit(): Promise<void> {
    if (this.form.invalid || this.loading()) return;
    this.loading.set(true);
    this.errorMessage.set(null);
    this.form.controls.label.setErrors(null);
    const { label } = this.form.getRawValue();
    try {
      await firstValueFrom(this.data.submit(label));
      this.dialogRef.close(label);
    } catch (err) {
      this.loading.set(false);
      // El 403 ya lo avisa el interceptor global con su toast; no duplicamos el mensaje.
      if (err instanceof HttpErrorResponse && err.status === 403) return;
      if (fieldOf(err) === this.data.field) {
        this.form.controls.label.setErrors({ backend: messageForError(err) });
      } else {
        this.errorMessage.set(messageForError(err));
      }
    }
  }
}

/** Rechaza los textos compuestos solo de espacios (el backend los trata como vacíos). */
function notBlank(control: AbstractControl): { required: true } | null {
  return typeof control.value === 'string' && control.value.trim().length === 0
    ? { required: true }
    : null;
}
