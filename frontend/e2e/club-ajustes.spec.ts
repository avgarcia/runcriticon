import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * E2E de Ajustes del club (LAL-98). A diferencia del resto de e2e, este intercepta la API: sin
 * backend en CI no hay sesión, y el `authGuard` redirigiría a /login antes de pintar nada. Se
 * mockean las tres llamadas que dispara el shell más la pantalla.
 */

const CLUB = { id: 'club-1', nombre: 'Club Atletismo Pinares', slug: null };

const PERMISOS_ADMIN = {
  COACH: ['INVITE', 'LIST'],
  STUDENT: ['INVITE'],
  USER: ['REVOKE_SESSIONS', 'DEACTIVATE'],
  CLUB: ['UPDATE'],
};

async function mockApi(
  page: Page,
  opciones: {
    role?: string;
    permisos?: Record<string, string[]>;
    club?: typeof CLUB;
  } = {},
): Promise<void> {
  const { role = 'ADMIN', permisos = PERMISOS_ADMIN, club = CLUB } = opciones;

  await page.route('**/api/sesion/actual', (route) =>
    route.fulfill({ json: { userId: 'u-1', clubId: club.id, role } }),
  );
  await page.route('**/api/me/permissions', (route) => route.fulfill({ json: permisos }));
  await page.route('**/api/club', async (route) => {
    if (route.request().method() === 'PATCH') {
      const body = route.request().postDataJSON() as { nombre: string };
      return route.fulfill({ json: { ...club, nombre: body.nombre } });
    }
    return route.fulfill({ json: club });
  });
}

test.describe('Ajustes del club', () => {
  test('el admin ve el nombre del club y el identificador sin asignar', async ({ page }) => {
    await mockApi(page);
    await page.goto('/club/ajustes');

    await expect(page.getByRole('heading', { name: 'Ajustes del club' })).toBeVisible();
    await expect(page.getByLabel('Nombre')).toHaveValue('Club Atletismo Pinares');
    await expect(page.getByLabel('Identificador')).toHaveValue('Sin asignar');
    await expect(page.getByLabel('Identificador')).toHaveAttribute('readonly', '');
  });

  test('no tiene violaciones de accesibilidad WCAG 2.1 AA', async ({ page }) => {
    await mockApi(page);
    await page.goto('/club/ajustes');
    await expect(page.getByRole('heading', { name: 'Ajustes del club' })).toBeVisible();

    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(resultados.violations).toEqual([]);
  });

  test('guardar el nombre lo refleja en la cabecera sin recargar', async ({ page }) => {
    await mockApi(page);
    await page.goto('/club/ajustes');

    // El <header> de la tarjeta vive dentro de <main> y no es landmark; 'banner' solo casa con
    // la cabecera del shell, que es justo la que debe reaccionar al guardado.
    const cabecera = page.getByRole('banner');
    await expect(cabecera).toContainText('Club Atletismo Pinares');

    await page.getByLabel('Nombre').fill('Club Atletismo Central');
    await page.getByRole('button', { name: 'Guardar' }).click();

    await expect(cabecera).toContainText('Club Atletismo Central');
  });

  test('el entrenador no ve la entrada de Ajustes del club en el menú', async ({ page }) => {
    await mockApi(page, { role: 'ENTRENADOR', permisos: { STUDENT: ['INVITE'] } });
    await page.goto('/');

    await expect(page.getByRole('navigation', { name: 'Principal' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Ajustes del club' })).toHaveCount(0);
  });

  test('el formulario es navegable por teclado', async ({ page }) => {
    await mockApi(page);
    await page.goto('/club/ajustes');
    await expect(page.getByLabel('Nombre')).toBeVisible();

    await page.getByLabel('Nombre').focus();
    await expect(page.getByLabel('Nombre')).toBeFocused();

    // Identificador es readonly pero sigue siendo tabulable; el siguiente tabulador da al botón.
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: 'Guardar' })).toBeFocused();
  });
});
