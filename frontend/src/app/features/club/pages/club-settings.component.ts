import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { HlmSkeleton } from '@spartan-ng/helm/skeleton';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { ClubService } from '../../../core/club.service';
import { PermissionsService } from '../../../core/permissions.service';
import { ToastService } from '../../../core/toast.service';
import { fieldOf, messageForError } from '../../../core/api/error-codes';

/**
 * Ajustes del club: el admin cambia el nombre sin depender de nadie.
 *
 * La ficha la carga el shell, no esta pantalla: aquí solo se lee el signal de `ClubService`, y al
 * guardar se actualiza ese mismo signal, de modo que la cabecera refleja el nombre nuevo sin
 * recargar.
 *
 * Fuera de alcance por decisión de producto: el logo (falta decidir dónde se almacenan los
 * binarios) y la zona horaria e inicio de semana, que existen como columnas pero no se exponen.
 */
@Component({
  selector: 'rc-club-settings',
  standalone: true,
  imports: [ReactiveFormsModule, HlmButton, HlmInput, HlmLabel, HlmSkeleton, HlmSpinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-2xl">
      <div class="rounded-xl border border-border bg-card p-5">
        <header class="mb-4">
          <h1 class="text-lg font-semibold" i18n>Ajustes del club</h1>
          <p class="text-sm text-muted-foreground" i18n>Datos generales de tu club</p>
        </header>

        @if (club() === undefined) {
          <div class="flex flex-col gap-2">
            <hlm-skeleton class="h-5 w-1/3" />
            <hlm-skeleton class="h-9 w-full" />
          </div>
        } @else if (club() === null) {
          <p class="my-2 text-muted-foreground" role="alert" i18n>
            No se ha encontrado la ficha del club.
          </p>
        } @else {
          <form [formGroup]="form" (ngSubmit)="submit()" class="flex flex-col gap-4">
            <div class="flex flex-col gap-1.5">
              <label hlmLabel for="nombre" i18n>Nombre</label>
              <input hlmInput id="nombre" formControlName="nombre" autocomplete="organization" />
              @if (form.controls.nombre.hasError('backend')) {
                <p class="text-xs text-danger">{{ form.controls.nombre.getError('backend') }}</p>
              } @else if (
                form.controls.nombre.touched && form.controls.nombre.hasError('required')
              ) {
                <p class="text-xs text-danger" i18n>El nombre del club no puede quedar vacío.</p>
              } @else if (form.controls.nombre.hasError('maxlength')) {
                <p class="text-xs text-danger" i18n>
                  El nombre del club no puede pasar de 200 caracteres.
                </p>
              }
            </div>

            <div class="flex flex-col gap-1.5">
              <label hlmLabel for="slug" i18n>Identificador</label>
              <!-- Como binding y no como atributo suelto: es un id de referencia, no texto
                   traducible, y la regla i18n del linter exigiría marcarlo como tal. -->
              <input
                hlmInput
                id="slug"
                [value]="slugTexto()"
                readonly
                [attr.aria-describedby]="'slug-ayuda'"
              />
              <p id="slug-ayuda" class="text-xs text-muted-foreground" i18n>
                Se reserva para dar a cada club su propia dirección web. Todavía no se usa y no se
                puede editar.
              </p>
            </div>

            @if (errorMessage()) {
              <p class="text-sm text-danger" role="alert">{{ errorMessage() }}</p>
            }

            @if (permissions.can('CLUB', 'UPDATE')) {
              <div class="flex justify-end">
                <button hlmBtn type="submit" [disabled]="form.invalid || loading()">
                  @if (loading()) {
                    <hlm-spinner aria-label="Guardando" i18n-aria-label />
                  }
                  <span i18n>Guardar</span>
                </button>
              </div>
            }
          </form>
        }
      </div>
    </div>
  `,
})
export class ClubSettingsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly clubService = inject(ClubService);
  private readonly toastService = inject(ToastService);
  protected readonly permissions = inject(PermissionsService);

  readonly club = this.clubService.club;
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    // Espeja el contrato (`NombreClub`: minLength 1, maxLength 200). `notBlank` cubre el hueco
    // que deja `required`: un nombre de solo espacios pasaría la validación de cliente y volvería
    // del backend como INVALID_INPUT genérico, sin poder decirle al usuario qué corregir.
    nombre: ['', [Validators.required, notBlank, Validators.maxLength(200)]],
  });

  constructor() {
    // La ficha llega por el signal que puebla el shell, que puede resolverse después de montar
    // esta pantalla; el effect sincroniza el formulario cuando eso ocurre.
    effect(() => {
      const club = this.club();
      if (club) {
        this.form.controls.nombre.setValue(club.nombre);
      }
    });
  }

  /** El slug siempre llega vacío en el MVP; un input en blanco parecería un fallo de carga. */
  readonly slugTexto = () => this.club()?.slug || $localize`Sin asignar`;

  async submit(): Promise<void> {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.errorMessage.set(null);
    this.form.controls.nombre.setErrors(null);
    const { nombre } = this.form.getRawValue();
    try {
      // El servicio guarda la respuesta en su signal: la cabecera se actualiza sola.
      await new Promise((resolve, reject) => {
        this.clubService.rename(nombre).subscribe({ next: resolve, error: reject });
      });
      this.loading.set(false);
      this.toastService.success($localize`Nombre del club actualizado.`);
    } catch (err) {
      this.loading.set(false);
      // El 403 ya lo avisa el interceptor global con su toast; no duplicamos el mensaje.
      if (err instanceof HttpErrorResponse && err.status === 403) return;
      if (fieldOf(err) === 'nombre') {
        this.form.controls.nombre.setErrors({ backend: messageForError(err) });
      } else {
        this.errorMessage.set(messageForError(err));
      }
    }
  }
}

/** Rechaza los nombres compuestos solo de espacios (el backend los trata como vacíos). */
function notBlank(control: AbstractControl): { required: true } | null {
  return typeof control.value === 'string' && control.value.trim().length === 0
    ? { required: true }
    : null;
}
