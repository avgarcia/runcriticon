import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { AuthPageComponent } from '../shared/auth-page/auth-page.component';
import { SessionService } from '../core/session.service';

/**
 * Pantalla para pedir un magic link de login (LAL-11, ADR-0003 D5; maqueta identidad-acceso). El
 * usuario introduce su email y se le envía un enlace de un solo uso. La respuesta es **neutra**: tras
 * enviar, se muestra "revisa tu email" exista o no la cuenta, para no revelar si un email está
 * registrado.
 */
@Component({
  selector: 'rc-magic-link-request',
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
        title="Entrar"
        subtitle="Te enviaremos un enlace de un solo uso a tu email. Sin contraseñas."
      >
        <form [formGroup]="form" (ngSubmit)="submit()" class="flex flex-col gap-[18px]">
          <div class="flex flex-col gap-1.5">
            <label hlmLabel for="email" class="text-[13px]">Email</label>
            <input
              hlmInput
              id="email"
              type="email"
              formControlName="email"
              autocomplete="username"
              placeholder="tu@email.com"
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
              <hlm-spinner aria-label="Enviando" />
            }
            Enviarme el enlace
          </button>
        </form>

        <a
          routerLink="/login"
          class="p-1.5 text-center text-[13px] font-medium text-primary underline underline-offset-[3px]"
        >
          Entrar con contraseña
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
          <h1 class="text-[21px] font-semibold tracking-[-0.4px]">Revisa tu email</h1>
          <p class="mt-1.5 text-[13.5px] leading-relaxed text-muted-foreground">
            Si tu email está registrado, te hemos enviado un enlace para entrar a
            <strong class="font-medium text-foreground">{{ sentEmail() }}</strong
            >.
          </p>
        </header>
        <div class="rounded-lg bg-muted px-4 py-3.5">
          <h2 class="mb-2 text-[11px] font-semibold text-muted-foreground">¿Y si no llega?</h2>
          <ul
            class="m-0 flex list-none flex-col gap-1 p-0 text-[12.5px] leading-relaxed text-muted-foreground"
          >
            <li>· Mira la carpeta de spam o promociones.</li>
            <li>
              · El enlace caduca en <strong class="font-medium text-foreground">15 minutos</strong>.
            </li>
            <li>· Solo funciona <strong class="font-medium text-foreground">una vez</strong>.</li>
          </ul>
        </div>
        <button
          type="button"
          (click)="reset()"
          class="cursor-pointer border-none bg-transparent p-1.5 text-[13px] font-medium text-muted-foreground"
        >
          Cambiar de email
        </button>
      </rc-auth-page>
    }
  `,
})
export class MagicLinkRequestComponent {
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
    this.session.requestMagicLink(email).subscribe({
      next: () => {
        this.loading.set(false);
        this.sentEmail.set(email);
        this.sent.set(true);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(
          err instanceof HttpErrorResponse && err.status === 400
            ? 'Revisa el email introducido.'
            : 'No se ha podido enviar el enlace. Inténtalo de nuevo.',
        );
      },
    });
  }

  reset(): void {
    this.sent.set(false);
  }
}
