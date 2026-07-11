import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmSpinner } from '@spartan-ng/helm/spinner';
import { AuthPageComponent } from '../../../shared/auth-page/auth-page.component';
import { SessionService } from '../../../core/session.service';

/**
 * Pantalla de consumo del magic link (LAL-11, ADR-0003 D5; maqueta identidad-acceso). Se llega desde
 * el enlace del email (`…/entrar?token=…`). **Auto-consume al abrir**: lee el token de la query y crea
 * sesión sin intervención; los escáneres de enlaces que solo hacen GET cargan el HTML pero no
 * ejecutan este POST. Si el enlace ha caducado o ya se usó, ofrece pedir uno nuevo.
 */
@Component({
  selector: 'rc-magic-link-consume',
  standalone: true,
  imports: [RouterLink, AuthPageComponent, HlmButton, HlmSpinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (failed()) {
      <rc-auth-page>
        <div
          class="mx-auto flex size-[60px] items-center justify-center rounded-full border border-danger-border bg-danger-soft text-[26px] text-danger"
          aria-hidden="true"
        >
          ⏱
        </div>
        <header class="text-center">
          <h1 class="text-[21px] font-semibold tracking-[-0.4px]" i18n>Enlace caducado</h1>
          <p class="mt-1.5 text-[13.5px] leading-relaxed text-muted-foreground" i18n>
            Este enlace ya no es válido. Los enlaces caducan a los 15 minutos o tras un uso.
          </p>
        </header>
        <p
          class="rounded-lg border border-danger-border bg-danger-soft px-3.5 py-3 text-[12.5px] leading-relaxed text-danger"
          i18n
        >
          <strong>No te preocupes.</strong> Pide un enlace nuevo; recibirás uno fresco en segundos.
        </p>
        <a hlmBtn size="lg" routerLink="/entrar-con-enlace" class="w-full" i18n>
          Pedir un enlace nuevo
        </a>
      </rc-auth-page>
    } @else {
      <main class="flex min-h-screen flex-col items-center justify-center gap-[22px] p-6">
        <hlm-spinner class="size-10 text-primary" aria-label="Entrando" i18n-aria-label />
        <div class="text-center">
          <h1 class="text-[20px] font-semibold tracking-[-0.3px]" i18n>Entrando…</h1>
          <p class="mt-1 text-[13.5px] text-muted-foreground" i18n>
            Te estamos identificando con tu enlace.
          </p>
        </div>
      </main>
    }
  `,
})
export class MagicLinkConsumeComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);

  readonly failed = signal(false);

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.failed.set(true);
      return;
    }
    this.session.consumeMagicLink(token).subscribe({
      next: () => void this.router.navigate(['/']),
      error: () => this.failed.set(true),
    });
  }
}
