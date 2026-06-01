import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';

/**
 * Pantalla trivial del esqueleto andante (H0). Demuestra que Angular + Material 3
 * renderizan y que el build produce un bundle válido. Se sustituye por las pantallas
 * reales del camino crítico en Fase 1.
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
          <p>
            El frontend Angular + Material está en marcha. Esta pantalla es
            provisional: el camino crítico (login, plan semanal, vista del
            alumno) llega en las fases siguientes.
          </p>
        </mat-card-content>
        <mat-card-actions>
          <button mat-flat-button disabled>Iniciar sesión (Bloque 5)</button>
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
    `,
  ],
})
export class HomeComponent {}
