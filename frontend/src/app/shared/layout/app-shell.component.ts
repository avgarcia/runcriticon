import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { HlmButton } from '@spartan-ng/helm/button';
import { ClubService } from '../../core/club.service';
import { GroupService } from '../../core/group.service';
import { PermissionsService } from '../../core/permissions.service';
import { SessionService } from '../../core/session.service';
import { TaxonomyService } from '../../core/taxonomy.service';

/**
 * Shell de la app autenticada (maqueta `docs/diseno/editor-taxonomia.html`): barra superior con
 * marca, nombre del club y avatar, más navegación lateral. Envuelve como ruta padre a todas las
 * pantallas con sesión; las de acceso (login, activación, magic link, reseteo) quedan fuera y por
 * eso no lo ven.
 *
 * Qué se oculta y con qué criterio: «Taxonomía» y «Ajustes del club» van por permiso
 * (`TAXONOMY:MANAGE` y `CLUB:UPDATE`), que son claves que expone la matriz — para la taxonomía se
 * usa `MANAGE` y no `LIST` porque el entrenador también tiene `LIST` y esta pantalla es el editor
 * del admin. «Entrenadores» y «Alumnos» van por rol, porque la matriz del backend
 * no tiene hoy una clave de listado de alumnos (no existe `STUDENT:LIST`) e inventarla en el
 * cliente sería fingir un contrato que no existe. En ambos casos es ayuda de UX: la ruta la
 * protege su guard y el backend re-autoriza.
 */
@Component({
  selector: 'rc-app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, HlmButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex min-h-screen flex-col">
      <header
        class="sticky top-0 z-10 flex h-[52px] items-center gap-2.5 border-b border-muted bg-card px-4"
      >
        <div class="flex items-center gap-2 text-[15px] font-semibold tracking-[-0.2px]">
          <img src="logo-mark.svg" alt="" class="size-6" />
          <span i18n>Runcriticon</span>
        </div>

        <div class="flex flex-1 items-center justify-end gap-4">
          @if (club(); as c) {
            <span class="border-r border-border pr-4 text-[13px] text-muted-foreground">{{
              c.nombre
            }}</span>
          }
          <div
            class="flex size-8 items-center justify-center rounded-full bg-primary-soft text-xs font-semibold text-primary"
            [attr.aria-label]="sessionAriaLabel()"
          >
            <!-- TODO(H1): iniciales reales cuando /me devuelva el nombre del usuario -->
            {{ initials() }}
          </div>
          <button hlmBtn variant="ghost" size="sm" (click)="close()">
            <span i18n>Cerrar sesión</span>
          </button>
        </div>
      </header>

      <div
        class="mx-auto grid w-full max-w-[1280px] gap-7 px-6 pb-12 pt-6 lg:grid-cols-[240px_1fr]"
      >
        <!--
          En pantallas estrechas la navegación pasa a fila desplazable en vez de ocultarse: la
          maqueta la esconde con display:none, pero es la única navegación de la app y dejarla
          inalcanzable en móvil incumpliría WCAG 2.1 AA.
        -->
        <nav
          class="flex gap-1 overflow-x-auto lg:flex-col lg:overflow-visible lg:pt-2"
          aria-label="Principal"
          i18n-aria-label
        >
          <div
            class="hidden px-3 pb-1.5 pt-3.5 text-[11px] font-semibold uppercase tracking-[0.8px] text-muted-foreground lg:block"
            i18n
          >
            Administración
          </div>

          @if (isAdmin()) {
            <a
              class="whitespace-nowrap rounded-lg px-3 py-2 text-sm hover:bg-muted"
              routerLink="/coaches"
              routerLinkActive="bg-primary-soft font-semibold text-primary"
              #coachesLink="routerLinkActive"
              [attr.aria-current]="coachesLink.isActive ? 'page' : null"
              i18n
              >Entrenadores</a
            >
          }
          @if (isStaff()) {
            <a
              class="whitespace-nowrap rounded-lg px-3 py-2 text-sm hover:bg-muted"
              routerLink="/alumnos"
              routerLinkActive="bg-primary-soft font-semibold text-primary"
              #alumnosLink="routerLinkActive"
              [attr.aria-current]="alumnosLink.isActive ? 'page' : null"
              i18n
              >Alumnos</a
            >
          }
          @if (permissions.can('GROUP', 'LIST')) {
            <a
              class="whitespace-nowrap rounded-lg px-3 py-2 text-sm hover:bg-muted"
              routerLink="/club/grupos"
              routerLinkActive="bg-primary-soft font-semibold text-primary"
              #gruposLink="routerLinkActive"
              [attr.aria-current]="gruposLink.isActive ? 'page' : null"
              i18n
              >Grupos</a
            >
          }
          @if (permissions.can('TAXONOMY', 'MANAGE')) {
            <a
              class="whitespace-nowrap rounded-lg px-3 py-2 text-sm hover:bg-muted"
              routerLink="/club/taxonomia"
              routerLinkActive="bg-primary-soft font-semibold text-primary"
              #taxonomiaLink="routerLinkActive"
              [attr.aria-current]="taxonomiaLink.isActive ? 'page' : null"
              i18n
              >Taxonomía</a
            >
          }
          @if (permissions.can('CLUB', 'UPDATE')) {
            <a
              class="whitespace-nowrap rounded-lg px-3 py-2 text-sm hover:bg-muted"
              routerLink="/club/ajustes"
              routerLinkActive="bg-primary-soft font-semibold text-primary"
              #ajustesLink="routerLinkActive"
              [attr.aria-current]="ajustesLink.isActive ? 'page' : null"
              i18n
              >Ajustes del club</a
            >
          }
        </nav>

        <main class="min-w-0">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class AppShellComponent implements OnInit {
  private readonly sessionService = inject(SessionService);
  private readonly clubService = inject(ClubService);
  private readonly taxonomyService = inject(TaxonomyService);
  private readonly groupService = inject(GroupService);
  private readonly router = inject(Router);
  protected readonly permissions = inject(PermissionsService);

  readonly session = this.sessionService.session;
  readonly club = this.clubService.club;

  readonly isAdmin = computed(() => this.session()?.role === 'ADMIN');
  readonly isStaff = computed(() => {
    const role = this.session()?.role;
    return role === 'ADMIN' || role === 'ENTRENADOR';
  });

  /** Inicial del rol como avatar provisional (no hay nombre en el principal en H0). */
  readonly initials = computed(() => this.session()?.role.charAt(0).toUpperCase() ?? '·');

  readonly sessionAriaLabel = computed(() => {
    const role = this.session()?.role ?? '';
    return $localize`Sesión de ${role}:role:`;
  });

  ngOnInit(): void {
    // El shell es el único que carga estas dos: las pantallas hijas solo leen los signals.
    // Hasta que responda /me/permissions, «Ajustes del club» no aparece (fail-closed); es un
    // parpadeo asumido, preferible a bloquear el render de toda la navegación.
    this.clubService.loadOnce();
    this.permissions.loadOnce();
  }

  close(): void {
    // Las cachés se vacían aquí y no dentro de SessionService para no acoplar servicios de core
    // entre sí. Sin esto, un admin que cierra sesión y entra como alumno seguiría viendo
    // «Ajustes del club» hasta recargar la página.
    this.clubService.reset();
    this.permissions.reset();
    this.taxonomyService.reset();
    this.groupService.reset();
    this.sessionService.close().subscribe({
      next: () => void this.router.navigate(['/login']),
      error: () => void this.router.navigate(['/login']),
    });
  }
}
