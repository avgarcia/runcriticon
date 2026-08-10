import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * E2E del listado de grupos del club. Como los demás, intercepta la API — sin backend en CI no hay
 * sesión y el `authGuard` redirigiría a /login antes de pintar nada.
 */

const CLUB = { id: 'club-1', nombre: 'Club Atletismo Pinares', slug: null };

const PERMISOS_STAFF = {
  COACH: ['INVITE', 'LIST'],
  STUDENT: ['INVITE'],
  CLUB: ['UPDATE'],
  TAXONOMY: ['LIST', 'MANAGE'],
  GROUP: ['LIST'],
};

const TAXONOMIA = {
  tags: [
    {
      id: 'tag-nivel',
      nombre: 'nivel',
      valores: [
        { id: 'val-medio', valor: 'medio', metadata: { tipo: 'EMPTY' } },
        { id: 'val-alto', valor: 'alto', metadata: { tipo: 'EMPTY' } },
      ],
    },
    {
      id: 'tag-terreno',
      nombre: 'terreno',
      valores: [
        { id: 'val-trail', valor: 'trail', metadata: { tipo: 'EMPTY' } },
        { id: 'val-pista', valor: 'pista', metadata: { tipo: 'EMPTY' } },
      ],
    },
  ],
};

const gruposIniciales = () => [
  { id: 'g-1', nombre: 'Maratón nivel medio', valores: ['val-medio'], totalAlumnos: 2 },
  // Un grupo sin nadie dentro no es decorado: su aviso es lo que hay que pasar por el analizador.
  { id: 'g-2', nombre: 'Trail avanzado', valores: ['val-alto', 'val-trail'], totalAlumnos: 0 },
];

async function mockApi(
  page: Page,
  opciones: { role?: string; permisos?: Record<string, string[]> } = {},
): Promise<void> {
  const { role = 'ADMIN', permisos = PERMISOS_STAFF } = opciones;

  await page.route('**/api/sesion/actual', (route) =>
    route.fulfill({ json: { userId: 'u-1', clubId: CLUB.id, role } }),
  );
  await page.route('**/api/me/permissions', (route) => route.fulfill({ json: permisos }));
  await page.route('**/api/club', (route) => route.fulfill({ json: CLUB }));
  await page.route('**/api/taxonomia', (route) => route.fulfill({ json: TAXONOMIA }));
  await page.route('**/api/grupos', (route) => route.fulfill({ json: { grupos: gruposIniciales() } }));
}

test.describe('Grupos del club', () => {
  test('el listado enseña el filtro en palabras y cuánta gente hay en cada grupo', async ({
    page,
  }) => {
    await mockApi(page);
    await page.goto('/club/grupos');

    await expect(page.getByRole('heading', { name: 'Maratón nivel medio' })).toBeVisible();
    await expect(page.getByText('nivel = medio')).toBeVisible();
    await expect(page.getByText('2 alumnos')).toBeVisible();
    await expect(page.getByText('Ningún alumno cumple este filtro ahora mismo.')).toBeVisible();
  });

  test('el listado con un grupo vacío cumple WCAG 2.1 AA', async ({ page }) => {
    await mockApi(page);
    await page.goto('/club/grupos');
    await expect(page.getByText('Ningún alumno cumple este filtro ahora mismo.')).toBeVisible();

    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(resultados.violations).toEqual([]);
  });

  test('el alumno no ve la entrada de Grupos y la URL directa lo devuelve al inicio', async ({
    page,
  }) => {
    await mockApi(page, { role: 'ALUMNO', permisos: {} });
    await page.goto('/');

    await expect(page.getByRole('link', { name: 'Grupos' })).toHaveCount(0);

    await page.goto('/club/grupos');

    await expect(page).not.toHaveURL(/\/club\/grupos/);
  });
});
