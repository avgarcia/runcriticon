import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { filter } from 'rxjs';
import { InviteAlumnoDialogComponent } from './invite-alumno-dialog.component';
import { ToastService } from '../core/toast.service';

@Component({
  selector: 'rc-alumnos',
  standalone: true,
  imports: [HlmButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="mx-auto max-w-2xl px-4 py-8">
      <div class="rounded-xl border border-border bg-card p-5">
        <header class="mb-4">
          <h1 class="text-lg font-semibold">Alumnos</h1>
          <p class="text-sm text-muted-foreground">
            Da de alta a los corredores del club; la invitación se envía por email
          </p>
        </header>

        <p class="my-2 text-muted-foreground">No hay alumnos aún. Da de alta el primero.</p>

        <div class="mt-4">
          <button hlmBtn (click)="openInviteDialog()">Dar de alta alumno</button>
        </div>
      </div>
    </main>
  `,
})
export class AlumnosComponent {
  private readonly dialogService = inject(HlmDialogService);
  private readonly toastService = inject(ToastService);

  openInviteDialog(): void {
    this.dialogService
      .open<string>(InviteAlumnoDialogComponent)
      .closed$.pipe(filter(Boolean))
      .subscribe((email) => this.toastService.success(`Invitación enviada a ${email}`));
  }
}
