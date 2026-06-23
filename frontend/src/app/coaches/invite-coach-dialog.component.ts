import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { CoachesService } from './coaches.service';

@Component({
  selector: 'rc-invite-coach-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressBarModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Dar de alta entrenador</h2>

    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    <mat-dialog-content>
      <form [formGroup]="form" id="invite-form" (ngSubmit)="submit()" class="invite-form">
        <mat-form-field appearance="outline">
          <mat-label>Nombre</mat-label>
          <input matInput formControlName="name" autocomplete="name" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Email</mat-label>
          <input matInput type="email" formControlName="email" autocomplete="email" />
        </mat-form-field>
        @if (errorMessage()) {
          <p class="error" role="alert">{{ errorMessage() }}</p>
        }
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close type="button">Cancelar</button>
      <button
        mat-flat-button
        type="submit"
        form="invite-form"
        [disabled]="form.invalid || loading()"
      >
        Enviar invitación
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .invite-form {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        min-width: 20rem;
        padding-top: 0.5rem;
      }
      .error {
        color: var(--mat-sys-error, #b3261e);
        font-size: 0.875rem;
        margin: 0 0 0.5rem;
      }
    `,
  ],
})
export class InviteCoachDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject(MatDialogRef<InviteCoachDialogComponent>);
  private readonly coachesService = inject(CoachesService);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
  });

  submit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.errorMessage.set(null);
    const { name, email } = this.form.getRawValue();
    this.coachesService.invite(name, email).subscribe({
      next: () => this.dialogRef.close(email),
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        if (err.status === 409) {
          this.errorMessage.set('Ya existe un entrenador con ese email.');
        } else if (err.status === 400 && err.error?.message) {
          this.errorMessage.set(err.error.message as string);
        } else {
          this.errorMessage.set('No se ha podido enviar la invitación.');
        }
      },
    });
  }
}
