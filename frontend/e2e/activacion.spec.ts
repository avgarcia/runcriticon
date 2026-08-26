import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * E2E de la activación de cuenta (pantalla crítica de onboarding). Cubre la casilla de
 * consentimiento de datos de salud añadida en LAL-128 (ADR-0014 D16/D18): no premarcada, se envía
 * en el cuerpo de `POST /activacion`, y el error `CONSENTIMIENTO_REQUERIDO` se muestra si falta.
 */
async function mockApi(page: Page, activationStatus = 200): Promise<void> {
  await page.route('**/api/activacion', (route) => {
    const body = route.request().postDataJSON();
    if (activationStatus !== 200) {
      return route.fulfill({
        status: activationStatus,
        json: { code: 'CONSENTIMIENTO_REQUERIDO', field: 'consentimiento', message: 'x' },
      });
    }
    return route.fulfill({
      json: { userId: 'u1', clubId: 'c1', role: 'ALUMNO', __consentimientoEnviado: body.consentimiento },
    });
  });
  await page.route('**/api/sesion/actual', (route) =>
    route.fulfill({ json: { userId: 'u1', clubId: 'c1', role: 'ALUMNO' } }),
  );
  await page.route('**/api/me/permissions', (route) => route.fulfill({ json: {} }));
  await page.route('**/api/me/plan*', (route) => route.fulfill({ json: { semana: '2026-08-17', sesiones: [] } }));
}

test.describe('Activación de cuenta', () => {
  test('la casilla de consentimiento no viene premarcada', async ({ page }) => {
    await mockApi(page);
    await page.goto('/activar?token=tok-123');

    const checkbox = page.getByRole('checkbox');
    await expect(checkbox).toBeVisible();
    await expect(checkbox).not.toBeChecked();
  });

  test('activar sin marcar la casilla envia consentimiento=false', async ({ page }) => {
    await mockApi(page);
    let sentBody: { consentimiento?: boolean } | undefined;
    await page.route('**/api/activacion', async (route) => {
      sentBody = route.request().postDataJSON();
      await route.fulfill({ json: { userId: 'u1', clubId: 'c1', role: 'ALUMNO' } });
    });
    await page.goto('/activar?token=tok-123');

    await page.getByLabel('Contraseña', { exact: true }).fill('clave-clave-clave');
    await page.getByLabel('Repite la contraseña').fill('clave-clave-clave');
    await page.getByRole('button', { name: 'Activar mi cuenta' }).click();

    await expect.poll(() => sentBody?.consentimiento).toBe(false);
  });

  test('marcar la casilla envia consentimiento=true', async ({ page }) => {
    let sentBody: { consentimiento?: boolean } | undefined;
    await page.route('**/api/activacion', async (route) => {
      sentBody = route.request().postDataJSON();
      await route.fulfill({ json: { userId: 'u1', clubId: 'c1', role: 'ALUMNO' } });
    });
    await page.route('**/api/sesion/actual', (route) =>
      route.fulfill({ json: { userId: 'u1', clubId: 'c1', role: 'ALUMNO' } }),
    );
    await page.route('**/api/me/permissions', (route) => route.fulfill({ json: {} }));
    await page.route('**/api/me/plan*', (route) => route.fulfill({ json: { semana: '2026-08-17', sesiones: [] } }));
    await page.goto('/activar?token=tok-123');

    await page.getByLabel('Contraseña', { exact: true }).fill('clave-clave-clave');
    await page.getByLabel('Repite la contraseña').fill('clave-clave-clave');
    await page.getByRole('checkbox').check();
    await page.getByRole('button', { name: 'Activar mi cuenta' }).click();

    await expect.poll(() => sentBody?.consentimiento).toBe(true);
  });

  test('CONSENTIMIENTO_REQUERIDO pide marcar la casilla', async ({ page }) => {
    await mockApi(page, 400);
    await page.goto('/activar?token=tok-123');

    await page.getByLabel('Contraseña', { exact: true }).fill('clave-clave-clave');
    await page.getByLabel('Repite la contraseña').fill('clave-clave-clave');
    await page.getByRole('button', { name: 'Activar mi cuenta' }).click();

    await expect(page.getByText('Marca la casilla')).toBeVisible();
  });

  test('no tiene violaciones de accesibilidad WCAG 2.1 AA', async ({ page }) => {
    await mockApi(page);
    await page.goto('/activar?token=tok-123');
    await expect(page.getByRole('heading', { name: 'Activa tu cuenta' })).toBeVisible();

    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(resultados.violations).toEqual([]);
  });
});
