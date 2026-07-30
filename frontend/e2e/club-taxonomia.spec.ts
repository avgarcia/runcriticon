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

interface Valor {
  id: string;
  valor: string;
  metadata: { tipo: 'EMPTY' };
  archivadoEn?: string | null;
}
interface Tag {
  id: string;
  nombre: string;
  valores: Valor[];
  archivadoEn?: string | null;
}

const taxonomiaInicial = (): { tags: Tag[] } => ({
  tags: [
    {
      id: 'tag-nivel',
      nombre: 'nivel',
      valores: [
        { id: 'val-inic', valor: 'iniciación', metadata: { tipo: 'EMPTY' } },
        { id: 'val-medio', valor: 'medio', metadata: { tipo: 'EMPTY' } },
      ],
    },
    { id: 'tag-terreno', nombre: 'terreno', valores: [] },
    // Un tag archivado en el fixture no es decorado: el atenuado con el que se pinta es
    // precisamente lo que hay que pasar por el analizador de accesibilidad.
    {
      id: 'tag-viejo',
      nombre: 'grupo-antiguo',
      valores: [
        { id: 'val-a', valor: 'grupo A', metadata: { tipo: 'EMPTY' }, archivadoEn: '2026-01-01T00:00:00Z' },
      ],
      archivadoEn: '2026-01-01T00:00:00Z',
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
    const { nombre } = route.request().postDataJSON() as { nombre: string };
    const tag: Tag = { id: `tag-${nombre}`, nombre, valores: [] };
    taxonomia.tags.push(tag);
    return route.fulfill({ status: 201, json: tag });
  });

  await page.route('**/api/taxonomia/tags/*/valores', (route) => {
    const tagId = route.request().url().split('/tags/')[1].split('/')[0];
    const { valor } = route.request().postDataJSON() as { valor: string };
    const nuevo: Valor = { id: `val-${valor}`, valor, metadata: { tipo: 'EMPTY' } };
    taxonomia.tags.find((tag) => tag.id === tagId)?.valores.push(nuevo);
    return route.fulfill({ status: 201, json: nuevo });
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

  test('el entrenador no ve la entrada de Taxonomía en el menú', async ({ page }) => {
    await mockApi(page, { role: 'ENTRENADOR', permisos: { TAXONOMY: ['LIST'] } });
    await page.goto('/');

    await expect(page.getByRole('navigation', { name: 'Principal' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Taxonomía' })).toHaveCount(0);
  });
});
