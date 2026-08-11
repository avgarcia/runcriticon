import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * E2E del listado de alumnos: filtro por tags, chips activos y alta. Como los demás, intercepta la
 * API — sin backend en CI no hay sesión y el `authGuard` redirigiría a /login antes de pintar nada.
 *
 * El doble de `GET /api/alumnos` **filtra de verdad** por `tagValueId` (AND, ausente = todos): si
 * devolviera una lista fija, el recorrido no probaría que la pantalla manda el filtro que compone el
 * usuario, ni que la semántica "sin filtro = todos" (la que diverge de grupos) es la que de verdad
 * pinta la pantalla.
 */

const CLUB = { id: 'club-1', nombre: 'Club Atletismo Pinares', slug: null };

const PERMISOS_STAFF = {
  STUDENT: ['INVITE', 'LIST'],
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
        // Nadie lo tiene: es lo que permite componer un filtro sin resultados.
        { id: 'val-pista', valor: 'pista', metadata: { tipo: 'EMPTY' } },
      ],
    },
  ],
};

const alumnosIniciales = () => [
  { id: 'a-1', nombre: 'Ana Ruiz', email: 'ana@club.test', estado: 'ACTIVO', valores: ['val-medio', 'val-trail'] },
  { id: 'a-2', nombre: 'Pedro Cordero', email: 'pedro@club.test', estado: 'INVITADO', valores: [] },
  { id: 'a-3', nombre: 'Zoe Martín', email: 'zoe@club.test', estado: 'ACTIVO', valores: ['val-alto'] },
];

async function mockApi(
  page: Page,
  opciones: { role?: string; permisos?: Record<string, string[]> } = {},
): Promise<void> {
  const { role = 'ADMIN', permisos = PERMISOS_STAFF } = opciones;
  const alumnos = alumnosIniciales();

  await page.route('**/api/sesion/actual', (route) =>
    route.fulfill({ json: { userId: 'u-1', clubId: CLUB.id, role } }),
  );
  await page.route('**/api/me/permissions', (route) => route.fulfill({ json: permisos }));
  await page.route('**/api/club', (route) => route.fulfill({ json: CLUB }));
  await page.route('**/api/taxonomia', (route) => route.fulfill({ json: TAXONOMIA }));

  await page.route('**/api/alumnos**', (route) => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON() as { nombre: string; email: string };
      alumnos.push({ id: `a-${alumnos.length + 1}`, nombre: body.nombre, email: body.email, estado: 'INVITADO', valores: [] });
      route.fulfill({
        status: 201,
        json: { id: `a-${alumnos.length}`, nombre: body.nombre, email: body.email, estado: 'INVITADO' },
      });
      return;
    }
    const url = new URL(route.request().url());
    const filtro = url.searchParams.getAll('tagValueId');
    const filtrados = filtro.length
      ? alumnos.filter((alumno) => filtro.every((valor) => alumno.valores.includes(valor)))
      : alumnos;
    route.fulfill({ json: { alumnos: filtrados } });
  });
}

test.describe('Alumnos del club', () => {
  test('el listado inicial trae a todos, sin ningún filtro', async ({ page }) => {
    await mockApi(page);
    await page.goto('/alumnos');

    await expect(page.getByText('Ana Ruiz')).toBeVisible();
    await expect(page.getByText('Pedro Cordero')).toBeVisible();
    await expect(page.getByText('Zoe Martín')).toBeVisible();
    await expect(page.getByText('3 alumnos')).toBeVisible();
    await expect(page.getByText('Invitado')).toBeVisible();
  });

  test('filtrar por un valor reduce la tabla y el chip lo restaura al quitarlo', async ({ page }) => {
    await mockApi(page);
    await page.goto('/alumnos');
    await expect(page.getByText('3 alumnos')).toBeVisible();

    await elegirFiltro(page, 'nivel', 'medio');

    await expect(page.getByText('1 alumno', { exact: true })).toBeVisible();
    await expect(page.getByText('Ana Ruiz')).toBeVisible();
    await expect(page.getByText('Zoe Martín')).not.toBeVisible();
    // El texto "nivel: medio" también aparece como tag de Ana en la tabla: se acota al grupo del chip.
    await expect(page.getByRole('group', { name: 'Filtros activos' }).getByText('nivel: medio')).toBeVisible();

    await page.getByRole('button', { name: 'Quitar filtro nivel: medio' }).click();

    await expect(page.getByText('3 alumnos')).toBeVisible();
    await expect(page.getByText('Zoe Martín')).toBeVisible();
  });

  test('un filtro que nadie cumple lo dice en vez de confundirlo con que no hay alumnos', async ({
    page,
  }) => {
    await mockApi(page);
    await page.goto('/alumnos');

    await elegirFiltro(page, 'terreno', 'pista');

    await expect(page.getByText('Ningún alumno cumple estos filtros.')).toBeVisible();
    await expect(page.getByText('Aún no tienes alumnos.')).not.toBeVisible();
  });

  test('dar de alta un alumno lo deja ver en el listado', async ({ page }) => {
    await mockApi(page);
    await page.goto('/alumnos');

    await page.getByRole('button', { name: '+ Dar de alta alumno' }).click();
    await page.getByLabel('Nombre').fill('Marta López');
    await page.getByLabel('Email').fill('marta@club.test');
    await page.getByRole('button', { name: 'Enviar invitación' }).click();

    await expect(page.getByRole('heading', { name: 'Dar de alta alumno' })).not.toBeVisible();
    await expect(page.getByText('Marta López')).toBeVisible();
    await expect(page.getByText('4 alumnos')).toBeVisible();
  });

  test('el listado sin filtros cumple WCAG 2.1 AA', async ({ page }) => {
    await mockApi(page);
    await page.goto('/alumnos');
    await expect(page.getByText('3 alumnos')).toBeVisible();

    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(resultados.violations).toEqual([]);
  });

  test('el listado con un filtro sin resultados cumple WCAG 2.1 AA', async ({ page }) => {
    await mockApi(page);
    await page.goto('/alumnos');

    await elegirFiltro(page, 'terreno', 'pista');
    await expect(page.getByText('Ningún alumno cumple estos filtros.')).toBeVisible();

    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(resultados.violations).toEqual([]);
  });

  test('el alumno no ve la entrada de Alumnos y la URL directa lo devuelve al inicio', async ({
    page,
  }) => {
    await mockApi(page, { role: 'ALUMNO', permisos: {} });
    await page.goto('/');

    await expect(page.getByRole('link', { name: 'Alumnos' })).toHaveCount(0);

    await page.goto('/alumnos');

    await expect(page).not.toHaveURL(/\/alumnos/);
  });
});

/** Elige un valor en el desplegable de un eje, localizándolo por su nombre (accesible por label). */
async function elegirFiltro(page: Page, eje: string, valor: string): Promise<void> {
  await page.getByRole('combobox', { name: eje }).click();
  await page.getByRole('option', { name: valor, exact: true }).click();
}
