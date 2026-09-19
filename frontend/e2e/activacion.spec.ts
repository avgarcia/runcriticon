import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * E2E de la activación de cuenta (pantalla crítica de onboarding). Cubre la casilla de
 * consentimiento de datos de salud añadida en LAL-128 (ADR-0014 D16/D18): no premarcada, se envía
 * en el cuerpo de `POST /activacion`, y el error `CONSENTIMIENTO_REQUERIDO` se muestra si falta.
 * Cubre también la tarjeta de contexto de la invitación (LAL-64): la pantalla resuelve `GET
 * /activacion?token=…` antes de mostrar el formulario, así que todo mock de esta ruta debe cubrir
 * ambos verbos -- si solo se mockea el `POST`, el `GET` cae fuera de la ruta y el formulario no llega
 * a renderizarse.
 */
const invitationDetailsBody = {
  nombre: 'Andrea López',
  club: 'Club Atletismo Pinares',
  rol: 'ALUMNO',
  invitadoPor: 'Ana Pinares',
};

/**
 * `**\/api/activacion**` (doble comodín al final), no `**\/api/activacion`: el patrón sin el segundo
 * `**` solo intercepta el `POST` (sin query string) -- el `GET ?token=…` que dispara `ngOnInit` (LAL-64)
 * queda fuera del patrón, la petición real sale hacia la red y falla, y la pantalla cae siempre a
 * "Invitación no válida" aunque el resto del mock sea correcto. Un único handler que ramifica por
 * `method()`, no dos `page.route()` apilados con `route.fallback()`.
 */
async function mockApi(
  page: Page,
  options: { activationStatus?: number; invitationStatus?: number } = {},
): Promise<void> {
  const { activationStatus = 200, invitationStatus = 200 } = options;
  await page.route('**/api/activacion**', (route) => {
    if (route.request().method() === 'GET') {
      if (invitationStatus !== 200) {
        return route.fulfill({
          status: invitationStatus,
          json: { code: 'INVALID_INPUT', field: 'token', message: 'x' },
        });
      }
      return route.fulfill({ json: invitationDetailsBody });
    }
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
    let sentBody: { consentimiento?: boolean } | undefined;
    await page.route('**/api/activacion**', (route) => {
      if (route.request().method() === 'GET') {
        return route.fulfill({ json: invitationDetailsBody });
      }
      sentBody = route.request().postDataJSON();
      return route.fulfill({ json: { userId: 'u1', clubId: 'c1', role: 'ALUMNO' } });
    });
    await page.route('**/api/sesion/actual', (route) =>
      route.fulfill({ json: { userId: 'u1', clubId: 'c1', role: 'ALUMNO' } }),
    );
    await page.route('**/api/me/permissions', (route) => route.fulfill({ json: {} }));
    await page.route('**/api/me/plan*', (route) => route.fulfill({ json: { semana: '2026-08-17', sesiones: [] } }));
    await page.goto('/activar?token=tok-123');

    await page.getByLabel('Contraseña', { exact: true }).fill('clave-clave-clave');
    await page.getByLabel('Repite la contraseña').fill('clave-clave-clave');
    await page.getByRole('button', { name: 'Activar mi cuenta' }).click();

    await expect.poll(() => sentBody?.consentimiento).toBe(false);
  });

  test('marcar la casilla envia consentimiento=true', async ({ page }) => {
    let sentBody: { consentimiento?: boolean } | undefined;
    await page.route('**/api/activacion**', (route) => {
      if (route.request().method() === 'GET') {
        return route.fulfill({ json: invitationDetailsBody });
      }
      sentBody = route.request().postDataJSON();
      return route.fulfill({ json: { userId: 'u1', clubId: 'c1', role: 'ALUMNO' } });
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
    await mockApi(page, { activationStatus: 400 });
    await page.goto('/activar?token=tok-123');

    await page.getByLabel('Contraseña', { exact: true }).fill('clave-clave-clave');
    await page.getByLabel('Repite la contraseña').fill('clave-clave-clave');
    await page.getByRole('button', { name: 'Activar mi cuenta' }).click();

    await expect(page.getByText('Marca la casilla')).toBeVisible();
  });

  test('pinta la tarjeta de contexto de la invitación (LAL-64)', async ({ page }) => {
    await mockApi(page);
    await page.goto('/activar?token=tok-123');

    await expect(page.getByRole('heading', { name: 'Hola, Andrea' })).toBeVisible();
    await expect(page.getByText('Club Atletismo Pinares')).toBeVisible();
    await expect(page.getByText('te invita Ana Pinares')).toBeVisible();
  });

  test('token rechazado por el backend muestra "Invitación no válida" (LAL-64)', async ({ page }) => {
    await mockApi(page, { invitationStatus: 400 });
    await page.goto('/activar?token=tok-invalido');

    await expect(page.getByRole('heading', { name: 'Invitación no válida' })).toBeVisible();
  });

  test('no tiene violaciones de accesibilidad WCAG 2.1 AA', async ({ page }) => {
    await mockApi(page);
    await page.goto('/activar?token=tok-123');
    await expect(page.getByRole('heading', { name: 'Hola, Andrea' })).toBeVisible();

    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(resultados.violations).toEqual([]);
  });
});
