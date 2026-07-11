import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { AuthPageComponent } from '../../../shared/auth-page/auth-page.component';
import { SessionService } from '../../../core/session.service';

/**
 * Pantalla de login con contraseña (ADR-0003 D5; maqueta docs/diseno/identidad-acceso.html).
 * El error es neutro (no distingue email inexistente de contraseña incorrecta).
 */
@Component({
  selector: 'rc-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AuthPageComponent,
    HlmButton,
    HlmInput,
    HlmLabel,
    HlmSpinner,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <rc-auth-page
      title="Inicia sesión"
      i18n-title
      subtitle="Entra con tu email y contraseña."
      i18n-subtitle
    >
      <form [formGroup]="form" (ngSubmit)="submit()" class="flex flex-col gap-[18px]">
        <div class="flex flex-col gap-1.5">
          <label hlmLabel for="email" class="text-[13px]" i18n>Email</label>
          <input
            hlmInput
            id="email"
            type="email"
            formControlName="email"
            autocomplete="username"
            placeholder="tu@email.com"
            i18n-placeholder
          />
        </div>

        <div class="flex flex-col gap-1.5">
          <label hlmLabel for="password" class="text-[13px]" i18n>Contraseña</label>
          <input
            hlmInput
            id="password"
            type="password"
            formControlName="password"
            autocomplete="current-password"
            placeholder="Tu contraseña"
            i18n-placeholder
          />
        </div>

        @if (error()) {
          <p
            class="rounded-lg border border-danger-border bg-danger-soft px-3 py-2.5 text-[12.5px] leading-snug text-danger"
            role="alert"
            i18n
          >
            Email o contraseña incorrectos.
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
            <hlm-spinner aria-label="Entrando" i18n-aria-label />
          }
          <span i18n>Entrar</span>
        </button>
      </form>

      <div class="mt-0.5 flex flex-col items-center gap-0.5">
        <a
          routerLink="/entrar-con-enlace"
          class="p-2 text-[13px] font-medium text-primary underline underline-offset-[3px]"
          i18n
        >
          Entrar con un enlace mágico
        </a>
        <a routerLink="/restablecer" class="p-1 text-[12.5px] text-muted-foreground" i18n>
          ¿Has olvidado tu contraseña?
        </a>
      </div>
    </rc-auth-page>
  `,
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly loading = signal(false);
  readonly error = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.loading.set(true);
    this.error.set(false);
    const { email, password } = this.form.getRawValue();
    this.session.start(email, password).subscribe({
      next: () => {
        // Tras un 401 fuera de un flujo anónimo, el interceptor (ADR-0012 D15) o authGuard
        // adjuntan returnUrl con la ruta que el usuario quería visitar antes de que le echaran.
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        if (returnUrl) {
          void this.router.navigateByUrl(returnUrl);
        } else {
          void this.router.navigate(['/']);
        }
      },
      error: (err: unknown) => {
        // Contraseña caducada (ADR-0003 D7): no es un error de credenciales — se lleva al cambio
        // obligatorio conservando las credenciales en memoria para revalidar.
        if (
          err instanceof HttpErrorResponse &&
          err.status === 409 &&
          err.error?.code === 'PASSWORD_EXPIRED'
        ) {
          this.session.stashExpiredCredentials(email, password);
          void this.router.navigate(['/cambiar-contrasena']);
          return;
        }
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }
}
