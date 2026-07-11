import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import {
  HlmDialogClose,
  HlmDialogFooter,
  HlmDialogHeader,
  HlmDialogTitle,
} from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { AlumnosService } from '../../../api/generated/services/alumnos.service';
import { fieldOf, messageForError } from '../../../core/api/error-codes';

@Component({
  selector: 'rc-invite-alumno-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogFooter,
    HlmDialogClose,
    HlmButton,
    HlmInput,
    HlmLabel,
    HlmSpinner,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div hlmDialogHeader>
      <h2 hlmDialogTitle i18n>Dar de alta alumno</h2>
    </div>

    <form
      [formGroup]="form"
      id="invite-form"
      (ngSubmit)="submit()"
      class="flex min-w-80 flex-col gap-4 pt-2"
    >
      <div class="flex flex-col gap-1.5">
        <label hlmLabel for="name" i18n>Nombre</label>
        <input hlmInput id="name" formControlName="name" autocomplete="name" />
      </div>
      <div class="flex flex-col gap-1.5">
        <label hlmLabel for="email" i18n>Email</label>
        <input hlmInput id="email" type="email" formControlName="email" autocomplete="email" />
        @if (form.controls.email.hasError('backend')) {
          <p class="text-xs text-danger">{{ form.controls.email.getError('backend') }}</p>
        }
      </div>
      @if (errorMessage()) {
        <p class="text-sm text-danger" role="alert">{{ errorMessage() }}</p>
      }
    </form>

    <div hlmDialogFooter>
      <button hlmBtn variant="outline" type="button" hlmDialogClose i18n>Cancelar</button>
      <button hlmBtn type="submit" form="invite-form" [disabled]="form.invalid || loading()">
        @if (loading()) {
          <hlm-spinner aria-label="Enviando" i18n-aria-label />
        }
        <span i18n>Enviar invitación</span>
      </button>
    </div>
  `,
})
export class InviteAlumnoDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject(BrnDialogRef<string>);
  private readonly alumnosService = inject(AlumnosService);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
  });

  async submit(): Promise<void> {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.errorMessage.set(null);
    this.form.controls.email.setErrors(null);
    const { name, email } = this.form.getRawValue();
    try {
      await this.alumnosService.invitarAlumno({ body: { nombre: name, email } });
      this.dialogRef.close(email);
    } catch (err) {
      this.loading.set(false);
      if (err instanceof HttpErrorResponse && err.status === 409) {
        this.errorMessage.set($localize`Ya existe un alumno con ese email.`);
      } else if (fieldOf(err) === 'email') {
        this.form.controls.email.setErrors({ backend: messageForError(err) });
      } else {
        this.errorMessage.set(messageForError(err));
      }
    }
  }
}
