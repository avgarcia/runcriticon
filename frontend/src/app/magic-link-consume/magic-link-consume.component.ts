import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { SessionService } from '../core/session.service';

/**
 * Pantalla de consumo del magic link (LAL-11, ADR-0003 D5; wireframe frame 4). Se llega desde el
 * enlace del email (`…/entrar?token=…`). **Auto-consume al abrir**: lee el token de la query y crea
 * sesión sin intervención; los escáneres de enlaces que solo hacen GET cargan el HTML pero no
 * ejecutan este POST. Si el enlace ha caducado o ya se usó, ofrece pedir uno nuevo.
 */
@Component({
  selector: 'rc-magic-link-consume',
  standalone: true,
  imports: [RouterLink, MatCardModule, MatButtonModule, MatProgressBarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="magic">
      <mat-card class="magic__card" appearance="outlined">
        @if (failed()) {
          <mat-card-header>
            <mat-card-title>Enlace caducado</mat-card-title>
            <mat-card-subtitle>
              Este enlace ya no es válido. Los enlaces caducan a los 15 minutos o tras un uso.
            </mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <a mat-flat-button routerLink="/entrar-con-enlace">Pedir un enlace nuevo</a>
          </mat-card-content>
        } @else {
          <mat-card-header>
            <mat-card-title>Entrando…</mat-card-title>
            <mat-card-subtitle>Te estamos identificando con tu enlace.</mat-card-subtitle>
          </mat-card-header>
          <mat-progress-bar mode="indeterminate" />
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
    `,
  ],
})
export class MagicLinkConsumeComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);

  readonly failed = signal(false);

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.failed.set(true);
      return;
    }
    this.session.consumeMagicLink(token).subscribe({
      next: () => void this.router.navigate(['/']),
      error: () => this.failed.set(true),
    });
  }
}
