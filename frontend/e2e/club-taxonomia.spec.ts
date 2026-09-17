import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * E2E del editor de taxonomía. Como el de ajustes del club, intercepta la API: sin backend en CI no
 * hay sesión y el `authGuard` redirigiría a /login antes de pintar nada.
 *
 * El doble de la API guarda estado entre llamadas para poder comprobar que la pantalla refleja lo
 * que devuelve el servidor tras cada operación, que es justo lo que se quiere probar.
 */

const CLUB = { id: 'club-1', nombre: 'Club Atletismo Pinares', slug: null };

const PERMISOS_ADMIN = {
  COACH: ['INVITE', 'LIST'],
  STUDENT: ['INVITE'],
  USER: ['REVOKE_SESSIONS', 'DEACTIVATE'],
  CLUB: ['UPDATE'],
  TAXONOMY: ['LIST', 'MANAGE'],
};

type Metadata =
  | { tipo: 'EMPTY' }
  | { tipo: 'RACE'; fecha: string; distancia: '5K' | '10K' | '21K' | '42K' };
interface Valor {
  id: string;
  valor: string;
  metadata: Metadata;
  archivadoEn?: string | null;
}
interface Tag {
  id: string;
  nombre: string;
  tipo: 'SIMPLE' | 'RACE';
  valores: Valor[];
  archivadoEn?: string | null;
}

const taxonomiaInicial = (): { tags: Tag[] } => ({
  tags: [
    {
      id: 'tag-nivel',
      nombre: 'nivel',
      tipo: 'SIMPLE',
      valores: [
        { id: 'val-inic', valor: 'iniciación', metadata: { tipo: 'EMPTY' } },
        { id: 'val-medio', valor: 'medio', metadata: { tipo: 'EMPTY' } },
      ],
    },
    { id: 'tag-terreno', nombre: 'terreno', tipo: 'SIMPLE', valores: [] },
    // Un tag archivado en el fixture no es decorado: el atenuado con el que se pinta es
    // precisamente lo que hay que pasar por el analizador de accesibilidad.
    {
      id: 'tag-viejo',
      nombre: 'grupo-antiguo',
      tipo: 'SIMPLE',
      valores: [
        { id: 'val-a', valor: 'grupo A', metadata: { tipo: 'EMPTY' }, archivadoEn: '2026-01-01T00:00:00Z' },
      ],
      archivadoEn: '2026-01-01T00:00:00Z',
    },
    // Eje de tipo carrera (LAL-84): el diálogo de carrera y su formulario también pasan por axe.
    {
      id: 'tag-objetivo',
      nombre: 'objetivo',
      tipo: 'RACE',
      valores: [{ id: 'val-sin-carrera', valor: 'sin carrera', metadata: { tipo: 'EMPTY' } }],
    },
  ],
});

async function mockApi(
  page: Page,
  opciones: { role?: string; permisos?: Record<string, string[]> } = {},
): Promise<void> {
  const { role = 'ADMIN', permisos = PERMISOS_ADMIN } = opciones;
  const taxonomia = taxonomiaInicial();

  await page.route('**/api/sesion/actual', (route) =>
    route.fulfill({ json: { userId: 'u-1', clubId: CLUB.id, role } }),
  );
  await page.route('**/api/me/permissions', (route) => route.fulfill({ json: permisos }));
  await page.route('**/api/club', (route) => route.fulfill({ json: CLUB }));

  await page.route('**/api/taxonomia', (route) => route.fulfill({ json: taxonomia }));

  await page.route('**/api/taxonomia/tags', (route) => {
    const { nombre, tipo } = route.request().postDataJSON() as { nombre: string; tipo?: 'SIMPLE' | 'RACE' };
    const tag: Tag = { id: `tag-${nombre}`, nombre, tipo: tipo ?? 'SIMPLE', valores: [] };
    taxonomia.tags.push(tag);
    return route.fulfill({ status: 201, json: tag });
  });

  await page.route('**/api/taxonomia/tags/*/tipo', (route) => {
    const tagId = route.request().url().split('/tags/')[1].split('/')[0];
    const { tipo } = route.request().postDataJSON() as { tipo: 'SIMPLE' | 'RACE' };
    const tag = taxonomia.tags.find((candidato) => candidato.id === tagId)!;
    tag.tipo = tipo;
    return route.fulfill({ json: tag });
  });

  await page.route('**/api/taxonomia/tags/*/valores', (route) => {
    const tagId = route.request().url().split('/tags/')[1].split('/')[0];
    const { valor, metadata } = route.request().postDataJSON() as { valor: string; metadata?: Metadata };
    const nuevo: Valor = { id: `val-${valor}`, valor, metadata: metadata ?? { tipo: 'EMPTY' } };
    taxonomia.tags.find((tag) => tag.id === tagId)?.valores.push(nuevo);
    return route.fulfill({ status: 201, json: nuevo });
  });

  await page.route('**/api/taxonomia/valores/*/metadata', (route) => {
    const valorId = route.request().url().split('/valores/')[1].split('/')[0];
    const metadata = route.request().postDataJSON() as Metadata;
    for (const tag of taxonomia.tags) {
      const valor = tag.valores.find((candidato) => candidato.id === valorId);
      if (valor) {
        valor.metadata = metadata;
        return route.fulfill({ json: valor });
      }
    }
    return route.fulfill({ status: 404 });
  });

  await page.route('**/api/taxonomia/tags/archivados/*', (route) => {
    const tagId = route.request().url().split('/archivados/')[1];
    const tag = taxonomia.tags.find((candidato) => candidato.id === tagId)!;
    tag.archivadoEn = route.request().method() === 'PUT' ? '2026-07-30T10:00:00Z' : null;
    return route.fulfill({ json: tag });
  });

  await page.route('**/api/taxonomia/tags/*', (route) => {
    const tagId = route.request().url().split('/tags/')[1];
    const { nombre } = route.request().postDataJSON() as { nombre: string };
    const tag = taxonomia.tags.find((candidato) => candidato.id === tagId)!;
    tag.nombre = nombre;
    return route.fulfill({ json: tag });
  });
}

const abrirEditor = async (page: Page): Promise<void> => {
  await page.goto('/club/taxonomia');
  await expect(page.getByRole('heading', { name: 'Taxonomía del club' })).toBeVisible();
};

test.describe('Editor de taxonomía', () => {
  test('el admin ve sus tags y el detalle del primero sin hacer clic', async ({ page }) => {
    await mockApi(page);
    await abrirEditor(page);

    const lista = page.getByRole('navigation', { name: 'Tags del club' });
    await expect(lista.getByRole('button', { name: /nivel/ })).toBeVisible();
    await expect(lista.getByRole('button', { name: /terreno/ })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'nivel' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Valores (2)' })).toBeVisible();
  });

  test('no tiene violaciones de accesibilidad WCAG 2.1 AA, tampoco en lo archivado', async ({
    page,
  }) => {
    await mockApi(page);
    await abrirEditor(page);
    await page.getByRole('button', { name: /grupo-antiguo/ }).click();
    await expect(page.getByText('Este tag está archivado')).toBeVisible();

    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(resultados.violations).toEqual([]);
  });

  test('el diálogo de carrera no tiene violaciones de accesibilidad WCAG 2.1 AA', async ({ page }) => {
    await mockApi(page);
    await abrirEditor(page);
    await page.getByRole('button', { name: /objetivo/ }).click();
    await page.getByRole('button', { name: '+ Añadir valor' }).click();
    await expect(page.getByRole('textbox', { name: 'Valor' })).toBeVisible();

    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(resultados.violations).toEqual([]);
  });

  test('renombrar un tag lo refleja en la lista y en el detalle', async ({ page }) => {
    await mockApi(page);
    await abrirEditor(page);

    await page.getByRole('button', { name: 'Renombrar tag' }).click();
    // Por rol y no por `getByLabel`: el diálogo se anuncia con su propio título, así que una etiqueta
    // suelta casaría también con el contenedor del diálogo.
    await page.getByRole('textbox', { name: 'Nombre' }).fill('categoría');
    await page.getByRole('button', { name: 'Guardar' }).click();

    await expect(page.getByRole('heading', { name: 'categoría' })).toBeVisible();
    await expect(
      page.getByRole('navigation', { name: 'Tags del club' }).getByRole('button', { name: /categoría/ }),
    ).toBeVisible();
  });

  test('añadir un valor lo deja disponible en el tag', async ({ page }) => {
    await mockApi(page);
    await abrirEditor(page);

    await page.getByRole('button', { name: '+ Añadir valor' }).click();
    await page.getByRole('textbox', { name: 'Valor' }).fill('alto');
    await page.getByRole('button', { name: 'Añadir' }).click();

    await expect(page.getByRole('heading', { name: 'Valores (3)' })).toBeVisible();
    await expect(page.getByText('alto')).toBeVisible();
  });

  test('el menú de un valor se abre y se recorre con el teclado', async ({ page }) => {
    await mockApi(page);
    await abrirEditor(page);

    await page.getByRole('button', { name: 'Opciones de iniciación' }).focus();
    await page.keyboard.press('Enter');

    await expect(page.getByRole('menuitem', { name: 'Renombrar' })).toBeVisible();
    await page.keyboard.press('ArrowDown');
    await expect(page.getByRole('menuitem', { name: 'Archivar' })).toBeFocused();
  });

  test('añadir una carrera al eje objetivo la muestra con fecha y distancia', async ({ page }) => {
    await mockApi(page);
    await abrirEditor(page);

    await page.getByRole('button', { name: /objetivo/ }).click();
    await page.getByRole('button', { name: '+ Añadir valor' }).click();
    await page.getByRole('textbox', { name: 'Valor' }).fill('Maratón de Valencia');
    await page.getByLabel('Fecha de la carrera').fill('2026-12-06');
    await page.getByRole('combobox', { name: 'Distancia de la carrera' }).click();
    await page.getByRole('option', { name: '42K' }).click();
    await page.getByRole('button', { name: 'Añadir' }).click();

    const detalle = page.locator('rc-tag-detail');
    await expect(detalle.getByText('Maratón de Valencia')).toBeVisible();
    await expect(detalle.getByText('42K')).toBeVisible();
  });

  test('convertir el eje nivel en carrera permite añadirle valores con fecha y distancia', async ({
    page,
  }) => {
    await mockApi(page);
    await abrirEditor(page);

    await expect(page.getByText('Enum simple')).toBeVisible();
    await page.getByRole('button', { name: 'Convertir en carrera' }).click();
    await expect(page.getByText('Enum con metadata')).toBeVisible();

    await page.getByRole('button', { name: '+ Añadir valor' }).click();
    await expect(page.getByRole('textbox', { name: 'Valor' })).toBeVisible();
    await expect(page.getByLabel('Fecha de la carrera')).toBeVisible();
  });

  test('el entrenador no ve la entrada de Taxonomía en el menú', async ({ page }) => {
    await mockApi(page, { role: 'ENTRENADOR', permisos: { TAXONOMY: ['LIST'] } });
    await page.goto('/');

    await expect(page.getByRole('navigation', { name: 'Principal' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Taxonomía' })).toHaveCount(0);
  });
});
