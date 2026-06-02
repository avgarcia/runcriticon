import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { SesionService } from '../core/sesion.service';

/**
 * Pantalla post-login del esqueleto andante (H0): muestra el principal de la sesión (cargado por
 * el authGuard) y permite cerrar sesión. Se sustituye por el panel real del camino crítico en
 * Fase 1.
 */
@Component({
  selector: 'rc-home',
  standalone: true,
  imports: [MatCardModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="home">
      <mat-card class="home__card" appearance="outlined">
        <mat-card-header>
          <mat-card-title>Runcriticon</mat-card-title>
          <mat-card-subtitle>Esqueleto andante — Hito H0</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          @if (sesion(); as s) {
            <p>Sesión iniciada. Rol: <strong>{{ s.rol }}</strong></p>
            <dl class="home__datos">
              <dt>Usuario</dt>
              <dd>{{ s.userId }}</dd>
              <dt>Club</dt>
              <dd>{{ s.clubId }}</dd>
            </dl>
          } @else {
            <p>Cargando sesión…</p>
          }
        </mat-card-content>
        <mat-card-actions>
          <button mat-flat-button (click)="cerrar()">Cerrar sesión</button>
        </mat-card-actions>
      </mat-card>
    </main>
  `,
  styles: [
    `
      .home {
        display: flex;
        justify-content: center;
        align-items: center;
        min-height: 100vh;
        padding: 1rem;
      }
      .home__card {
        max-width: 28rem;
      }
      .home__datos {
        font-family: monospace;
        font-size: 0.85rem;
        margin: 0;
      }
      .home__datos dd {
        margin: 0 0 0.5rem;
      }
    `,
  ],
})
export class HomeComponent {
  private readonly sesionService = inject(SesionService);
  private readonly router = inject(Router);

  readonly sesion = this.sesionService.sesion;

  cerrar(): void {
    this.sesionService.cerrar().subscribe({
      next: () => void this.router.navigate(['/login']),
      error: () => void this.router.navigate(['/login']),
    });
  }
}
