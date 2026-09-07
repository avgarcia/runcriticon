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
  STUDENT: ['INVITE', 'LIST', 'CLASSIFY'],
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

  // Registrado después de '**/api/alumnos**': Playwright prueba las rutas en orden inverso de alta, así
  // que esta, más específica, intercepta el PUT de tags antes de caer en el handler general.
  await page.route('**/api/alumnos/*/tags', (route) => {
    const id = route.request().url().match(/\/alumnos\/([^/]+)\/tags/)?.[1];
    const body = route.request().postDataJSON() as { valores: string[] };
    const alumno = alumnos.find((a) => a.id === id);
    if (alumno) alumno.valores = body.valores;
    route.fulfill({ json: { asignados: [] } });
  });

  // Registradas al final: '**/api/alumnos**' también casa con estas URLs (el `**` cruza barras), y
  // Playwright prueba las rutas en orden inverso de alta. El doble cuenta solo a quien cambia de
  // verdad, igual que el backend: es lo que prueba que la pantalla lee el recuento del servidor y no
  // el tamaño de su propia selección.
  await page.route('**/api/alumnos/tags/asignacion-masiva', (route) => {
    const body = route.request().postDataJSON() as { alumnos: string[]; valorId: string };
    let actualizados = 0;
    for (const id of new Set(body.alumnos)) {
      const alumno = alumnos.find((a) => a.id === id);
      if (alumno && !alumno.valores.includes(body.valorId)) {
        alumno.valores.push(body.valorId);
        actualizados++;
      }
    }
    route.fulfill({ json: { alumnosActualizados: actualizados } });
  });

  await page.route('**/api/alumnos/tags/desasignacion-masiva', (route) => {
    const body = route.request().postDataJSON() as { alumnos: string[]; valorId: string };
    let actualizados = 0;
    for (const id of new Set(body.alumnos)) {
      const alumno = alumnos.find((a) => a.id === id);
      if (alumno && alumno.valores.includes(body.valorId)) {
        alumno.valores = alumno.valores.filter((v) => v !== body.valorId);
        actualizados++;
      }
    }
    route.fulfill({ json: { alumnosActualizados: actualizados } });
  });
}

test.describe('Alumnos del club', () => {
  test('el listado inicial trae a todos, sin ningún filtro', async ({ page }) => {
    await mockApi(page);
    await page.goto('/alumnos');

    await expect(nombreEnTabla(page, 'Ana Ruiz')).toBeVisible();
    await expect(nombreEnTabla(page, 'Pedro Cordero')).toBeVisible();
    await expect(nombreEnTabla(page, 'Zoe Martín')).toBeVisible();
    await expect(page.getByText('3 alumnos')).toBeVisible();
    await expect(page.getByText('Invitado')).toBeVisible();
  });

  test('filtrar por un valor reduce la tabla y el chip lo restaura al quitarlo', async ({ page }) => {
    await mockApi(page);
    await page.goto('/alumnos');
    await expect(page.getByText('3 alumnos')).toBeVisible();

    await elegirFiltro(page, 'nivel', 'medio');

    await expect(page.getByText('1 alumno', { exact: true })).toBeVisible();
    await expect(nombreEnTabla(page, 'Ana Ruiz')).toBeVisible();
    await expect(nombreEnTabla(page, 'Zoe Martín')).not.toBeVisible();
    // El texto "nivel: medio" también aparece como tag de Ana en la tabla: se acota al grupo del chip.
    await expect(page.getByRole('group', { name: 'Filtros activos' }).getByText('nivel: medio')).toBeVisible();

    await page.getByRole('button', { name: 'Quitar filtro nivel: medio' }).click();

    await expect(page.getByText('3 alumnos')).toBeVisible();
    await expect(nombreEnTabla(page, 'Zoe Martín')).toBeVisible();
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
    await expect(nombreEnTabla(page, 'Marta López')).toBeVisible();
    await expect(page.getByText('4 alumnos')).toBeVisible();
  });

  test('editar los tags de un alumno actualiza sus chips en la fila', async ({ page }) => {
    await mockApi(page);
    await page.goto('/alumnos');
    const filaZoe = page.getByRole('row', { name: /Zoe Martín/ });
    await expect(filaZoe.getByText('nivel: alto')).toBeVisible();

    await filaZoe.getByRole('button', { name: 'Editar tags' }).click();
    await elegirTagsEnDialogo(page, 'nivel', 'medio');
    await page.getByRole('button', { name: 'Guardar' }).click();

    await expect(page.getByRole('heading', { name: 'Zoe Martín' })).not.toBeVisible();
    await expect(filaZoe.getByText('nivel: medio')).toBeVisible();
    await expect(filaZoe.getByText('nivel: alto')).not.toBeVisible();
  });

  test('seleccionar varios alumnos y asignarles un tag lo deja en sus filas', async ({ page }) => {
    await mockApi(page);
    await page.goto('/alumnos');
    const filaAna = page.getByRole('row', { name: /Ana Ruiz/ });
    const filaZoe = page.getByRole('row', { name: /Zoe Martín/ });

    await filaAna.getByRole('checkbox', { name: 'Seleccionar a Ana Ruiz' }).check();
    await filaZoe.getByRole('checkbox', { name: 'Seleccionar a Zoe Martín' }).check();
    await expect(page.getByText('2 seleccionados')).toBeVisible();

    await page.getByRole('button', { name: 'Asignar tag' }).click();
    await elegirEjeYValorEnDialogoMasivo(page, 'terreno', 'trail');
    await page.getByRole('dialog').getByRole('button', { name: 'Asignar' }).click();

    // Ana ya tenía "terreno: trail"; solo Zoe cambia de verdad. El diálogo cierra y recarga con el
    // resultado del servidor, igual que comprueba "editar los tags de un alumno..." para el caso individual.
    await expect(page.getByRole('dialog')).not.toBeVisible();
    await expect(filaZoe.getByText('terreno: trail')).toBeVisible();
  });

  test('quitar un tag en masa no falla por los que no lo tenían', async ({ page }) => {
    await mockApi(page);
    await page.goto('/alumnos');

    await page.getByRole('checkbox', { name: 'Seleccionar todos los alumnos visibles' }).check();
    await expect(page.getByText('3 seleccionados')).toBeVisible();

    await page.getByRole('button', { name: 'Quitar tag' }).click();
    await elegirEjeYValorEnDialogoMasivo(page, 'nivel', 'medio');
    await page.getByRole('dialog').getByRole('button', { name: 'Quitar' }).click();

    // Solo Ana tenía "nivel: medio"; Pedro y Zoe no lo tenían y no hacen fallar la operación.
    await expect(page.getByRole('dialog')).not.toBeVisible();
    await expect(page.getByRole('row', { name: /Ana Ruiz/ }).getByText('nivel: medio')).not.toBeVisible();
    await expect(nombreEnTabla(page, 'Pedro Cordero')).toBeVisible();
    await expect(nombreEnTabla(page, 'Zoe Martín')).toBeVisible();
  });

  test('la casilla de cabecera selecciona solo los alumnos que el filtro deja ver', async ({ page }) => {
    await mockApi(page);
    await page.goto('/alumnos');
    await elegirFiltro(page, 'nivel', 'medio');
    await expect(page.getByText('1 alumno', { exact: true })).toBeVisible();

    await page.getByRole('checkbox', { name: 'Seleccionar todos los alumnos visibles' }).check();

    await expect(page.getByText('1 seleccionado')).toBeVisible();
  });

  test('el listado con la barra de acciones en masa cumple WCAG 2.1 AA', async ({ page }) => {
    await mockApi(page);
    await page.goto('/alumnos');

    await page.getByRole('checkbox', { name: 'Seleccionar a Ana Ruiz' }).check();
    await expect(page.getByText('1 seleccionado')).toBeVisible();

    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(resultados.violations).toEqual([]);
  });

  test('el diálogo de etiquetado en masa cumple WCAG 2.1 AA', async ({ page }) => {
    await mockApi(page);
    await page.goto('/alumnos');
    await page.getByRole('checkbox', { name: 'Seleccionar a Ana Ruiz' }).check();
    await page.getByRole('button', { name: 'Asignar tag' }).click();
    await expect(page.getByRole('dialog')).toBeVisible();

    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(resultados.violations).toEqual([]);
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

/**
 * Igual que `elegirFiltro`, pero acotado al diálogo: con el diálogo abierto hay dos combobox con el
 * mismo nombre de eje (el del filtro del listado, detrás, y el del diálogo).
 */
async function elegirTagsEnDialogo(page: Page, eje: string, valor: string): Promise<void> {
  await page.getByRole('dialog').getByRole('combobox', { name: eje }).click();
  await page.getByRole('option', { name: valor, exact: true }).click();
}

/**
 * El diálogo de etiquetado en masa tiene dos combobox con nombre genérico ("Eje"/"Valor"), a diferencia
 * de los combobox del filtro de fondo (nombrados por el eje concreto): no hace falta acotar por
 * `getByRole('dialog')` para desambiguar, pero se hace igualmente para que el helper no dependa de que
 * el filtro de fondo nunca use esos mismos nombres.
 */
async function elegirEjeYValorEnDialogoMasivo(page: Page, eje: string, valor: string): Promise<void> {
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('combobox', { name: 'Eje' }).click();
  await page.getByRole('option', { name: eje, exact: true }).click();
  await dialog.getByRole('combobox', { name: 'Valor' }).click();
  await page.getByRole('option', { name: valor, exact: true }).click();
}

/**
 * El nombre de la celda, no un `getByText` suelto: cada fila lleva ahora una casilla de selección
 * cuyo texto accesible ("Seleccionar a {nombre}") contiene el nombre como subcadena, así que
 * `page.getByText(nombre)` resuelve a dos elementos (la celda y la casilla) y viola el modo estricto.
 */
function nombreEnTabla(page: Page, nombre: string) {
  return page.getByRole('cell', { name: nombre, exact: true });
}
