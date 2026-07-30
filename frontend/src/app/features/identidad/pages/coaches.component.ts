import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmSkeleton } from '@spartan-ng/helm/skeleton';
import { Observable, filter, from, switchMap } from 'rxjs';
import { InviteCoachDialogComponent } from '../components/invite-coach-dialog.component';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/confirm-dialog/confirm-dialog.component';
import { EntrenadoresService } from '../../../api/generated/services/entrenadores.service';
import { UsuariosService } from '../../../api/generated/services/usuarios.service';
import { CoachSummary } from '../../../api/generated/models/coach-summary';
import { ToastService } from '../../../core/toast.service';

/**
 * Pantalla admin de entrenadores (LAL-7 alta, LAL-13 gestión de sesión). Lista los entrenadores del
 * club y permite al admin **revocar sus sesiones** o **desactivar** la cuenta (ADR-0003 D11). Las
 * acciones son ayuda de UX: el backend re-autoriza cada petición (ADR-0009). Ruta protegida por
 * `adminGuard`.
 */
@Component({
  selector: 'rc-coaches',
  standalone: true,
  imports: [HlmButton, HlmSkeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="mx-auto max-w-2xl px-4 py-8">
      <div class="rounded-xl border border-border bg-card p-5">
        <header class="mb-4">
          <h1 class="text-lg font-semibold" i18n>Entrenadores</h1>
          <p class="text-sm text-muted-foreground" i18n>Gestión de entrenadores del club</p>
        </header>

        @if (coaches() === null) {
          <div class="flex flex-col gap-2">
            <hlm-skeleton class="h-5 w-1/3" />
            <hlm-skeleton class="h-4 w-full" />
            <hlm-skeleton class="h-4 w-2/3" />
          </div>
        } @else if (coaches(); as list) {
          @if (list.length === 0) {
            <p class="my-2 text-muted-foreground" i18n>
              No hay entrenadores aún. Da de alta el primero.
            </p>
          } @else {
            <ul class="m-0 flex list-none flex-col p-0">
              @for (coach of list; track coach.id) {
                <li
                  class="flex items-center justify-between gap-4 border-b border-muted py-2 last:border-0"
                >
                  <div class="flex flex-col">
                    <span class="font-medium">{{ coach.nombre }}</span>
                    <span class="text-sm text-muted-foreground">{{ coach.email }}</span>
                    <span
                      class="text-xs lowercase"
                      [class]="
                        coach.estado === 'DESACTIVADO' ? 'text-danger' : 'text-muted-foreground'
                      "
                    >
                      {{ coach.estado }}
                    </span>
                  </div>
                  <div class="flex gap-2">
                    <button hlmBtn variant="ghost" size="sm" (click)="revoke(coach)" i18n>
                      Revocar sesiones
                    </button>
                    <button
                      hlmBtn
                      variant="ghost"
                      size="sm"
                      [disabled]="coach.estado === 'DESACTIVADO'"
                      (click)="deactivate(coach)"
                      i18n
                    >
                      Desactivar
                    </button>
                  </div>
                </li>
              }
            </ul>
          }
        }

        <div class="mt-4">
          <button hlmBtn (click)="openInviteDialog()" i18n>Dar de alta entrenador</button>
        </div>
      </div>
    </main>
  `,
})
export class CoachesComponent implements OnInit {
  private readonly dialogService = inject(HlmDialogService);
  private readonly toastService = inject(ToastService);
  private readonly entrenadores = inject(EntrenadoresService);
  private readonly usuarios = inject(UsuariosService);

  /** Lista de entrenadores; `null` mientras carga. */
  readonly coaches = signal<CoachSummary[] | null>(null);

  ngOnInit(): void {
    this.reload();
  }

  openInviteDialog(): void {
    this.dialogService
      .open<string>(InviteCoachDialogComponent)
      .closed$.pipe(filter(Boolean))
      .subscribe((email) => {
        this.toastService.success($localize`Invitación enviada a ${email}:email:`);
        this.reload();
      });
  }

  revoke(coach: CoachSummary): void {
    this.confirm({
      title: $localize`Revocar sesiones`,
      message: $localize`Se cerrarán todas las sesiones activas de ${coach.nombre}:nombre:.`,
      confirmLabel: $localize`Revocar`,
    })
      .pipe(switchMap(() => from(this.usuarios.revocarSesionesUsuario({ id: coach.id }))))
      .subscribe({
        next: () => this.notifyAndReload($localize`Sesiones de ${coach.nombre}:nombre: revocadas`),
        error: () => this.toastService.error($localize`No se pudo revocar. Inténtalo de nuevo.`),
      });
  }

  deactivate(coach: CoachSummary): void {
    this.confirm({
      title: $localize`Desactivar cuenta`,
      message: $localize`${coach.nombre}:nombre: no podrá acceder hasta reactivar la cuenta. Se cerrarán sus sesiones.`,
      confirmLabel: $localize`Desactivar`,
    })
      .pipe(switchMap(() => from(this.usuarios.desactivarUsuario({ id: coach.id }))))
      .subscribe({
        next: () => this.notifyAndReload($localize`${coach.nombre}:nombre: desactivado`),
        error: () => this.toastService.error($localize`No se pudo desactivar. Inténtalo de nuevo.`),
      });
  }

  private reload(): void {
    from(this.entrenadores.listarEntrenadores()).subscribe({
      next: (list) => this.coaches.set(list),
      error: () => this.coaches.set([]),
    });
  }

  private confirm(data: ConfirmDialogData): Observable<true> {
    return this.dialogService
      .open<boolean>(ConfirmDialogComponent, { context: data })
      .closed$.pipe(filter((confirmed): confirmed is true => confirmed === true));
  }

  private notifyAndReload(message: string): void {
    this.toastService.success(message);
    this.reload();
  }
}
