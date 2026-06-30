import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { SessionService } from '../core/session.service';

/**
 * Pantalla para pedir un magic link de login (LAL-11, ADR-0003 D5; wireframe frames 1→2). El usuario
 * introduce su email y se le envía un enlace de un solo uso. La respuesta es **neutra**: tras enviar,
 * se muestra "revisa tu email" exista o no la cuenta, para no revelar si un email está registrado.
 */
@Component({
  selector: 'rc-magic-link-request',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressBarModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="magic">
      <mat-card class="magic__card" appearance="outlined">
        @if (!sent()) {
          <mat-card-header>
            <mat-card-title>Entrar</mat-card-title>
            <mat-card-subtitle
              >Te enviaremos un enlace de un solo uso a tu email. Sin
              contraseñas.</mat-card-subtitle
            >
          </mat-card-header>

          @if (loading()) {
            <mat-progress-bar mode="indeterminate" />
          }

          <mat-card-content>
            <form [formGroup]="form" (ngSubmit)="submit()" class="magic__form">
              <mat-form-field appearance="outline">
                <mat-label>Email</mat-label>
                <input matInput type="email" formControlName="email" autocomplete="username" />
              </mat-form-field>

              @if (errorMessage()) {
                <p class="magic__error" role="alert">{{ errorMessage() }}</p>
              }

              <button mat-flat-button type="submit" [disabled]="form.invalid || loading()">
                Enviarme el enlace
              </button>
            </form>
            <a routerLink="/login" class="magic__alt">Entrar con contraseña</a>
          </mat-card-content>
        } @else {
          <mat-card-header>
            <mat-card-title>Revisa tu email</mat-card-title>
            <mat-card-subtitle
              >Si tu email está registrado, te hemos enviado un enlace para
              entrar.</mat-card-subtitle
            >
          </mat-card-header>
          <mat-card-content>
            <p class="magic__hint">
              El enlace caduca en 15 minutos y solo funciona una vez. Mira también la carpeta de
              spam.
            </p>
            <a routerLink="/login" class="magic__alt">Volver</a>
          </mat-card-content>
        }
      </mat-card>
    </main>
  `,
  styles: [
    `
      .magic {
        display: flex;
        justify-content: center;
        align-items: center;
        min-height: 100vh;
        padding: 1rem;
      }
      .magic__card {
        width: 100%;
        max-width: 24rem;
      }
      .magic__form {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }
      .magic__error {
        color: var(--mat-sys-error, #b3261e);
        margin: 0 0 0.5rem;
      }
      .magic__hint {
        color: var(--mat-sys-on-surface-variant, #5b615e);
        font-size: 0.9rem;
      }
      .magic__alt {
        display: block;
        margin-top: 0.75rem;
        text-align: center;
        color: var(--mat-sys-primary, #1976d2);
      }
    `,
  ],
})
export class MagicLinkRequestComponent {
  private readonly fb = inject(FormBuilder);
  private readonly session = inject(SessionService);

  readonly loading = signal(false);
  readonly sent = signal(false);
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
}
