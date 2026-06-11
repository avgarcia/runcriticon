# Testing frontend — Runcriticon

Fuente: ADR-0012 D6/D7/D8/D21, ADR-0010 D8, `frontend/CLAUDE.md`

## Pirámide de tests

| Tipo | Herramienta | Alcance | Velocidad |
|------|------------|---------|-----------|
| **Unit** | Jest + jest-preset-angular | Servicios, pipes, utilidades, validadores | Rápido |
| **Component** | Jest + Angular Testing Library | Componentes con interacción | Rápido |
| **E2E** | Playwright | Flujos completos críticos | Lento |
| **A11y** | axe-core (`@axe-core/playwright`) | Integrado en E2E de pantallas críticas | En E2E |

## Jest — tests unitarios y de componente

Configuración en `frontend/jest.config.js` con `jest-preset-angular`.

```typescript
import { TestBed } from '@angular/core/testing';
import { render, screen, fireEvent } from '@testing-library/angular';
import { {Feature}Component } from './{feature}.component';

// Con TestBed (para unit puro):
describe('{Feature}Component', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [{Feature}Component],
    }).compileComponents();
  });

  it('debería crear el componente', () => {
    const fixture = TestBed.createComponent({Feature}Component);
    expect(fixture.componentInstance).toBeTruthy();
  });
});

// Con Angular Testing Library (para interacción usuario):
it('debería mostrar el listado de alumnos', async () => {
  await render({Feature}Component, {
    componentInputs: { alumnos: mockAlumnos },
  });
  expect(screen.getByText('García López')).toBeInTheDocument();
});
```

### Comandos

```bash
npm test                                          # todos los tests
npm test -- --testPathPattern={feature}           # solo la feature
npm test -- --coverage                            # con cobertura
npm test -- --watch                               # modo watch
```

### Cobertura mínima

NFR: **> 70 %** en lógica de presentación (componentes, servicios).
Sin threshold numérico forzado en `jest.config.js` en H0 — se añade cuando haya baseline real.

## Playwright — tests E2E

Configuración en `frontend/playwright.config.ts`. Base URL: `http://localhost:4200`. Arranca el dev server automáticamente en local.

```typescript
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

// Flujo completo:
test('entrenador puede crear un grupo nuevo', async ({ page }) => {
  await page.goto('/grupos');
  await page.getByRole('button', { name: /nuevo grupo/i }).click();
  await page.getByLabel(/nombre del grupo/i).fill('Grupo A');
  await page.getByRole('button', { name: /guardar/i }).click();
  await expect(page.getByText('Grupo A')).toBeVisible();
});

// Con axe-core (pantallas críticas WCAG AA):
test('no debe tener violaciones WCAG 2.1 AA', async ({ page }) => {
  await page.goto('/grupos');
  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations).toEqual([]);
});

// Navegación con teclado (ADR-0012 D8):
test('flujo accesible solo con teclado', async ({ page }) => {
  await page.goto('/grupos');
  await page.keyboard.press('Tab');
  // ... navegar con Tab hasta el botón objetivo
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog')).toBeVisible();
});
```

### Solo recorridos críticos (ADR-0012 D21)

No probar todo de extremo a extremo. Los flujos con test E2E obligatorio:
- Login y activación.
- Dashboard del alumno (vista "hoy").
- Editor de plan semanal (entrenador).
- Reportar sesión (alumno).
- Vista de salud del club.
- Gestión de alumnos.

### Comando

```bash
npm run e2e                           # todos los E2E
npm run e2e -- --grep "{feature}"     # solo los de la feature
```

## axe-core — accesibilidad automática (ADR-0012 D7)

Pantallas críticas (D6) requieren test axe-core en el E2E de Playwright. El CI bloquea PRs con violaciones AA.

```typescript
// Nivel AA completo (recomendado para pantallas críticas):
const results = await new AxeBuilder({ page })
  .withTags(['wcag2a', 'wcag2aa'])
  .analyze();
expect(results.violations).toEqual([]);

// Excluir componentes de terceros que no podemos controlar:
const results = await new AxeBuilder({ page })
  .exclude('.third-party-widget')
  .analyze();
```

## Contrato de API

El test de contrato lo cubre el pipeline del **backend**. El frontend usa el cliente generado — no hay tests de contrato adicionales en el frontend.

## CI (ADR-0010)

El pipeline ejecuta en orden:
1. `npm run lint` — ESLint + Prettier
2. `npm test` — Jest unit/component
3. `npm run build` — build + budget check
4. `npm run e2e` — Playwright + axe-core

Un fallo en cualquier paso bloquea el merge.
