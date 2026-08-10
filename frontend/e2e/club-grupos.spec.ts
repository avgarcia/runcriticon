import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * E2E de los grupos del club: listado y constructor. Como los demás, intercepta la API — sin backend
 * en CI no hay sesión y el `authGuard` redirigiría a /login antes de pintar nada.
 *
 * El doble de la previsualización **calcula la intersección de verdad** a partir de los tags de cada
 * alumno del fixture: si devolviera un número fijo, el recorrido no probaría que la pantalla manda
 * el filtro que el usuario acaba de componer.
 */

const CLUB = { id: 'club-1', nombre: 'Club Atletismo Pinares', slug: null };

const PERMISOS_STAFF = {
  COACH: ['INVITE', 'LIST'],
  STUDENT: ['INVITE'],
  CLUB: ['UPDATE'],
  TAXONOMY: ['LIST', 'MANAGE'],
  GROUP: ['LIST', 'CREATE'],
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
        // Un valor que no tiene ningún alumno: es lo que permite componer un filtro imposible.
        { id: 'val-pista', valor: 'pista', metadata: { tipo: 'EMPTY' } },
      ],
    },
  ],
};

const ALUMNOS = [
  { id: 'a-1', nombre: 'Ana Ruiz', tags: ['val-medio', 'val-trail'] },
  { id: 'a-2', nombre: 'Pedro Cordero', tags: ['val-medio'] },
  { id: 'a-3', nombre: 'Zoe Martín', tags: ['val-alto', 'val-trail'] },
];

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
  const grupos = gruposIniciales();

  await page.route('**/api/sesion/actual', (route) =>
    route.fulfill({ json: { userId: 'u-1', clubId: CLUB.id, role } }),
  );
  await page.route('**/api/me/permissions', (route) => route.fulfill({ json: permisos }));
  await page.route('**/api/club', (route) => route.fulfill({ json: CLUB }));
  await page.route('**/api/taxonomia', (route) => route.fulfill({ json: TAXONOMIA }));

  await page.route('**/api/grupos/miembros**', (route) => {
    const url = new URL(route.request().url());
    const filtro = url.searchParams.getAll('tagValueId');
    const alumnos = filtro.length
      ? ALUMNOS.filter((alumno) => filtro.every((valor) => alumno.tags.includes(valor)))
      : [];
    route.fulfill({
      json: { total: alumnos.length, alumnos: alumnos.map(({ id, nombre }) => ({ id, nombre })) },
    });
  });

  await page.route('**/api/grupos', (route) => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON() as { nombre: string; valores: string[] };
      const alumnos = ALUMNOS.filter((alumno) =>
        body.valores.every((valor) => alumno.tags.includes(valor)),
      );
      grupos.push({
        id: `g-${grupos.length + 1}`,
        nombre: body.nombre,
        valores: body.valores,
        totalAlumnos: body.valores.length ? alumnos.length : 0,
      });
      route.fulfill({
        status: 201,
        json: { id: `g-${grupos.length}`, nombre: body.nombre, valores: body.valores },
      });
      return;
    }
    route.fulfill({ json: { grupos } });
  });
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

  test('construir un grupo actualiza la vista previa y lo deja en el listado', async ({ page }) => {
    await mockApi(page);
    await page.goto('/club/grupos');

    await page.getByRole('link', { name: '+ Nuevo grupo' }).first().click();
    await expect(page.getByRole('heading', { name: 'Nuevo grupo' })).toBeVisible();

    await page.getByRole('textbox').first().fill('Trail medio');

    // Sin condiciones no entra nadie: lo dice el servidor, no una suposición del cliente.
    await expect(page.getByText('Añade una condición para ver quién entra en el grupo.')).toBeVisible();

    await page.getByRole('button', { name: '+ Añadir condición' }).click();
    await elegirValor(page, 'medio');
    await expect(page.getByText('2', { exact: true })).toBeVisible();

    await page.getByRole('button', { name: '+ Añadir condición' }).click();
    await elegirValor(page, 'trail');
    await expect(page.getByText('1', { exact: true })).toBeVisible();
    await expect(page.getByText('Ana Ruiz')).toBeVisible();

    await page.getByRole('button', { name: 'Guardar' }).click();

    await expect(page.getByRole('heading', { name: 'Trail medio' })).toBeVisible();
  });

  test('un filtro sin nadie pide confirmación antes de guardar', async ({ page }) => {
    await mockApi(page);
    await page.goto('/club/grupos/nuevo');

    await page.getByRole('textbox').first().fill('Grupo imposible');
    await page.getByRole('button', { name: '+ Añadir condición' }).click();
    await elegirValor(page, 'alto');
    await page.getByRole('button', { name: '+ Añadir condición' }).click();
    await elegirValor(page, 'pista');

    await expect(page.getByText('Ningún alumno cumple este filtro. Quizá sea demasiado estricto.')).toBeVisible();

    await page.getByRole('button', { name: 'Guardar' }).click();

    await expect(page.getByRole('heading', { name: 'Un grupo sin alumnos' })).toBeVisible();
  });

  test('el constructor con un filtro sin resultados cumple WCAG 2.1 AA', async ({ page }) => {
    await mockApi(page);
    await page.goto('/club/grupos/nuevo');

    await page.getByRole('button', { name: '+ Añadir condición' }).click();
    await elegirValor(page, 'alto');
    await page.getByRole('button', { name: '+ Añadir condición' }).click();
    await elegirValor(page, 'pista');
    await expect(page.getByText('Ningún alumno cumple este filtro. Quizá sea demasiado estricto.')).toBeVisible();

    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(resultados.violations).toEqual([]);
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

/** Elige un valor en la última condición añadida, localizando el desplegable por su etiqueta. */
async function elegirValor(page: Page, opcion: string): Promise<void> {
  await page.getByRole('combobox', { name: 'Valor de la condición' }).last().click();
  await page.getByRole('option', { name: opcion, exact: true }).click();
}
