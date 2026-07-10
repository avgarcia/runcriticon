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
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
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
 * Pantalla de reseteo de contraseña (LAL-12, ADR-0003 D8; maqueta identidad-acceso). Se llega desde
 * el enlace del email (`…/restablecer/nueva?token=…`): lee el token de la query y ofrece un
 * formulario de contraseña nueva + confirmación con medidor de fortaleza (D6). Al enviarlo, el
 * backend fija la contraseña, invalida el resto de sesiones activas e inicia sesión (auto-login) →
 * navega a `/`. Si el enlace ha caducado o ya se usó (404/409), ofrece pedir uno nuevo; si la
 * contraseña no cumple la política (400), muestra el error.
 */
@Component({
  selector: 'rc-password-reset-consume',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AuthPageComponent,
    PasswordStrengthComponent,
    HlmButton,
    HlmInput,
    HlmLabel,
    HlmSpinner,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (linkInvalid()) {
      <rc-auth-page>
        <div
          class="mx-auto flex size-[60px] items-center justify-center rounded-full border border-danger-border bg-danger-soft text-[26px] text-danger"
          aria-hidden="true"
        >
          ⏱
        </div>
        <header class="text-center">
          <h1 class="text-[21px] font-semibold tracking-[-0.4px]">Enlace caducado</h1>
          <p class="mt-1.5 text-[13.5px] leading-relaxed text-muted-foreground">
            Este enlace ya no es válido. Los enlaces de reseteo caducan a los 15 minutos o tras un
            uso.
          </p>
        </header>
        <p
          class="rounded-lg border border-danger-border bg-danger-soft px-3.5 py-3 text-[12.5px] leading-relaxed text-danger"
        >
          <strong>No te preocupes.</strong> Pide un enlace nuevo; recibirás uno fresco en segundos.
        </p>
        <a hlmBtn size="lg" routerLink="/restablecer" class="w-full">Pedir un enlace nuevo</a>
      </rc-auth-page>
    } @else {
      <rc-auth-page
        title="Crea una contraseña nueva"
        subtitle="Elige una contraseña para tu cuenta."
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
    }
  `,
})
export class PasswordResetConsumeComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);

  private token: string | null = null;
  readonly loading = signal(false);
  readonly linkInvalid = signal(false);
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
    this.token = this.route.snapshot.queryParamMap.get('token');
    if (!this.token) {
      this.linkInvalid.set(true);
    }
  }

  submit(): void {
    if (this.form.invalid || !this.token) {
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    const { password } = this.form.getRawValue();
    this.session.consumePasswordReset(this.token, password).subscribe({
      next: () => void this.router.navigate(['/']),
      error: (err: unknown) => {
        this.loading.set(false);
        // 404/409: enlace caducado o ya usado → pantalla de "pide uno nuevo". 400: política D6.
        if (err instanceof HttpErrorResponse && (err.status === 404 || err.status === 409)) {
          this.linkInvalid.set(true);
          return;
        }
        this.errorMessage.set(this.messageFor(err));
      },
    });
  }

  private messageFor(err: unknown): string {
    if (err instanceof HttpErrorResponse && err.status === 400) {
      return 'La contraseña no cumple los requisitos (mínimo 12 caracteres, sin tus datos personales y distinta de las anteriores).';
    }
    return 'No se ha podido restablecer la contraseña. Inténtalo de nuevo.';
  }
}
