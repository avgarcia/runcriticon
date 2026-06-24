import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { filter } from 'rxjs';
import { InviteAlumnoDialogComponent } from './invite-alumno-dialog.component';

@Component({
  selector: 'rc-alumnos',
  standalone: true,
  imports: [MatCardModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="alumnos">
      <mat-card appearance="outlined">
        <mat-card-header>
          <mat-card-title>Alumnos</mat-card-title>
          <mat-card-subtitle>
            Da de alta a los corredores del club; la invitación se envía por email
          </mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <p class="empty-state">No hay alumnos aún. Da de alta el primero.</p>
        </mat-card-content>
        <mat-card-actions>
          <button mat-flat-button (click)="openInviteDialog()">Dar de alta alumno</button>
        </mat-card-actions>
      </mat-card>
    </main>
  `,
  styles: [
    `
      .alumnos {
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
export class AlumnosComponent {
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  openInviteDialog(): void {
    this.dialog
      .open(InviteAlumnoDialogComponent)
      .afterClosed()
      .pipe(filter(Boolean))
      .subscribe((email: string) =>
        this.snackBar.open(`Invitación enviada a ${email}`, 'Cerrar', { duration: 4000 }),
      );
  }
}
