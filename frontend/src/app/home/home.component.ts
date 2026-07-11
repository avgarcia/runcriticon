import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { HlmButton } from '@spartan-ng/helm/button';
import { SessionService } from '../core/session.service';

/**
 * Pantalla post-login del esqueleto andante (H0; maqueta identidad-acceso): header de app con
 * avatar de iniciales, confirmación de sesión y datos del principal (cargado por el authGuard).
 * Se sustituye por el panel real del camino crítico en Fase 1.
 */
@Component({
  selector: 'rc-home',
  standalone: true,
  imports: [HlmButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex min-h-screen flex-col">
      <header class="flex h-[52px] items-center gap-2.5 border-b border-muted bg-card px-4">
        <div
          class="flex flex-1 items-center gap-2 text-[15px] font-semibold tracking-[-0.2px]"
          i18n
        >
          <img src="logo-mark.svg" alt="" class="size-6" />
          Runcriticon
        </div>
        <div
          class="flex size-8 items-center justify-center rounded-full bg-primary-soft text-xs font-semibold text-primary"
          [attr.aria-label]="sessionAriaLabel()"
        >
          <!-- TODO(H1): iniciales reales cuando /me devuelva el nombre del usuario -->
          {{ initials() }}
        </div>
      </header>

      <main class="flex flex-1 flex-col items-center justify-center gap-5 p-6 text-center">
        <div class="mx-auto w-full max-w-sm flex flex-col gap-5">
          <div
            class="mx-auto flex size-[68px] items-center justify-center rounded-full border border-success-border bg-success-soft text-[32px] text-success"
            aria-hidden="true"
          >
            ✓
          </div>
          <div>
            <h1 class="text-[22px] font-semibold tracking-[-0.4px]" i18n>¡Estás dentro!</h1>
            <p class="mt-1.5 text-[13.5px] leading-relaxed text-muted-foreground" i18n>
              Aquí empezará tu app: tu sesión de hoy, tus grupos o el panel del club según tu rol.
            </p>
          </div>

          @if (session(); as s) {
            <dl class="rounded-xl bg-muted p-4 text-left text-[13px] leading-relaxed">
              <div class="mb-2 text-[11px] font-semibold text-muted-foreground" i18n>
                SESIÓN ACTIVA
              </div>
              <div class="flex gap-2">
                <dt class="font-medium" i18n>Rol</dt>
                <dd class="m-0">{{ s.role }}</dd>
              </div>
              <div class="flex gap-2 font-mono text-[12px]">
                <dt class="font-medium font-sans text-[13px]" i18n>Usuario</dt>
                <dd class="m-0 self-center">{{ s.userId }}</dd>
              </div>
              <div class="flex gap-2 font-mono text-[12px]">
                <dt class="font-medium font-sans text-[13px]" i18n>Club</dt>
                <dd class="m-0 self-center">{{ s.clubId }}</dd>
              </div>
            </dl>
          } @else {
            <p class="text-[13.5px] text-muted-foreground" i18n>Cargando sesión…</p>
          }

          <button hlmBtn variant="outline" size="lg" class="w-full" (click)="close()">
            <span i18n>Cerrar sesión</span>
          </button>
        </div>
      </main>
    </div>
  `,
})
export class HomeComponent {
  private readonly sessionService = inject(SessionService);
  private readonly router = inject(Router);

  readonly session = this.sessionService.session;

  /** Inicial del rol como avatar provisional (no hay nombre en el principal en H0). */
  readonly initials = computed(() => this.session()?.role.charAt(0).toUpperCase() ?? '·');

  readonly sessionAriaLabel = computed(() => {
    const role = this.session()?.role ?? '';
    return $localize`Sesión de ${role}:role:`;
  });

  close(): void {
    this.sessionService.close().subscribe({
      next: () => void this.router.navigate(['/login']),
      error: () => void this.router.navigate(['/login']),
    });
  }
}
