import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { AuthPageComponent } from '../shared/auth-page/auth-page.component';
import { PasswordStrengthComponent } from '../shared/password-strength/password-strength.component';
import { SessionService } from '../core/session.service';

/** Validador de grupo: la confirmación debe coincidir con la contraseña nueva. */
function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const confirm = group.get('confirm')?.value;
  return password === confirm ? null : { mismatch: true };
}

/**
 * Pantalla de cambio obligatorio de contraseña por caducidad (LAL-10, ADR-0003 D7; maqueta
 * identidad-acceso). No es accesible por URL directa: se llega desde el login cuando la contraseña
 * ha caducado (respuesta `PASSWORD_EXPIRED`). Recupera de memoria las credenciales caducadas
 * (handoff del login); si no las hay (p. ej. recarga de página) vuelve al login. Al fijar la nueva,
 * el backend inicia la sesión.
 */
@Component({
  selector: 'rc-force-password-change',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    AuthPageComponent,
    PasswordStrengthComponent,
    HlmButton,
    HlmInput,
    HlmLabel,
    HlmSpinner,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <rc-auth-page
      title="Tu contraseña ha caducado"
      subtitle="Por seguridad, crea una contraseña nueva para continuar."
    >
      <form [formGroup]="form" (ngSubmit)="submit()" class="flex flex-col gap-4">
        <div class="flex flex-col gap-1.5">
          <label hlmLabel for="password" class="text-[13px]">Contraseña nueva</label>
          <input
            hlmInput
            id="password"
            type="password"
            formControlName="password"
            autocomplete="new-password"
            placeholder="Al menos 12 caracteres"
          />
        </div>

        <div class="flex flex-col gap-1.5">
          <label hlmLabel for="confirm" class="text-[13px]">Repite la contraseña</label>
          <input
            hlmInput
            id="confirm"
            type="password"
            formControlName="confirm"
            autocomplete="new-password"
            placeholder="Repite la contraseña"
          />
        </div>

        <rc-password-strength [password]="passwordValue()" [confirm]="confirmValue()" />

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
            <hlm-spinner aria-label="Guardando" />
          }
          Guardar y entrar
        </button>
      </form>
    </rc-auth-page>
  `,
})
export class ForcePasswordChangeComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  private credentials: { email: string; password: string } | null = null;
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group(
    {
      password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
      confirm: ['', [Validators.required]],
    },
    { validators: passwordsMatch },
  );

  readonly passwordValue = toSignal(this.form.controls.password.valueChanges, { initialValue: '' });
  readonly confirmValue = toSignal(this.form.controls.confirm.valueChanges, { initialValue: '' });

  ngOnInit(): void {
    this.credentials = this.session.takeExpiredCredentials();
    if (!this.credentials) {
      void this.router.navigate(['/login']);
    }
  }

  submit(): void {
    if (this.form.invalid || !this.credentials) {
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    const { password } = this.form.getRawValue();
    const { email, password: currentPassword } = this.credentials;
    this.session.changeExpiredPassword(email, currentPassword, password).subscribe({
      next: () => void this.router.navigate(['/']),
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(this.messageFor(err));
      },
    });
  }

  private messageFor(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 400) {
        return 'La contraseña no cumple los requisitos (mínimo 12 caracteres, sin tus datos personales y distinta de las anteriores).';
      }
      if (err.status === 401) {
        return 'No hemos podido validar tu identidad. Vuelve a iniciar sesión.';
      }
    }
    return 'No se ha podido cambiar la contraseña. Inténtalo de nuevo.';
  }
}
