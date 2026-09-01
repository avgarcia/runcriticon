import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { HlmButton } from '@spartan-ng/helm/button';
import { SessionService } from '../../core/session.service';

/**
 * Shell del alumno (LAL-29): appbar mínima —marca y avatar, sin navegación lateral—, mobile-first
 * (`max-width` la fija cada pantalla hija, `plantilla vista-hoy-alumno.html`). Deliberadamente
 * distinto de `AppShellComponent`: aquel es desktop-first (`lg:grid-cols-[240px_1fr]`) con toda su
 * navegación detrás de permisos de ADMIN/ENTRENADOR — un ALUMNO no ve ningún enlace ahí.
 *
 * Cuelga de su propia ruta de nivel raíz (`/mi-plan`), no de `path: ''` del shell de gestión: dos
 * rutas raíz con `path: ''` compitiendo rompía `authGuard` (ver `app.routes.ts`), así que este shell
 * vive fuera de ese árbol con su propio `studentGuard`.
 */
@Component({
  selector: 'rc-student-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, HlmButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex min-h-screen flex-col bg-background">
      <header
        class="sticky top-0 z-10 flex h-[52px] items-center justify-between border-b border-muted bg-card px-4"
      >
        <div class="flex items-center gap-2 text-[15px] font-semibold tracking-[-0.2px]">
          <img src="logo-mark.svg" alt="" class="size-6" />
          <span i18n>Runcriticon</span>
        </div>
        <div class="flex items-center gap-3">
          <a routerLink="/mis-marcas" class="text-[13px] font-medium text-muted-foreground hover:text-foreground" i18n>
            Mis marcas
          </a>
          <a routerLink="/mi-cuenta" class="text-[13px] font-medium text-muted-foreground hover:text-foreground" i18n>
            Mi cuenta
          </a>
          <div
            class="flex size-8 items-center justify-center rounded-full bg-primary-soft text-xs font-semibold text-primary"
            [attr.aria-label]="sessionAriaLabel()"
          >
            {{ initials() }}
          </div>
          <button hlmBtn variant="ghost" size="sm" (click)="close()">
            <span i18n>Cerrar sesión</span>
          </button>
        </div>
      </header>

      <main class="flex-1 px-4 py-6">
        <router-outlet />
      </main>
    </div>
  `,
})
export class StudentShellComponent {
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
