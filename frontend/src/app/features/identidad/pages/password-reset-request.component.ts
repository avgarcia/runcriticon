import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { AuthPageComponent } from '../../../shared/auth-page/auth-page.component';
import { SessionService } from '../../../core/session.service';

/**
 * Pantalla para pedir un reseteo de contraseña (LAL-12, ADR-0003 D8; maqueta identidad-acceso). El
 * usuario introduce su email y se le envía un enlace de un solo uso (15 min) para crear una
 * contraseña nueva sin conocer la antigua. La respuesta es **neutra**: tras enviar, se muestra
 * "revisa tu email" exista o no la cuenta. Espejo de `MagicLinkRequestComponent`.
 */
@Component({
  selector: 'rc-password-reset-request',
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
    @if (!sent()) {
      <rc-auth-page
        title="Restablecer contraseña"
        i18n-title
        subtitle="Te enviaremos un enlace para crear una contraseña nueva."
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
              <hlm-spinner aria-label="Enviando" i18n-aria-label />
            }
            <span i18n>Enviarme el enlace</span>
          </button>
        </form>

        <a
          routerLink="/login"
          class="p-1.5 text-center text-[13px] font-medium text-primary underline underline-offset-[3px]"
          i18n
        >
          Volver a iniciar sesión
        </a>
      </rc-auth-page>
    } @else {
      <rc-auth-page>
        <div
          class="mx-auto flex size-[60px] items-center justify-center rounded-full border border-success-border bg-success-soft text-[26px] text-success"
          aria-hidden="true"
        >
          ✉
        </div>
        <header class="text-center">
          <h1 class="text-[21px] font-semibold tracking-[-0.4px]" i18n>Revisa tu email</h1>
          <p class="mt-1.5 text-[13.5px] leading-relaxed text-muted-foreground" i18n>
            Si tu email está registrado, te hemos enviado un enlace para restablecer tu contraseña a
            <strong class="font-medium text-foreground">{{ sentEmail() }}</strong
            >.
          </p>
        </header>
        <p
          class="rounded-lg bg-muted px-4 py-3.5 text-[12.5px] leading-relaxed text-muted-foreground"
          i18n
        >
          El enlace caduca en <strong class="font-medium text-foreground">15 minutos</strong> y solo
          funciona una vez. Mira también la carpeta de spam.
        </p>
        <a
          routerLink="/login"
          class="p-1.5 text-center text-[13px] font-medium text-muted-foreground"
          i18n
        >
          Volver
        </a>
      </rc-auth-page>
    }
  `,
})
export class PasswordResetRequestComponent {
  private readonly fb = inject(FormBuilder);
  private readonly session = inject(SessionService);

  readonly loading = signal(false);
  readonly sent = signal(false);
  readonly sentEmail = signal('');
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    const { email } = this.form.getRawValue();
    this.session.requestPasswordReset(email).subscribe({
      next: () => {
        this.loading.set(false);
        this.sentEmail.set(email);
        this.sent.set(true);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(
          err instanceof HttpErrorResponse && err.status === 400
            ? $localize`Revisa el email introducido.`
            : $localize`No se ha podido enviar el enlace. Inténtalo de nuevo.`,
        );
      },
    });
  }
}
