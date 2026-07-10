import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Shell común de las pantallas de identidad y acceso (maqueta docs/diseno/identidad-acceso.html):
 * columna centrada de 384 px con logo, nombre de la app y cabecera de título/subtítulo opcional.
 * El contenido de cada pantalla se proyecta debajo.
 */
@Component({
  selector: 'rc-auth-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="flex min-h-screen justify-center p-4 sm:items-center">
      <div class="flex w-full max-w-sm flex-col gap-[18px] py-8">
        <div class="flex flex-col items-center gap-1.5">
          <img src="logo.svg" alt="" class="size-11" />
          <span class="text-[13px] font-semibold">Runcriticon</span>
        </div>
        @if (title()) {
          <header class="text-center">
            <h1 class="text-[22px] font-semibold tracking-[-0.4px]">{{ title() }}</h1>
            @if (subtitle()) {
              <p class="mt-1 text-[13.5px] leading-normal text-muted-foreground">
                {{ subtitle() }}
              </p>
            }
          </header>
        }
        <ng-content />
      </div>
    </main>
  `,
})
export class AuthPageComponent {
  readonly title = input('');
  readonly subtitle = input('');
}
