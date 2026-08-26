import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmSkeleton } from '@spartan-ng/helm/skeleton';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { filter } from 'rxjs';
import { messageForError } from '../../../core/api/error-codes';
import { ConsentService, MyConsent } from '../../../core/consent.service';
import { ToastService } from '../../../core/toast.service';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/confirm-dialog/confirm-dialog.component';

/** `"2026-08-25T10:00:00Z"` → `"25/08/2026"`. Sin depender de `DatePipe`/locale (ADR-0012 D9: sin
 * `registerLocaleData` en el proyecto), igual que `date-format-es.ts` de seguimiento — pero esta
 * pantalla no importa de esa feature (frontera entre features), así que se formatea aquí mismo. */
function formatDateEs(iso: string): string {
  const date = new Date(iso);
  const day = String(date.getDate()).padStart(2, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  return `${day}/${month}/${date.getFullYear()}`;
}

/**
 * "Mi cuenta" del alumno (LAL-128): hogar de la concesión pendiente y la revocación del
 * consentimiento explícito de datos de salud (Art. 9.2.a RGPD, ADR-0014 D16/D18). Solo el ALUMNO
 * llega aquí — es el único interesado de los datos que captura `seguimiento.reporte_sesion` — pero la
 * ruta no impone un guard de rol propio más allá de `studentGuard` (mismo criterio que `/mi-plan`).
 *
 * Tres estados posibles según `MiConsentimientoResponse.estado`:
 * - `PENDIENTE`: nunca ha concedido (activó su cuenta antes de que existiera este mecanismo).
 * - `VIGENTE`: consentimiento activo — puede revocarlo, con confirmación explicando la consecuencia.
 * - `REVOCADO`: puede volver a conceder.
 */
@Component({
  selector: 'rc-my-account',
  standalone: true,
  imports: [HlmButton, HlmSkeleton, HlmSpinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto max-w-[560px]">
      <h1 class="mb-5 text-2xl font-semibold tracking-[-0.3px]" i18n>Mi cuenta</h1>

      <section class="rounded-2xl border border-border bg-card p-5">
        <h2 class="mb-1 text-base font-semibold" i18n>Consentimiento de datos de salud</h2>
        <p class="mb-4 text-[13px] text-muted-foreground" i18n>
          Cubre las sensaciones y molestias que registras al reportar tus sesiones de entrenamiento.
        </p>

        @if (consent(); as loaded) {
          @switch (loaded.estado) {
            @case ('PENDIENTE') {
              <p class="mb-3 text-sm text-foreground" i18n>
                Todavía no has dado tu consentimiento. Sin él no podrás reportar tus sesiones.
              </p>
              <button hlmBtn [disabled]="saving()" (click)="grant()">
                @if (saving()) {
                  <hlm-spinner aria-label="Guardando" i18n-aria-label />
                }
                <span i18n>Dar mi consentimiento</span>
              </button>
            }
            @case ('VIGENTE') {
              <p class="mb-3 text-sm text-foreground">
                <span class="font-medium text-success" i18n>Vigente</span>
                @if (loaded.concedidoEn) {
                  <span i18n> · concedido el {{ formatDate(loaded.concedidoEn) }}</span>
                }
              </p>
              <button hlmBtn variant="outline" class="text-danger" [disabled]="saving()" (click)="confirmRevoke()">
                @if (saving()) {
                  <hlm-spinner aria-label="Guardando" i18n-aria-label />
                }
                <span i18n>Revocar consentimiento</span>
              </button>
            }
            @case ('REVOCADO') {
              <p class="mb-3 text-sm text-foreground">
                <span class="font-medium text-muted-foreground" i18n>Revocado</span>
                @if (loaded.revocadoEn) {
                  <span i18n> · el {{ formatDate(loaded.revocadoEn) }}</span>
                }
              </p>
              <button hlmBtn [disabled]="saving()" (click)="grant()">
                @if (saving()) {
                  <hlm-spinner aria-label="Guardando" i18n-aria-label />
                }
                <span i18n>Volver a dar mi consentimiento</span>
              </button>
            }
          }
        } @else if (loadFailed()) {
          <p class="text-sm text-muted-foreground" role="alert" i18n>No se ha podido cargar tu estado.</p>
          <button hlmBtn variant="outline" class="mt-3" (click)="reload()" i18n>Reintentar</button>
        } @else {
          <hlm-skeleton class="h-9 w-48" />
        }
      </section>
    </div>
  `,
})
export class MyAccountComponent implements OnInit {
  private readonly consentService = inject(ConsentService);
  private readonly dialogService = inject(HlmDialogService);
  private readonly toastService = inject(ToastService);

  readonly consent = signal<MyConsent | undefined>(undefined);
  readonly loadFailed = signal(false);
  readonly saving = signal(false);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loadFailed.set(false);
    this.consentService.getMyConsent().subscribe({
      next: (consent) => this.consent.set(consent),
      error: () => this.loadFailed.set(true),
    });
  }

  formatDate(iso: string): string {
    return formatDateEs(iso);
  }

  grant(): void {
    this.saving.set(true);
    this.consentService.grant().subscribe({
      next: (consent) => {
        this.consent.set(consent);
        this.saving.set(false);
        this.toastService.success($localize`Consentimiento registrado.`);
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  confirmRevoke(): void {
    const data: ConfirmDialogData = {
      title: $localize`Revocar consentimiento`,
      message: $localize`Sin consentimiento vigente no podrás reportar nuevas sesiones hasta que vuelvas a darlo. ¿Seguro que quieres revocarlo?`,
      confirmLabel: $localize`Revocar`,
    };
    this.dialogService
      .open<boolean>(ConfirmDialogComponent, { context: data })
      .closed$.pipe(filter((confirmed): confirmed is true => confirmed === true))
      .subscribe(() => this.revoke());
  }

  private revoke(): void {
    this.saving.set(true);
    this.consentService.revoke().subscribe({
      next: (consent) => {
        this.consent.set(consent);
        this.saving.set(false);
        this.toastService.success($localize`Consentimiento revocado.`);
      },
      error: (err: unknown) => this.handleError(err),
    });
  }

  private handleError(err: unknown): void {
    this.saving.set(false);
    // El 403 ya lo avisa el interceptor global con su toast; no duplicamos el mensaje.
    if (err instanceof HttpErrorResponse && err.status === 403) return;
    this.toastService.error(messageForError(err));
  }
}
