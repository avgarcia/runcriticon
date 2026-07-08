import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { SessionService } from '../core/session.service';

/**
 * Pantalla de login con contraseña (ADR-0003 D5). En H0 es el primer pixel del esqueleto andante.
 * El error es neutro (no distingue email inexistente de contraseña incorrecta).
 */
@Component({
  selector: 'rc-login',
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
    <main class="login">
      <mat-card class="login__card" appearance="outlined">
        <mat-card-header>
          <mat-card-title>Runcriticon</mat-card-title>
          <mat-card-subtitle>Inicia sesión</mat-card-subtitle>
        </mat-card-header>

        @if (loading()) {
          <mat-progress-bar mode="indeterminate" />
        }

        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="submit()" class="login__form">
            <mat-form-field appearance="outline">
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email" autocomplete="username" />
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Contraseña</mat-label>
              <input
                matInput
                type="password"
                formControlName="password"
                autocomplete="current-password"
              />
            </mat-form-field>

            @if (error()) {
              <p class="login__error" role="alert">Email o contraseña incorrectos.</p>
            }

            <button mat-flat-button type="submit" [disabled]="form.invalid || loading()">
              Entrar
            </button>
          </form>
          <a routerLink="/entrar-con-enlace" class="login__alt">Entrar con un enlace mágico</a>
          <a routerLink="/restablecer" class="login__alt">¿Has olvidado tu contraseña?</a>
        </mat-card-content>
      </mat-card>
    </main>
  `,
  styles: [
    `
      .login {
        display: flex;
        justify-content: center;
        align-items: center;
        min-height: 100vh;
        padding: 1rem;
      }
      .login__card {
        width: 100%;
        max-width: 24rem;
      }
      .login__form {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }
      .login__error {
        color: var(--mat-sys-error, #b3261e);
        margin: 0 0 0.5rem;
      }
      .login__alt {
        display: block;
        margin-top: 0.75rem;
        text-align: center;
        color: var(--mat-sys-primary, #1976d2);
      }
    `,
  ],
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
