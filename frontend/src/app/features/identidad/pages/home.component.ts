import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { SessionService } from '../../../core/session.service';

/**
 * Pantalla post-login del esqueleto andante (H0; maqueta identidad-acceso): confirmación de sesión
 * y datos del principal (cargado por el authGuard). La cabecera con marca, avatar y cierre de
 * sesión vive en el shell (`shared/layout/app-shell.component.ts`), que envuelve esta ruta.
 * Se sustituye por el panel real del camino crítico en Fase 1.
 */
@Component({
  selector: 'rc-home',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col">
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
        </div>
      </main>
    </div>
  `,
})
export class HomeComponent {
  private readonly sessionService = inject(SessionService);

  readonly session = this.sessionService.session;
}
