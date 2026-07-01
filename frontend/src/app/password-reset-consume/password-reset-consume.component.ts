import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { SessionService } from '../core/session.service';

/** Validador de grupo: la confirmación debe coincidir con la contraseña nueva. */
function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const confirm = group.get('confirm')?.value;
  return password === confirm ? null : { mismatch: true };
}

/**
 * Pantalla de reseteo de contraseña (LAL-12, ADR-0003 D8). Se llega desde el enlace del email
 * (`…/restablecer/nueva?token=…`): lee el token de la query y ofrece un formulario de contraseña
 * nueva + confirmación con los requisitos D6. Al enviarlo, el backend fija la contraseña, invalida el
 * resto de sesiones activas e inicia sesión (auto-login) → navega a `/`. Si el enlace ha caducado o ya
 * se usó (404/409), ofrece pedir uno nuevo; si la contraseña no cumple la política (400), muestra el
 * error. Mezcla de `MagicLinkConsumeComponent` (token de query) y `ForcePasswordChangeComponent`
 * (form de contraseña).
 */
@Component({
  selector: 'rc-password-reset-consume',
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
    <main class="reset">
      <mat-card class="reset__card" appearance="outlined">
        @if (linkInvalid()) {
          <mat-card-header>
            <mat-card-title>Enlace caducado</mat-card-title>
            <mat-card-subtitle>
              Este enlace ya no es válido. Los enlaces de reseteo caducan a los 15 minutos o tras un
              uso.
            </mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <a mat-flat-button routerLink="/restablecer">Pedir un enlace nuevo</a>
          </mat-card-content>
        } @else {
          <mat-card-header>
            <mat-card-title>Crea una contraseña nueva</mat-card-title>
            <mat-card-subtitle>Elige una contraseña para tu cuenta.</mat-card-subtitle>
          </mat-card-header>

          @if (loading()) {
            <mat-progress-bar mode="indeterminate" />
          }

          <mat-card-content>
            <form [formGroup]="form" (ngSubmit)="submit()" class="reset__form">
              <mat-form-field appearance="outline">
                <mat-label>Contraseña nueva</mat-label>
                <input
                  matInput
                  type="password"
                  formControlName="password"
                  autocomplete="new-password"
                />
                <mat-hint
                  >Al menos 12 caracteres. No puede ser una de tus últimas contraseñas.</mat-hint
                >
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Repite la contraseña</mat-label>
                <input
                  matInput
                  type="password"
                  formControlName="confirm"
                  autocomplete="new-password"
                />
              </mat-form-field>

              @if (form.hasError('mismatch') && form.get('confirm')?.dirty) {
                <p class="reset__error" role="alert">Las contraseñas no coinciden.</p>
              }
              @if (errorMessage()) {
                <p class="reset__error" role="alert">{{ errorMessage() }}</p>
              }

              <button mat-flat-button type="submit" [disabled]="form.invalid || loading()">
                Guardar y entrar
              </button>
            </form>
          </mat-card-content>
        }
      </mat-card>
    </main>
  `,
  styles: [
    `
      .reset {
        display: flex;
        justify-content: center;
        align-items: center;
        min-height: 100vh;
        padding: 1rem;
      }
      .reset__card {
        width: 100%;
        max-width: 24rem;
      }
      .reset__form {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }
      .reset__error {
        color: var(--mat-sys-error, #b3261e);
        margin: 0 0 0.5rem;
      }
    `,
  ],
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
