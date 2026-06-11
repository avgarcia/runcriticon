# Componentes Angular — patrones del proyecto

Fuente: ADR-0001 D5/D6, ADR-0012 D1-D5/D16/D19/D20

## Angular Material 3 — imports frecuentes

Importar solo lo que usa el componente:

```typescript
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatExpansionModule } from '@angular/material/expansion';
import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
```

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
  <mat-progress-spinner />
} @else if (tieneDatos()) {
  <mat-list>
    @for (alumno of alumnos(); track alumno.id) {
      <mat-list-item>{{ alumno.nombre }}</mat-list-item>
    } @empty {
      <p i18n>Sin alumnos</p>
    }
  </mat-list>
} @else {
  <p i18n>No hay datos disponibles.</p>
}

@switch (estado()) {
  @case ('cargando') { <mat-progress-spinner /> }
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
  <!-- Skeleton: misma forma que el contenido real -->
  <mat-card>
    <div class="skeleton skeleton-title"></div>
    <div class="skeleton skeleton-line"></div>
    <div class="skeleton skeleton-line short"></div>
  </mat-card>
} @else {
  <!-- Contenido real -->
}
```

## Optimistic UI (ADR-0012 D20)

Para acciones reversibles (toggle, marcar como leído):

1. Aplicar el cambio en la señal local inmediatamente.
2. Enviar la petición HTTP.
3. Si falla, revertir la señal y mostrar toast de error.

## Material 3 theming — tokens disponibles

```scss
// En estilos del componente:
.mi-elemento {
  background-color: var(--mat-sys-surface-container);
  color: var(--mat-sys-on-surface);
  border-radius: var(--mat-sys-shape-corner-medium);
}
```

Tokens principales: `--mat-sys-primary`, `--mat-sys-on-primary`, `--mat-sys-surface`, `--mat-sys-surface-container`, `--mat-sys-error`, `--mat-sys-on-error`.

## Focus management — accesibilidad (ADR-0012 D8)

```typescript
import { FocusMonitor } from '@angular/cdk/a11y';

// Al abrir diálogo: focus al primer control
// Al cerrar diálogo: devolver focus al elemento disparador
// Skip link en el shell: salta al contenido principal
```

Todo componente interactivo debe ser completamente operable con Tab, Enter, Space, flechas y Esc.
