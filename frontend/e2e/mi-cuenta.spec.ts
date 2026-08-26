import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * E2E de "Mi cuenta" (LAL-128): el propio estado de consentimiento de datos de salud del alumno,
 * en sus tres estados. Mismo patrón que `mi-plan.spec.ts`: mockea la API, sin backend real en CI.
 */
const SESION_ALUMNO = { userId: 'alumno-1', clubId: 'club-1', role: 'ALUMNO' };

async function mockApi(page: Page, consent: unknown): Promise<void> {
  await page.route('**/api/sesion/actual', (route) => route.fulfill({ json: SESION_ALUMNO }));
  await page.route('**/api/me/permissions', (route) => route.fulfill({ json: {} }));
  await page.route('**/api/me/consentimiento', (route) => {
    if (route.request().method() === 'GET') return route.fulfill({ json: consent });
    return route.continue();
  });
}

test.describe('Mi cuenta — consentimiento de datos de salud', () => {
  test('PENDIENTE ofrece dar el consentimiento', async ({ page }) => {
    await mockApi(page, { estado: 'PENDIENTE' });
    await page.goto('/mi-cuenta');

    await expect(page.getByText('Todavía no has dado tu consentimiento')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Dar mi consentimiento' })).toBeVisible();
  });

  test('conceder desde PENDIENTE deja VIGENTE', async ({ page }) => {
    await mockApi(page, { estado: 'PENDIENTE' });
    await page.route('**/api/me/consentimiento', async (route) => {
      if (route.request().method() === 'POST') {
        return route.fulfill({ json: { estado: 'VIGENTE', versionTexto: 'v2026-08-25', concedidoEn: '2026-08-25T10:00:00Z' } });
      }
      return route.fulfill({ json: { estado: 'PENDIENTE' } });
    });
    await page.goto('/mi-cuenta');

    await page.getByRole('button', { name: 'Dar mi consentimiento' }).click();

    await expect(page.getByText('Vigente')).toBeVisible();
  });

  test('VIGENTE ofrece revocar, con confirmacion', async ({ page }) => {
    await mockApi(page, { estado: 'VIGENTE', versionTexto: 'v2026-08-25', concedidoEn: '2026-08-20T10:00:00Z' });
    await page.goto('/mi-cuenta');

    await expect(page.getByText('Vigente')).toBeVisible();
    await page.getByRole('button', { name: 'Revocar consentimiento' }).click();

    await expect(page.getByRole('heading', { name: 'Revocar consentimiento' })).toBeVisible();
  });

  test('REVOCADO ofrece volver a conceder', async ({ page }) => {
    await mockApi(page, { estado: 'REVOCADO', concedidoEn: '2026-08-01T10:00:00Z', revocadoEn: '2026-08-20T10:00:00Z' });
    await page.goto('/mi-cuenta');

    await expect(page.getByText('Revocado')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Volver a dar mi consentimiento' })).toBeVisible();
  });

  test('un ENTRENADOR no puede entrar en la pantalla del alumno', async ({ page }) => {
    await page.route('**/api/sesion/actual', (route) =>
      route.fulfill({ json: { userId: 'e1', clubId: 'club-1', role: 'ENTRENADOR' } }),
    );
    await page.route('**/api/me/permissions', (route) => route.fulfill({ json: {} }));

    await page.goto('/mi-cuenta');

    await expect(page).not.toHaveURL(/\/mi-cuenta$/);
  });

  test('no tiene violaciones de accesibilidad WCAG 2.1 AA', async ({ page }) => {
    await mockApi(page, { estado: 'VIGENTE', versionTexto: 'v2026-08-25', concedidoEn: '2026-08-20T10:00:00Z' });
    await page.goto('/mi-cuenta');
    await expect(page.getByText('Vigente')).toBeVisible();

    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(resultados.violations).toEqual([]);
  });
});
