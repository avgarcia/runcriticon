import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * E2E de la vista de salud del club (M16). Como los demás, intercepta la API — sin backend
 * en CI no hay sesión y el `authGuard` redirigiría a /login antes de pintar nada.
 *
 * La pantalla compone dos respuestas de dos módulos distintos (`GET /grupos` y
 * `GET /salud-del-club/actividad`) por `grupoId`: sin interceptar la segunda, `forkJoin` no emite
 * nunca y la pantalla se queda cargando para siempre.
 */

const CLUB = { id: 'club-1', nombre: 'Club Atletismo Pinares', slug: null };

const PERMISOS_ADMIN = {
  CLUB_HEALTH: ['LIST'],
  GROUP: ['LIST'],
};

const GRUPOS = [
  { id: 'g-1', nombre: 'Maratón Valencia avanzado', valores: [], totalAlumnos: 12, tieneEntrenador: true },
  { id: 'g-2', nombre: 'Iniciación CACO', valores: [], totalAlumnos: 8, tieneEntrenador: true },
  { id: 'g-3', nombre: 'Trail finde', valores: [], totalAlumnos: 6, tieneEntrenador: false },
];

const ACTIVIDAD = [
  { grupoId: 'g-1', ultimaActividadEn: new Date().toISOString() },
  // g-2 no tiene entrada: nunca ha reportado nadie de ese grupo.
  { grupoId: 'g-3', ultimaActividadEn: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString() },
];

async function mockApi(
  page: Page,
  opciones: { role?: string; permisos?: Record<string, string[]> } = {},
): Promise<void> {
  const { role = 'ADMIN', permisos = PERMISOS_ADMIN } = opciones;

  await page.route('**/api/sesion/actual', (route) =>
    route.fulfill({ json: { userId: 'u-1', clubId: CLUB.id, role } }),
  );
  await page.route('**/api/me/permissions', (route) => route.fulfill({ json: permisos }));
  await page.route('**/api/club', (route) => route.fulfill({ json: CLUB }));
  await page.route('**/api/grupos', (route) => route.fulfill({ json: { grupos: GRUPOS } }));
  await page.route('**/api/salud-del-club/actividad', (route) =>
    route.fulfill({ json: { grupos: ACTIVIDAD } }),
  );
}

test.describe('Salud del club', () => {
  test('el admin ve cada grupo con sus alumnos, su entrenador y su última actividad', async ({ page }) => {
    await mockApi(page);
    await page.goto('/club/salud');

    await expect(page.getByRole('heading', { name: 'Salud del club' })).toBeVisible();
    const filaAvanzado = page.getByRole('row', { name: /Maratón Valencia avanzado/ });
    await expect(filaAvanzado).toContainText('12');
    await expect(filaAvanzado).toContainText('Asignado');

    const filaIniciacion = page.getByRole('row', { name: /Iniciación CACO/ });
    await expect(filaIniciacion).toContainText('Sin actividad');
  });

  test('distingue visualmente los grupos sin entrenador', async ({ page }) => {
    await mockApi(page);
    await page.goto('/club/salud');

    const filaSinEntrenador = page.getByRole('row', { name: /Trail finde/ });
    await expect(filaSinEntrenador).toContainText('Sin entrenador');
  });

  test('un entrenador no ve la entrada de Salud del club y la URL directa lo devuelve al inicio', async ({
    page,
  }) => {
    await mockApi(page, { role: 'ENTRENADOR', permisos: {} });
    await page.goto('/');

    await expect(page.getByRole('link', { name: 'Salud del club' })).toHaveCount(0);

    await page.goto('/club/salud');

    await expect(page).not.toHaveURL(/\/club\/salud/);
  });

  test('no tiene violaciones de accesibilidad WCAG 2.1 AA', async ({ page }) => {
    await mockApi(page);
    await page.goto('/club/salud');
    await expect(page.getByRole('heading', { name: 'Salud del club' })).toBeVisible();
    await expect(page.getByRole('row', { name: /Trail finde/ })).toBeVisible();

    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(resultados.violations).toEqual([]);
  });
});
