import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { ActivacionService } from '../../../api/generated/services/activacion.service';
import { AuthPageComponent } from '../../../shared/auth-page/auth-page.component';
import { CheckboxComponent } from '../../../shared/forms/checkbox.component';
import { PasswordStrengthComponent } from '../../../shared/password-strength/password-strength.component';
import { CONSENT_TEXT_VERSION } from '../../../core/consent.service';
import { SessionService } from '../../../core/session.service';

/** Validador de grupo: la confirmación debe coincidir con la contraseña. */
function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const confirm = group.get('confirm')?.value;
  return password === confirm ? null : { mismatch: true };
}

/**
 * Pantalla pública de activación de cuenta por invitación (LAL-9, ADR-0003 D4/D6; maqueta
 * identidad-acceso). El invitado abre `…/activar?token=…` desde el email, fija una contraseña y
 * entra (auto-login). La validación de la política la manda el backend; aquí solo se replica la
 * longitud y la coincidencia para UX. Versión degradada de la maqueta: sin datos de la invitación
 * (club, rol, quién invita) hasta que exista el endpoint de consulta por token (LAL-64).
 */
@Component({
  selector: 'rc-activate',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AuthPageComponent,
    CheckboxComponent,
    PasswordStrengthComponent,
    HlmButton,
    HlmInput,
    HlmLabel,
    HlmSpinner,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!hasToken) {
      <rc-auth-page>
        <div
          class="mx-auto flex size-[60px] items-center justify-center rounded-full border border-danger-border bg-danger-soft text-[26px] text-danger"
          aria-hidden="true"
        >
          ⚠
        </div>
        <header class="text-center">
          <h1 class="text-[21px] font-semibold tracking-[-0.4px]" i18n>Invitación no válida</h1>
          <p class="mt-1.5 text-[13.5px] leading-relaxed text-muted-foreground" role="alert" i18n>
            El enlace no es válido. Pide al administrador o a tu entrenador que te reenvíe la
            invitación.
          </p>
        </header>
        <a
          routerLink="/login"
          class="p-2 text-center text-[13px] font-medium text-primary underline underline-offset-[3px]"
          i18n
        >
          Volver
        </a>
      </rc-auth-page>
    } @else {
      <rc-auth-page
        title="Activa tu cuenta"
        i18n-title
        subtitle="Tu club te ha invitado a Runcriticon. Elige una contraseña para entrar."
        i18n-subtitle
      >
        <form [formGroup]="form" (ngSubmit)="submit()" class="flex flex-col gap-4">
          <div class="flex flex-col gap-1.5">
            <label hlmLabel for="password" class="text-[13px]" i18n>Contraseña</label>
            <input
              hlmInput
              id="password"
              type="password"
              formControlName="password"
              autocomplete="new-password"
              placeholder="Al menos 12 caracteres"
              i18n-placeholder
            />
          </div>

          <div class="flex flex-col gap-1.5">
            <label hlmLabel for="confirm" class="text-[13px]" i18n>Repite la contraseña</label>
            <input
              hlmInput
              id="confirm"
              type="password"
              formControlName="confirm"
              autocomplete="new-password"
              placeholder="Repite la contraseña"
              i18n-placeholder
            />
          </div>

          <rc-password-strength [password]="passwordValue()" [confirm]="confirmValue()" />

          <rc-checkbox
            [inputId]="'consentimiento-datos-salud'"
            [checked]="consentGranted()"
            (checkedChange)="consentGranted.set($event)"
          >
            <span i18n>
              Doy mi consentimiento explícito para el tratamiento de mis datos de salud (sensaciones,
              molestias) que registraré al reportar mis sesiones de entrenamiento. Solo aplica si vas a
              usar Runcriticon como alumno — puedes revocarlo cuando quieras desde Mi cuenta.
            </span>
          </rc-checkbox>

          @if (errorMessage()) {
            <p
              class="rounded-lg border border-danger-border bg-danger-soft px-3 py-2.5 text-[12.5px] leading-snug text-danger"
              role="alert"
            >
              {{ errorMessage() }}
            </p>
          }

          <button
            hlmBtn
            size="lg"
            type="submit"
            class="w-full"
            [disabled]="form.invalid || loading()"
          >
            @if (loading()) {
              <hlm-spinner aria-label="Activando" i18n-aria-label />
            }
            <span i18n>Activar mi cuenta</span>
          </button>
        </form>

        <p class="text-center text-[11.5px] text-muted-foreground" i18n>
          Al continuar aceptas la política de privacidad del club.
        </p>
      </rc-auth-page>
    }
  `,
})
export class ActivateComponent {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly activacionService = inject(ActivacionService);
  private readonly session = inject(SessionService);

  private readonly token = this.route.snapshot.queryParamMap.get('token');
  readonly hasToken = this.token !== null && this.token !== '';
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  /**
   * Sin `FormControl`: esta pantalla no conoce el rol del invitado (no hay endpoint que resuelva la
   * invitación por token todavía, LAL-63/LAL-64) así que no se puede bloquear el envío por esto — solo
   * el ALUMNO la necesita, y el backend la exige o la ignora según corresponda. Si falta, el backend
   * responde `CONSENTIMIENTO_REQUERIDO` y se muestra como cualquier otro error del servidor.
   */
  readonly consentGranted = signal(false);

  readonly form = this.fb.nonNullable.group(
    {
      password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
      confirm: ['', [Validators.required]],
    },
    { validators: passwordsMatch },
  );

  readonly passwordValue = toSignal(this.form.controls.password.valueChanges, { initialValue: '' });
  readonly confirmValue = toSignal(this.form.controls.confirm.valueChanges, { initialValue: '' });

  async submit(): Promise<void> {
    if (this.form.invalid || !this.token) {
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    const { password } = this.form.getRawValue();
    try {
      await this.activacionService.activarCuenta({
        body: {
          token: this.token,
          password,
          consentimiento: this.consentGranted(),
          versionConsentimiento: CONSENT_TEXT_VERSION,
        },
      });
      // La cookie de sesión ya está puesta; cargamos la sesión y entramos.
      this.session.loadCurrent().subscribe({
        next: () => void this.router.navigate(['/']),
        error: () => void this.router.navigate(['/login']),
      });
    } catch (err) {
      this.loading.set(false);
      this.errorMessage.set(this.messageFor(err));
    }
  }

  private messageFor(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 409 && err.error?.code === 'VERSION_CONSENTIMIENTO_OBSOLETA') {
        return $localize`El texto de consentimiento ha cambiado; recarga la página e inténtalo de nuevo.`;
      }
      if (err.status === 409) {
        return $localize`Tu cuenta ya está activa. Inicia sesión.`;
      }
      if (err.error?.code === 'CONSENTIMIENTO_REQUERIDO') {
        return $localize`Marca la casilla de consentimiento de datos de salud para activar tu cuenta.`;
      }
      if (err.status === 400) {
        return $localize`El enlace no es válido o ha caducado, o la contraseña no cumple los requisitos. Pide que te reenvíen la invitación.`;
      }
    }
    return $localize`No se ha podido activar la cuenta. Inténtalo de nuevo.`;
  }
}
