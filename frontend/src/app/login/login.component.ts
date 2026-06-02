import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { SesionService } from '../core/sesion.service';

/**
 * Pantalla de login con contraseña (ADR-0003 D5). En H0 es el primer pixel del esqueleto andante.
 * El error es neutro (no distingue email inexistente de contraseña incorrecta).
 */
@Component({
  selector: 'rc-login',
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
    <main class="login">
      <mat-card class="login__card" appearance="outlined">
        <mat-card-header>
          <mat-card-title>Runcriticon</mat-card-title>
          <mat-card-subtitle>Inicia sesión</mat-card-subtitle>
        </mat-card-header>

        @if (cargando()) {
          <mat-progress-bar mode="indeterminate" />
        }

        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="enviar()" class="login__form">
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

            <button mat-flat-button type="submit" [disabled]="form.invalid || cargando()">
              Entrar
            </button>
          </form>
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
    `,
  ],
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);

  readonly cargando = signal(false);
  readonly error = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  enviar(): void {
    if (this.form.invalid) {
      return;
    }
    this.cargando.set(true);
    this.error.set(false);
    const { email, password } = this.form.getRawValue();
    this.sesion.iniciar(email, password).subscribe({
      next: () => void this.router.navigate(['/']),
      error: () => {
        this.error.set(true);
        this.cargando.set(false);
      },
    });
  }
}
