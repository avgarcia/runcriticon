import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { filter } from 'rxjs';
import { InviteCoachDialogComponent } from './invite-coach-dialog.component';

@Component({
  selector: 'rc-coaches',
  standalone: true,
  imports: [MatCardModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="coaches">
      <mat-card appearance="outlined">
        <mat-card-header>
          <mat-card-title>Entrenadores</mat-card-title>
          <mat-card-subtitle>Gestión de entrenadores del club</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <p class="empty-state">No hay entrenadores aún. Da de alta el primero.</p>
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
    `,
  ],
})
export class CoachesComponent {
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  openInviteDialog(): void {
    this.dialog
      .open(InviteCoachDialogComponent)
      .afterClosed()
      .pipe(filter(Boolean))
      .subscribe((email: string) =>
        this.snackBar.open(`Invitación enviada a ${email}`, 'Cerrar', { duration: 4000 }),
      );
  }
}
