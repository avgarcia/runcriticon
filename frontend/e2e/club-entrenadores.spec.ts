import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * E2E de la carga de entrenadores (LAL-89): quién lleva qué grupos y cuántos alumnos suma. Como los
 * demás, intercepta la API — sin backend en CI no hay sesión y el `authGuard` redirigiría a /login
 * antes de pintar nada.
 *
 * `grupos` sale vacía para todos los entrenadores en el doble a propósito: hoy no existe la
 * asignación entrenador↔grupo (LAL-93), así que el fixture no debe fingir que sí.
 */

const CLUB = { id: 'club-1', nombre: 'Club Atletismo Pinares', slug: null };

const PERMISOS_ADMIN = {
  COACH: ['INVITE', 'LIST'],
  STUDENT: ['INVITE', 'LIST'],
  CLUB: ['UPDATE'],
  TAXONOMY: ['LIST', 'MANAGE'],
  GROUP: ['LIST', 'CREATE', 'UPDATE'],
};

const ENTRENADORES = [
  { id: 'e-1', nombre: 'Carlos Ruiz', email: 'carlos@club.test', estado: 'ACTIVO', grupos: [], totalAlumnos: 0 },
  { id: 'e-2', nombre: 'Marta López', email: 'marta@club.test', estado: 'INVITADO', grupos: [], totalAlumnos: 0 },
];

async function mockApi(
  page: Page,
  opciones: { role?: string; permisos?: Record<string, string[]>; entrenadores?: typeof ENTRENADORES } = {},
): Promise<void> {
  const { role = 'ADMIN', permisos = PERMISOS_ADMIN, entrenadores = ENTRENADORES } = opciones;

  await page.route('**/api/sesion/actual', (route) =>
    route.fulfill({ json: { userId: 'u-1', clubId: CLUB.id, role } }),
  );
  await page.route('**/api/me/permissions', (route) => route.fulfill({ json: permisos }));
  await page.route('**/api/club', (route) => route.fulfill({ json: CLUB }));
  await page.route('**/api/entrenadores/resumen', (route) => route.fulfill({ json: { entrenadores } }));
}

test.describe('Carga de entrenadores', () => {
  test('el listado enseña nombre, email y estado de cada entrenador', async ({ page }) => {
    await mockApi(page);
    await page.goto('/club/entrenadores');

    await expect(page.getByRole('heading', { name: 'Carlos Ruiz' })).toBeVisible();
    await expect(page.getByText('carlos@club.test')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Marta López' })).toBeVisible();
  });

  test('sin asignación entrenador-grupo todos muestran el distintivo de sin grupos', async ({ page }) => {
    await mockApi(page);
    await page.goto('/club/entrenadores');

    await expect(page.getByText('Sin grupos asignados')).toHaveCount(2);
  });

  test('un club sin entrenadores lo dice en vez de dejar la lista en blanco', async ({ page }) => {
    await mockApi(page, { entrenadores: [] });
    await page.goto('/club/entrenadores');

    await expect(page.getByText('Este club todavía no tiene entrenadores.')).toBeVisible();
  });

  test('el listado cumple WCAG 2.1 AA', async ({ page }) => {
    await mockApi(page);
    await page.goto('/club/entrenadores');
    await expect(page.getByRole('heading', { name: 'Carlos Ruiz' })).toBeVisible();

    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(resultados.violations).toEqual([]);
  });

  test('el entrenador no ve la entrada de Carga de entrenadores y la URL directa lo devuelve al inicio', async ({
    page,
  }) => {
    await mockApi(page, { role: 'ENTRENADOR', permisos: { STUDENT: ['LIST'], GROUP: ['LIST', 'UPDATE'] } });
    await page.goto('/');

    await expect(page.getByRole('link', { name: 'Carga de entrenadores' })).toHaveCount(0);

    await page.goto('/club/entrenadores');

    await expect(page).not.toHaveURL(/\/club\/entrenadores/);
  });
});
