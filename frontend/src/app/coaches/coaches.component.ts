import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, filter, from, switchMap } from 'rxjs';
import { InviteCoachDialogComponent } from './invite-coach-dialog.component';
import { ConfirmDialogComponent, ConfirmDialogData } from './confirm-dialog.component';
import { EntrenadoresService } from '../api/generated/services/entrenadores.service';
import { UsuariosService } from '../api/generated/services/usuarios.service';
import { CoachSummary } from '../api/generated/models/coach-summary';

/**
 * Pantalla admin de entrenadores (LAL-7 alta, LAL-13 gestión de sesión). Lista los entrenadores del
 * club y permite al admin **revocar sus sesiones** o **desactivar** la cuenta (ADR-0003 D11). Las
 * acciones son ayuda de UX: el backend re-autoriza cada petición (ADR-0009). Ruta protegida por
 * `adminGuard`.
 */
@Component({
  selector: 'rc-coaches',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatProgressBarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="coaches">
      <mat-card appearance="outlined">
        <mat-card-header>
          <mat-card-title>Entrenadores</mat-card-title>
          <mat-card-subtitle>Gestión de entrenadores del club</mat-card-subtitle>
        </mat-card-header>

        @if (coaches() === null) {
          <mat-progress-bar mode="indeterminate" />
        }

        <mat-card-content>
          @if (coaches(); as list) {
            @if (list.length === 0) {
              <p class="empty-state">No hay entrenadores aún. Da de alta el primero.</p>
            } @else {
              <ul class="coach-list">
                @for (coach of list; track coach.id) {
                  <li class="coach">
                    <div class="coach__info">
                      <span class="coach__name">{{ coach.nombre }}</span>
                      <span class="coach__email">{{ coach.email }}</span>
                      <span class="coach__status" [attr.data-status]="coach.estado">{{
                        coach.estado
                      }}</span>
                    </div>
                    <div class="coach__actions">
                      <button mat-button (click)="revoke(coach)">Revocar sesiones</button>
                      <button
                        mat-button
                        [disabled]="coach.estado === 'DESACTIVADO'"
                        (click)="deactivate(coach)"
                      >
                        Desactivar
                      </button>
                    </div>
                  </li>
                }
              </ul>
            }
          }
        </mat-card-content>

        <mat-card-actions>
          <button mat-flat-button (click)="openInviteDialog()">Dar de alta entrenador</button>
        </mat-card-actions>
      </mat-card>
    </main>
  `,
  styles: [
    `
      .coaches {
        max-width: 40rem;
        margin: 2rem auto;
        padding: 0 1rem;
      }
      .empty-state {
        color: var(--mat-sys-on-surface-variant, #49454f);
        margin: 0.5rem 0;
      }
      .coach-list {
        list-style: none;
        margin: 0;
        padding: 0;
      }
      .coach {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 1rem;
        padding: 0.5rem 0;
        border-bottom: 1px solid var(--mat-sys-outline-variant, #cac4d0);
      }
      .coach__info {
        display: flex;
        flex-direction: column;
      }
      .coach__email {
        color: var(--mat-sys-on-surface-variant, #49454f);
        font-size: 0.85rem;
      }
      .coach__status {
        font-size: 0.75rem;
        text-transform: lowercase;
      }
      .coach__status[data-status='DESACTIVADO'] {
        color: var(--mat-sys-error, #b3261e);
      }
    `,
  ],
})
export class CoachesComponent implements OnInit {
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly entrenadores = inject(EntrenadoresService);
  private readonly usuarios = inject(UsuariosService);

  /** Lista de entrenadores; `null` mientras carga. */
  readonly coaches = signal<CoachSummary[] | null>(null);

  ngOnInit(): void {
    this.reload();
  }

  openInviteDialog(): void {
    this.dialog
      .open(InviteCoachDialogComponent)
      .afterClosed()
      .pipe(filter(Boolean))
      .subscribe((email: string) => {
        this.snackBar.open(`Invitación enviada a ${email}`, 'Cerrar', { duration: 4000 });
        this.reload();
      });
  }

  revoke(coach: CoachSummary): void {
    this.confirm({
      title: 'Revocar sesiones',
      message: `Se cerrarán todas las sesiones activas de ${coach.nombre}.`,
      confirmLabel: 'Revocar',
    })
      .pipe(switchMap(() => from(this.usuarios.revocarSesionesUsuario({ id: coach.id }))))
      .subscribe({
        next: () => this.notifyAndReload(`Sesiones de ${coach.nombre} revocadas`),
        error: () =>
          this.snackBar.open('No se pudo revocar. Inténtalo de nuevo.', 'Cerrar', { duration: 4000 }),
      });
  }

  deactivate(coach: CoachSummary): void {
    this.confirm({
      title: 'Desactivar cuenta',
      message: `${coach.nombre} no podrá acceder hasta reactivar la cuenta. Se cerrarán sus sesiones.`,
      confirmLabel: 'Desactivar',
    })
      .pipe(switchMap(() => from(this.usuarios.desactivarUsuario({ id: coach.id }))))
      .subscribe({
        next: () => this.notifyAndReload(`${coach.nombre} desactivado`),
        error: () =>
          this.snackBar.open('No se pudo desactivar. Inténtalo de nuevo.', 'Cerrar', { duration: 4000 }),
      });
  }

  private reload(): void {
    from(this.entrenadores.listarEntrenadores()).subscribe({
      next: (list) => this.coaches.set(list),
      error: () => this.coaches.set([]),
    });
  }

  private confirm(data: ConfirmDialogData): Observable<true> {
    return this.dialog
      .open(ConfirmDialogComponent, { data })
      .afterClosed()
      .pipe(filter((confirmed): confirmed is true => confirmed === true));
  }

  private notifyAndReload(message: string): void {
    this.snackBar.open(message, 'Cerrar', { duration: 4000 });
    this.reload();
  }
}
