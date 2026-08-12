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
  GROUP: ['LIST', 'CREATE', 'UPDATE'],
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
  { id: 'a-1', nombre: 'Ana Ruiz', email: 'ana@club.test', estado: 'ACTIVO', tags: ['val-medio', 'val-trail'] },
  { id: 'a-2', nombre: 'Pedro Cordero', email: 'pedro@club.test', estado: 'ACTIVO', tags: ['val-medio'] },
  { id: 'a-3', nombre: 'Zoe Martín', email: 'zoe@club.test', estado: 'ACTIVO', tags: ['val-alto', 'val-trail'] },
];

const gruposIniciales = () => [
  { id: 'g-1', nombre: 'Maratón nivel medio', valores: ['val-medio'] },
  // Un grupo sin nadie dentro no es decorado: su aviso es lo que hay que pasar por el analizador.
  // 'val-pista' no lo tiene ningún alumno del fixture, así que el filtro queda genuinamente vacío
  // ahora que el total se recalcula de verdad a partir de ALUMNOS (antes era un 0 fijo sin relación).
  { id: 'g-2', nombre: 'Trail avanzado', valores: ['val-alto', 'val-pista'] },
];

function cumpleFiltro(alumno: (typeof ALUMNOS)[number], valores: readonly string[]): boolean {
  return valores.every((valor) => alumno.tags.includes(valor));
}

/**
 * Recalcula miembros y excluidos igual que el backend real: `(cumple ∨ incluido) ∧ ¬excluido`, con
 * `origen` recalculado en cada lectura y `ajusteManual` como pregunta aparte. `overrides` vive fuera
 * de esta función porque las mismas excepciones sobreviven entre la consulta y el ajuste dentro de
 * un mismo test.
 */
function detalleDe(
  grupo: { id: string; nombre: string; valores: string[] },
  overrides: Record<string, boolean>,
) {
  const miembros = ALUMNOS.filter((alumno) => overrides[alumno.id] !== false).filter(
    (alumno) => overrides[alumno.id] === true || cumpleFiltro(alumno, grupo.valores),
  );
  const excluidos = ALUMNOS.filter((alumno) => overrides[alumno.id] === false);
  return {
    id: grupo.id,
    nombre: grupo.nombre,
    valores: grupo.valores,
    total: miembros.length,
    miembros: miembros.map((alumno) => ({
      id: alumno.id,
      nombre: alumno.nombre,
      origen: cumpleFiltro(alumno, grupo.valores) ? 'FILTRO' : 'INCLUSION_MANUAL',
      ajusteManual: overrides[alumno.id] !== undefined,
    })),
    excluidos: excluidos.map((alumno) => ({
      id: alumno.id,
      nombre: alumno.nombre,
      cumpleFiltro: cumpleFiltro(alumno, grupo.valores),
    })),
  };
}

async function mockApi(
  page: Page,
  opciones: { role?: string; permisos?: Record<string, string[]> } = {},
): Promise<void> {
  const { role = 'ADMIN', permisos = PERMISOS_STAFF } = opciones;
  const grupos = gruposIniciales();
  // Excepciones manuales por grupo: `overridesPorGrupo['g-1']['a-2'] = false` es una exclusión.
  const overridesPorGrupo: Record<string, Record<string, boolean>> = {};

  await page.route('**/api/sesion/actual', (route) =>
    route.fulfill({ json: { userId: 'u-1', clubId: CLUB.id, role } }),
  );
  await page.route('**/api/me/permissions', (route) => route.fulfill({ json: permisos }));
  await page.route('**/api/club', (route) => route.fulfill({ json: CLUB }));
  await page.route('**/api/taxonomia', (route) => route.fulfill({ json: TAXONOMIA }));

  await page.route('**/api/alumnos**', (route) =>
    route.fulfill({
      json: {
        alumnos: ALUMNOS.map(({ id, nombre, email, estado, tags }) => ({
          id,
          nombre,
          email,
          estado,
          valores: tags,
        })),
      },
    }),
  );

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

  // Un único segmento tras '/grupos/' que no sea 'miembros' es un grupoId: consulta de detalle. El
  // `route.fallback()` cede al handler de arriba en vez de fiar el reparto al orden de alta —
  // '*' no distingue "miembros" de un id real, así que aquí se resuelve explícitamente.
  await page.route('**/api/grupos/*', (route) => {
    const grupoId = new URL(route.request().url()).pathname.split('/').pop();
    if (grupoId === 'miembros') {
      route.fallback();
      return;
    }
    const grupo = grupos.find((g) => g.id === grupoId);
    if (!grupo) {
      route.fulfill({ status: 404, json: { code: 'GROUP_NOT_FOUND' } });
      return;
    }
    overridesPorGrupo[grupo.id] ??= {};
    route.fulfill({ json: detalleDe(grupo, overridesPorGrupo[grupo.id]) });
  });

  await page.route('**/api/grupos/*/overrides/*', (route) => {
    const [, , grupoId, , alumnoId] = new URL(route.request().url()).pathname.split('/').slice(-5);
    const grupo = grupos.find((g) => g.id === grupoId);
    if (!grupo) {
      route.fulfill({ status: 404, json: { code: 'GROUP_NOT_FOUND' } });
      return;
    }
    overridesPorGrupo[grupo.id] ??= {};
    if (route.request().method() === 'DELETE') {
      delete overridesPorGrupo[grupo.id][alumnoId];
      route.fulfill({ status: 204 });
      return;
    }
    const body = route.request().postDataJSON() as { incluido: boolean };
    overridesPorGrupo[grupo.id][alumnoId] = body.incluido;
    route.fulfill({ json: detalleDe(grupo, overridesPorGrupo[grupo.id]) });
  });

  await page.route('**/api/grupos', (route) => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON() as { nombre: string; valores: string[] };
      grupos.push({ id: `g-${grupos.length + 1}`, nombre: body.nombre, valores: body.valores });
      route.fulfill({
        status: 201,
        json: { id: `g-${grupos.length}`, nombre: body.nombre, valores: body.valores },
      });
      return;
    }
    route.fulfill({
      json: {
        grupos: grupos.map((grupo) => ({
          id: grupo.id,
          nombre: grupo.nombre,
          valores: grupo.valores,
          totalAlumnos: detalleDe(grupo, overridesPorGrupo[grupo.id] ?? {}).total,
        })),
      },
    });
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

  test('ajustar la pertenencia a mano actualiza el grupo y el recuento del listado', async ({
    page,
  }) => {
    await mockApi(page);
    await page.goto('/club/grupos');
    const tarjeta = page.getByRole('listitem').filter({ hasText: 'Maratón nivel medio' });
    await expect(tarjeta.getByText('2 alumnos')).toBeVisible();

    await tarjeta.getByRole('button', { name: 'Gestionar miembros' }).click();
    const dialogo = page.getByRole('dialog');
    await expect(dialogo.getByRole('heading', { name: 'Maratón nivel medio' })).toBeVisible();
    const filaPedro = dialogo.getByRole('listitem').filter({ hasText: 'Pedro Cordero' });
    await expect(filaPedro.getByText('Por filtro')).toBeVisible();

    // Excluir a alguien que sí cumple el filtro: AC2 de LAL-92.
    await filaPedro.getByRole('button', { name: 'Excluir' }).click();
    await expect(dialogo.getByText('Excluidos manualmente (1)')).toBeVisible();
    const excluidoPedro = dialogo.getByRole('listitem').filter({ hasText: 'Pedro Cordero' });
    await expect(excluidoPedro.getByRole('button', { name: 'Restaurar' })).toBeVisible();

    // Restaurar lo devuelve al filtro, no lo deja como una inclusión manual.
    await excluidoPedro.getByRole('button', { name: 'Restaurar' }).click();
    await expect(dialogo.getByText('Excluidos manualmente (0)')).toBeVisible();
    await expect(filaPedro.getByText('Por filtro')).toBeVisible();

    // Incluir a alguien que NO cumple el filtro: AC1 de LAL-92. Zoe no tiene 'val-medio'.
    await dialogo.getByPlaceholder('Buscar alumno').fill('Zoe');
    await dialogo.getByRole('listitem').filter({ hasText: 'Zoe Martín' }).getByRole('button', { name: 'Incluir' }).click();
    const filaZoe = dialogo.getByRole('listitem').filter({ hasText: 'Zoe Martín' });
    await expect(filaZoe.getByText('Por excepción')).toBeVisible();
    await expect(filaZoe.getByRole('button', { name: 'Quitar excepción' })).toBeVisible();

    await dialogo.getByRole('button', { name: 'Cerrar' }).click();

    // El cambio se refleja en la tarjeta sin recargar la página a mano.
    await expect(tarjeta.getByText('3 alumnos')).toBeVisible();
  });

  test('el diálogo de ajuste manual cumple WCAG 2.1 AA', async ({ page }) => {
    await mockApi(page);
    await page.goto('/club/grupos');
    await page
      .getByRole('listitem')
      .filter({ hasText: 'Maratón nivel medio' })
      .getByRole('button', { name: 'Gestionar miembros' })
      .click();
    await expect(page.getByRole('dialog').getByText('Por filtro').first()).toBeVisible();

    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(resultados.violations).toEqual([]);
  });
});

/** Elige un valor en la última condición añadida, localizando el desplegable por su etiqueta. */
async function elegirValor(page: Page, opcion: string): Promise<void> {
  await page.getByRole('combobox', { name: 'Valor de la condición' }).last().click();
  await page.getByRole('option', { name: opcion, exact: true }).click();
}
