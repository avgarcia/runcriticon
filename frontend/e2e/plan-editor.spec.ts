import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * E2E del editor de plan semanal (pantalla crítica de ADR-0012 D6): axe WCAG 2.1 AA sobre la rejilla y sobre
 * el diálogo de sesión, y recorrido solo con teclado (D8). Mockea la API, como el resto de specs.
 */

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

const PLAN = {
  id: 'plan-1',
  grupoId: 'grupo-1',
  semana: '2026-08-17',
  estado: 'BORRADOR',
  sesiones: [
    {
      id: 'ses-1',
      dia: '2026-08-17',
      tipo: 'TEMPO',
      volumen: { tipo: 'DISTANCIA', metros: 8000 },
      ritmo: { tipo: 'ABSOLUTO', segundosPorKm: 240 },
      notas: 'Ritmo controlado.',
    },
  ],
  personalizaciones: [],
};

async function mockApi(page: Page): Promise<void> {
  await page.route('**/api/**', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/sesion/actual', (route) =>
    route.fulfill({ json: { userId: 'u-1', clubId: 'club-1', role: 'ENTRENADOR' } }),
  );
  await page.route('**/api/me/permissions', (route) =>
    route.fulfill({ json: { PLAN: ['CREATE', 'LIST', 'PUBLISH'], GROUP: ['LIST'] } }),
  );
  await page.route('**/api/planes/plan-1', (route) => route.fulfill({ json: PLAN }));
}

test.describe('Editor de plan semanal', () => {
  test.beforeEach(async ({ page }) => {
    await mockApi(page);
    await page.goto('/planificacion/planes/plan-1');
    await expect(page.getByRole('heading', { name: 'Plan semanal', level: 1 })).toBeVisible();
  });

  test('la rejilla de la semana no tiene violaciones WCAG 2.1 AA', async ({ page }) => {
    await expect(page.getByText('8000 m')).toBeVisible();
    const resultados = await new AxeBuilder({ page }).withTags(WCAG).analyze();
    expect(resultados.violations).toEqual([]);
  });

  test('con teclado se abre el diálogo de una sesión, se escanea y Escape lo cierra', async ({
    page,
  }) => {
    const sesion = page.getByRole('button', { name: /Tempo/ });
    await sesion.focus();
    await expect(sesion).toBeFocused();
    await page.keyboard.press('Enter');

    const dialogo = page.getByRole('dialog');
    await expect(dialogo).toBeVisible();
    await expect(dialogo.getByRole('heading', { name: /Editar sesión/ })).toBeVisible();

    const resultados = await new AxeBuilder({ page }).withTags(WCAG).analyze();
    expect(resultados.violations).toEqual([]);

    await page.keyboard.press('Escape');
    await expect(dialogo).toBeHidden();
    await expect(sesion).toBeFocused();
  });

  test('con teclado se abre el diálogo de una sesión nueva en un día libre', async ({ page }) => {
    const anadir = page.getByRole('button', { name: '+ Añadir sesión' }).first();
    await anadir.focus();
    await page.keyboard.press('Enter');
    await expect(
      page.getByRole('dialog').getByRole('heading', { name: /Nueva sesión/ }),
    ).toBeVisible();
  });
});
