import { test, expect, Page } from '@playwright/test';

/**
 * E2E del bucle del entrenador: crear un borrador, añadir una sesión y publicar al grupo (ADR-0012 D6). Mockea la API
 * con un estado mínimo en memoria, de modo que cada pantalla vea lo que dejó la anterior; verifica además qué cuerpo
 * viaja en cada escritura. Que el frontend y el backend reales encajan lo prueba `e2e-real/`, no este spec.
 */

const SEMANA = '2026-08-17';

interface EstadoMock {
  plan: { id: string; grupoId: string; semana: string; estado: 'BORRADOR' | 'PUBLICADO' } | null;
  sesiones: unknown[];
  cuerpos: { creacion?: unknown; sesion?: unknown; publicacion: boolean };
}

async function mockApi(page: Page): Promise<EstadoMock> {
  const estado: EstadoMock = { plan: null, sesiones: [], cuerpos: { publicacion: false } };

  await page.route('**/api/**', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/sesion/actual', (route) =>
    route.fulfill({ json: { userId: 'u-1', clubId: 'club-1', role: 'ENTRENADOR' } }),
  );
  await page.route('**/api/me/permissions', (route) =>
    route.fulfill({ json: { PLAN: ['CREATE', 'LIST', 'PUBLISH'], GROUP: ['LIST'] } }),
  );

  await page.route('**/api/planes**', async (route) => {
    const peticion = route.request();
    const ruta = new URL(peticion.url()).pathname;

    if (ruta.endsWith('/api/planes') && peticion.method() === 'POST') {
      estado.cuerpos.creacion = peticion.postDataJSON();
      const { grupoId, semana } = estado.cuerpos.creacion as { grupoId: string; semana: string };
      estado.plan = { id: 'plan-1', grupoId, semana, estado: 'BORRADOR' };
      return route.fulfill({ status: 201, json: estado.plan });
    }
    if (ruta.endsWith('/api/planes') && peticion.method() === 'GET') {
      return route.fulfill({ json: { planes: estado.plan ? [estado.plan] : [] } });
    }
    if (ruta.endsWith('/sesiones') && peticion.method() === 'POST') {
      estado.cuerpos.sesion = peticion.postDataJSON();
      const sesion = { id: 'ses-1', ...(estado.cuerpos.sesion as object) };
      estado.sesiones.push(sesion);
      return route.fulfill({ status: 201, json: sesion });
    }
    if (ruta.endsWith('/publicacion') && peticion.method() === 'POST') {
      estado.cuerpos.publicacion = true;
      estado.plan = { ...estado.plan!, estado: 'PUBLICADO' };
      return route.fulfill({
        json: {
          plan: { ...estado.plan, sesiones: estado.sesiones, personalizaciones: [] },
          alumnosEnSnapshot: 2,
        },
      });
    }
    if (ruta.endsWith('/api/planes/plan-1')) {
      return route.fulfill({
        json: { ...estado.plan, sesiones: estado.sesiones, personalizaciones: [] },
      });
    }
    return route.fallback();
  });

  await page.route('**/api/grupos/g-1', (route) =>
    route.fulfill({
      json: {
        id: 'g-1',
        nombre: 'Fondistas',
        valores: [],
        total: 2,
        miembros: [
          { id: 'a-1', nombre: 'Ana Pérez', origen: 'FILTRO', ajusteManual: false },
          { id: 'a-2', nombre: 'Luis Gómez', origen: 'FILTRO', ajusteManual: false },
        ],
        excluidos: [],
      },
    }),
  );

  return estado;
}

test.describe('Crear y publicar un plan semanal', () => {
  test('el entrenador crea el borrador, añade una sesión y publica al grupo', async ({ page }) => {
    // La semana en curso se deriva en cliente; fijamos el reloj para que sea la del mock.
    await page.clock.setFixedTime(new Date(`${SEMANA}T10:00:00`));
    const estado = await mockApi(page);

    await page.goto('/planificacion/grupos/g-1/planes');
    await expect(
      page.getByText('Todavía no hay planes en borrador para este grupo.'),
    ).toBeVisible();

    await page.getByRole('button', { name: /Nuevo plan/ }).click();
    await expect(page.getByRole('link', { name: new RegExp(SEMANA) })).toBeVisible();
    expect(estado.cuerpos.creacion).toEqual({ grupoId: 'g-1', semana: SEMANA });

    await page.getByRole('link', { name: new RegExp(SEMANA) }).click();
    await expect(page.getByRole('heading', { name: 'Plan semanal', level: 1 })).toBeVisible();

    await page.getByRole('button', { name: '+ Añadir sesión' }).first().click();
    const dialogoSesion = page.getByRole('dialog');
    await expect(dialogoSesion.getByRole('heading', { name: /Nueva sesión/ })).toBeVisible();
    await dialogoSesion.getByRole('button', { name: 'Rodaje' }).click();
    await dialogoSesion.getByRole('button', { name: 'Distancia' }).click();
    await dialogoSesion.getByLabel(/Metros|Distancia/).fill('8000');
    await dialogoSesion.getByRole('button', { name: 'Guardar sesión' }).click();

    await expect(dialogoSesion).toBeHidden();
    await expect(page.getByText('8000 m')).toBeVisible();
    expect(estado.cuerpos.sesion).toMatchObject({
      tipo: 'RODAJE',
      volumen: { tipo: 'DISTANCIA', metros: 8000 },
    });

    await page.getByRole('button', { name: 'Publicar al grupo' }).click();
    const dialogoPublicar = page.getByRole('dialog');
    await expect(dialogoPublicar.getByText('2 alumnos en el grupo ahora mismo')).toBeVisible();
    await dialogoPublicar.getByRole('button', { name: 'Publicar plan' }).click();

    await expect(dialogoPublicar).toBeHidden();
    expect(estado.cuerpos.publicacion).toBe(true);
    await expect(page.getByText('PUBLICADO')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Publicar al grupo' })).toBeHidden();
  });
});
