import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
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
 * Pantalla de cambio obligatorio de contraseña por caducidad (LAL-10, ADR-0003 D7). No es accesible
 * por URL directa: se llega desde el login cuando la contraseña ha caducado (respuesta
 * `PASSWORD_EXPIRED`). Recupera de memoria las credenciales caducadas (handoff del login); si no las
 * hay (p. ej. recarga de página) vuelve al login. Al fijar la nueva, el backend inicia la sesión.
 */
@Component({
  selector: 'rc-force-password-change',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressBarModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="change">
      <mat-card class="change__card" appearance="outlined">
        <mat-card-header>
          <mat-card-title>Tu contraseña ha caducado</mat-card-title>
          <mat-card-subtitle>Por seguridad, crea una contraseña nueva para continuar.</mat-card-subtitle>
        </mat-card-header>

        @if (loading()) {
          <mat-progress-bar mode="indeterminate" />
        }

        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="submit()" class="change__form">
            <mat-form-field appearance="outline">
              <mat-label>Contraseña nueva</mat-label>
              <input matInput type="password" formControlName="password" autocomplete="new-password" />
              <mat-hint>Al menos 12 caracteres. No puede ser una de tus últimas contraseñas.</mat-hint>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Repite la contraseña</mat-label>
              <input matInput type="password" formControlName="confirm" autocomplete="new-password" />
            </mat-form-field>

            @if (form.hasError('mismatch') && form.get('confirm')?.dirty) {
              <p class="change__error" role="alert">Las contraseñas no coinciden.</p>
            }
            @if (errorMessage()) {
              <p class="change__error" role="alert">{{ errorMessage() }}</p>
            }

            <button mat-flat-button type="submit" [disabled]="form.invalid || loading()">
              Guardar y entrar
            </button>
          </form>
        </mat-card-content>
      </mat-card>
    </main>
  `,
  styles: [
    `
      .change {
        display: flex;
        justify-content: center;
        align-items: center;
        min-height: 100vh;
        padding: 1rem;
      }
      .change__card {
        width: 100%;
        max-width: 24rem;
      }
      .change__form {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }
      .change__error {
        color: var(--mat-sys-error, #b3261e);
        margin: 0 0 0.5rem;
      }
    `,
  ],
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
