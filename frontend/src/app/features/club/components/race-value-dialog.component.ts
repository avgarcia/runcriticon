import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
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
import { HlmSelectImports } from '@spartan-ng/helm/select';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { Observable, firstValueFrom } from 'rxjs';
import { fieldOf, messageForError } from '../../../core/api/error-codes';
import { TagValueMetadataInput } from '../../../core/taxonomy.service';
import { RACE_DISTANCES, RaceDistance } from '../race-distance-labels';

/**
 * Datos del diálogo de carrera. `valueField` presente ⇒ el diálogo también pide el literal del valor
 * (alta); ausente ⇒ solo edita la carrera de un valor ya existente («Editar carrera» del menú).
 */
export interface RaceValueDialogData {
  readonly title: string;
  readonly confirmLabel: string;
  readonly valueField: { readonly initialValue: string; readonly maxLength: number } | null;
  readonly initialDate: string | null;
  readonly initialDistance: RaceDistance | null;
  readonly submit: (valor: string | undefined, metadata: TagValueMetadataInput) => Observable<unknown>;
}

/**
 * Diálogo de valor de carrera del eje `objetivo` (LAL-84): fecha y distancia, y el literal del valor
 * cuando se usa para dar de alta.
 *
 * Fecha y distancia van **juntas**: dejar las dos vacías guarda metadata `EMPTY` (el valor neutro «sin
 * carrera», o quitarle la carrera a uno existente); rellenar solo una es un estado a medias que el
 * backend rechazaría como `INVALID_INPUT` — se valida aquí antes de enviar para no ida-y-vuelta.
 *
 * Mismo patrón que `LabelDialogComponent`: es el diálogo quien llama al backend, para pintar el error
 * de campo sin cerrarse ni perder lo tecleado.
 */
@Component({
  selector: 'rc-race-value-dialog',
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
    ...HlmSelectImports,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div hlmDialogHeader>
      <h2 hlmDialogTitle>{{ data.title }}</h2>
    </div>

    <form
      [formGroup]="form"
      id="race-value-form"
      (ngSubmit)="submit()"
      class="flex min-w-80 flex-col gap-4 pt-2"
    >
      @if (data.valueField) {
        <div class="flex flex-col gap-1.5">
          <label hlmLabel for="valor" i18n>Valor</label>
          <input
            hlmInput
            id="valor"
            formControlName="valor"
            autocomplete="off"
            [attr.maxlength]="data.valueField.maxLength"
          />
          @if (form.controls.valor.hasError('backend')) {
            <p class="text-xs text-danger">{{ form.controls.valor.getError('backend') }}</p>
          } @else if (form.controls.valor.touched && form.controls.valor.hasError('required')) {
            <p class="text-xs text-danger" i18n>No puede quedar vacío.</p>
          }
        </div>
      }

      <div class="flex flex-col gap-1.5">
        <label hlmLabel for="fecha" i18n>Fecha de la carrera</label>
        <input hlmInput type="date" id="fecha" formControlName="fecha" />
      </div>

      <div class="flex flex-col gap-1.5">
        <!-- Un combobox no toma su nombre del contenido: la label oculta es obligatoria para a11y. -->
        <label class="sr-only" [for]="distanceTriggerId" i18n>Distancia de la carrera</label>
        <hlm-select formControlName="distancia">
          <hlm-select-trigger [buttonId]="distanceTriggerId">
            <hlm-select-value placeholder="Distancia" i18n-placeholder />
          </hlm-select-trigger>
          <hlm-select-content label="Distancias disponibles" i18n-label>
            @for (distance of distances; track distance.value) {
              <hlm-select-item [value]="distance.value">{{ distance.label }}</hlm-select-item>
            }
          </hlm-select-content>
        </hlm-select>
      </div>

      @if (form.hasError('metadataIncompleta')) {
        <p class="text-xs text-danger" role="alert" i18n>
          Completa la fecha y la distancia, o deja las dos vacías.
        </p>
      }

      @if (errorMessage()) {
        <p class="text-sm text-danger" role="alert">{{ errorMessage() }}</p>
      }
    </form>

    <div hlmDialogFooter>
      <button hlmBtn variant="outline" type="button" hlmDialogClose i18n>Cancelar</button>
      <button hlmBtn type="submit" form="race-value-form" [disabled]="form.invalid || loading()">
        @if (loading()) {
          <hlm-spinner aria-label="Guardando" i18n-aria-label />
        }
        <span>{{ data.confirmLabel }}</span>
      </button>
    </div>
  `,
})
export class RaceValueDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject(BrnDialogRef<true>);
  readonly data = injectBrnDialogContext<RaceValueDialogData>();

  readonly distances = RACE_DISTANCES;
  readonly distanceTriggerId = 'race-value-distancia';

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group(
    {
      valor: [
        this.data.valueField?.initialValue ?? '',
        this.data.valueField ? [Validators.required, notBlank] : [],
      ],
      fecha: [this.data.initialDate ?? ''],
      distancia: this.fb.control<RaceDistance | null>(this.data.initialDistance),
    },
    { validators: bothOrNeither },
  );

  async submit(): Promise<void> {
    if (this.form.invalid || this.loading()) return;
    this.loading.set(true);
    this.errorMessage.set(null);
    this.form.controls.valor.setErrors(null);
    const { valor, fecha, distancia } = this.form.getRawValue();
    const metadata: TagValueMetadataInput =
      fecha && distancia ? { tipo: 'RACE', fecha, distancia } : { tipo: 'EMPTY' };
    try {
      await firstValueFrom(this.data.submit(this.data.valueField ? valor : undefined, metadata));
      this.dialogRef.close(true);
    } catch (err) {
      this.loading.set(false);
      // El 403 ya lo avisa el interceptor global con su toast; no duplicamos el mensaje.
      if (err instanceof HttpErrorResponse && err.status === 403) return;
      if (fieldOf(err) === 'valor' && this.data.valueField) {
        this.form.controls.valor.setErrors({ backend: messageForError(err) });
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

/** Fecha y distancia van juntas: una sin la otra es un estado a medias que el backend rechazaría. */
function bothOrNeither(group: AbstractControl): ValidationErrors | null {
  const fecha = group.get('fecha')?.value as string;
  const distancia = group.get('distancia')?.value as RaceDistance | null;
  return Boolean(fecha) === Boolean(distancia) ? null : { metadataIncompleta: true };
}
