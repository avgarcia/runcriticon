import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivacionService } from '../api/generated/services/activacion.service';
import { SessionService } from '../core/session.service';
import { ErrorBannerComponent } from '../shared/error-banner/error-banner.component';
import { PasswordStrengthComponent } from '../shared/password-strength/password-strength.component';

/** Validador de grupo: la confirmación debe coincidir con la contraseña. */
function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const confirm = group.get('confirm')?.value;
  return password === confirm ? null : { mismatch: true };
}

/**
 * Pantalla pública de activación de cuenta por invitación (LAL-9, ADR-0003 D4/D6). El invitado abre
 * `…/activar?token=…` desde el email, fija una contraseña y entra (auto-login). La validación de la
 * política la manda el backend; aquí solo se replica la longitud y la coincidencia para UX.
 */
@Component({
  selector: 'rc-activate',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressBarModule,
    ErrorBannerComponent,
    PasswordStrengthComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="activate">
      <mat-card class="activate__card" appearance="outlined">
        <mat-card-header>
          <mat-card-title>Activa tu cuenta</mat-card-title>
          <mat-card-subtitle>Tu club te ha invitado a Runcriticon. Elige una contraseña para entrar.</mat-card-subtitle>
        </mat-card-header>

        @if (loading()) {
          <mat-progress-bar mode="indeterminate" />
        }

        <mat-card-content>
          @if (!hasToken) {
            <rc-error-banner>
              El enlace no es válido. Pide al administrador o a tu entrenador que te reenvíe la invitación.
            </rc-error-banner>
          } @else {
            <form [formGroup]="form" (ngSubmit)="submit()" class="activate__form">
              <mat-form-field appearance="outline">
                <mat-label>Contraseña</mat-label>
                <input matInput type="password" formControlName="password" autocomplete="new-password" />
                <mat-hint>Al menos 12 caracteres.</mat-hint>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Repite la contraseña</mat-label>
                <input matInput type="password" formControlName="confirm" autocomplete="new-password" />
              </mat-form-field>

              <rc-password-strength
                [password]="form.controls.password.value"
                [confirm]="form.controls.confirm.value"
              />

              @if (form.hasError('mismatch') && form.get('confirm')?.dirty) {
                <rc-error-banner>Las contraseñas no coinciden.</rc-error-banner>
              }
              @if (errorMessage()) {
                <rc-error-banner>{{ errorMessage() }}</rc-error-banner>
              }

              <button mat-flat-button type="submit" [disabled]="form.invalid || loading()">
                Activar mi cuenta
              </button>
            </form>
          }
        </mat-card-content>
      </mat-card>
    </main>
  `,
  styles: [
    `
      .activate {
        display: flex;
        justify-content: center;
        align-items: center;
        min-height: 100vh;
        padding: 1rem;
      }
      .activate__card {
        width: 100%;
        max-width: 24rem;
      }
      .activate__form {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }
    `,
  ],
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

  readonly form = this.fb.nonNullable.group(
    {
      password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
      confirm: ['', [Validators.required]],
    },
    { validators: passwordsMatch },
  );

  async submit(): Promise<void> {
    if (this.form.invalid || !this.token) {
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    const { password } = this.form.getRawValue();
    try {
      await this.activacionService.activarCuenta({ body: { token: this.token, password } });
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
      if (err.status === 409) {
        return 'Tu cuenta ya está activa. Inicia sesión.';
      }
      if (err.status === 400) {
        return 'El enlace no es válido o ha caducado, o la contraseña no cumple los requisitos. Pide que te reenvíen la invitación.';
      }
    }
    return 'No se ha podido activar la cuenta. Inténtalo de nuevo.';
  }
}
