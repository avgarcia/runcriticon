# Componentes Angular — patrones del proyecto

Fuente: ADR-0001 D5/D6, ADR-0012 D1-D5/D16/D19/D20

## spartan.ng (helm) — imports frecuentes

Los componentes helm están **copiados** en `frontend/src/app/ui/` y se importan por el path-alias `@spartan-ng/helm/*` (definido en `tsconfig.json` por el CLI de spartan). **El inventario real es el directorio `src/app/ui/`** — si falta un componente, se añade con `ng g @spartan-ng/cli:ui {componente}`, no se escribe a mano.

Importar solo lo que usa el componente (los nombres exactos de los exports están en cada `src/app/ui/{componente}/index.ts`):

```typescript
// Ejemplos — verificar el export real en src/app/ui/
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
```

Los toasts NO se disparan directamente con sonner: se usa el `ToastService` propio de `core/` (mockeable en specs).

## Angular CDK

```typescript
import { A11yModule } from '@angular/cdk/a11y';            // focus management
import { DragDropModule } from '@angular/cdk/drag-drop';    // editor plan semanal
import { OverlayModule } from '@angular/cdk/overlay';       // diálogos custom
```

## Signals — API nativa Angular 16+

```typescript
import { signal, computed, effect, input, output } from '@angular/core';

// Estado local del componente
readonly #cargando = signal(false);
readonly cargando = this.#cargando.asReadonly();

// Estado derivado
readonly tieneDatos = computed(() => this.datos().length > 0);

// Inputs como signals (Angular 17+)
readonly nombreAlumno = input.required<string>();
readonly maxGrupos = input(5);

// Outputs
readonly seleccionado = output<Alumno>();

// Effect (uso con moderación — preferir computed)
effect(() => {
  if (this.cargando()) { /* ... */ }
});
```

## Template control flow (Angular 17+)

```html
@if (cargando()) {
  <hlm-spinner />
} @else if (tieneDatos()) {
  <ul class="flex flex-col gap-2">
    @for (alumno of alumnos(); track alumno.id) {
      <li>{{ alumno.nombre }}</li>
    } @empty {
      <p i18n>Sin alumnos</p>
    }
  </ul>
} @else {
  <p i18n>No hay datos disponibles.</p>
}

@switch (estado()) {
  @case ('cargando') { <hlm-spinner /> }
  @case ('error') { <p i18n>Error al cargar.</p> }
  @default { <ng-content /> }
}
```

## Formularios tipados con ReactiveFormsModule

```typescript
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';

// En el componente:
private readonly fb = inject(FormBuilder);

readonly formulario = this.fb.group({
  nombre: ['', [Validators.required, Validators.maxLength(100)]],
  email:  ['', [Validators.required, Validators.email]],
});

// Acceso tipado:
this.formulario.controls.nombre.value // string | null
```

## Ciclo de vida del componente

- Preferir `input()` signal sobre `@Input()` decorator (Angular 17+).
- Usar `inject()` en lugar de constructor injection.
- `OnPush` siempre — el template se actualiza solo cuando cambian las señales o referencias.

## Skeleton screens y loading states (ADR-0012 D20)

```html
@if (cargando()) {
  <!-- Skeleton: misma forma que el contenido real (helm skeleton) -->
  <div class="flex flex-col gap-2 rounded-lg border border-border bg-card p-4">
    <hlm-skeleton class="h-5 w-1/3" />
    <hlm-skeleton class="h-4 w-full" />
    <hlm-skeleton class="h-4 w-2/3" />
  </div>
} @else {
  <!-- Contenido real -->
}
```

## Optimistic UI (ADR-0012 D20)

Para acciones reversibles (toggle, marcar como leído):

1. Aplicar el cambio en la señal local inmediatamente.
2. Enviar la petición HTTP.
3. Si falla, revertir la señal y mostrar toast de error.

## Theming — tokens del proyecto (ADR-0012 D3)

Variables CSS estilo shadcn declaradas en `frontend/src/styles.css`; se consumen como utilidades Tailwind (`bg-primary`, `text-muted-foreground`, `border-border`, `rounded-lg`) o como `var(--...)` en el SCSS residual:

Tokens principales: `--primary` (#1a3e72), `--primary-hover`, `--primary-soft`, `--background`, `--foreground`, `--muted`, `--muted-foreground`, `--border`, `--destructive`, `--ring`, `--radius` + colores de alerta éxito/error en `@theme`.

NUNCA hardcodear un hex en un componente: si el color no existe como token, es una conversación de theming en `styles.css`.

## Focus management — accesibilidad (ADR-0012 D8)

```typescript
import { FocusMonitor } from '@angular/cdk/a11y';

// Al abrir diálogo: focus al primer control
// Al cerrar diálogo: devolver focus al elemento disparador
// Skip link en el shell: salta al contenido principal
```

Todo componente interactivo debe ser completamente operable con Tab, Enter, Space, flechas y Esc.
