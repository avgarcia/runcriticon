import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * E2E de la vista semanal del alumno (LAL-29). Mismo patrón que `club-ajustes.spec.ts`: mockea la
 * API, sin backend real en CI. Es la primera pantalla del ALUMNO — `docs/wireframes/06-student-today.md`
 * fija que las pantallas críticas del alumno llevan el check axe AA obligatorio.
 */

const SESION_ALUMNO = { userId: 'alumno-1', clubId: 'club-1', role: 'ALUMNO' };

const SEMANA_CON_SESION = {
  semana: '2026-08-17',
  sesiones: [
    {
      dia: '2026-08-17',
      tipo: 'TEMPO',
      volumen: { tipo: 'DISTANCIA', metros: 8000 },
      ritmo: { segundosPorKm: 240 },
      notas: 'Ritmo controlado, sin forzar.',
      mensajeDelEntrenador: 'Cuidado con el sóleo, para si molesta.',
    },
  ],
};

async function mockApi(
  page: Page,
  opciones: { role?: string; plan?: unknown } = {},
): Promise<void> {
  const { role = 'ALUMNO', plan = SEMANA_CON_SESION } = opciones;

  await page.route('**/api/sesion/actual', (route) =>
    route.fulfill({ json: { ...SESION_ALUMNO, role } }),
  );
  await page.route('**/api/me/permissions', (route) => route.fulfill({ json: {} }));
  await page.route('**/api/me/plan*', (route) => route.fulfill({ json: plan }));
}

test.describe('Vista semanal del alumno', () => {
  test('el alumno ve la sesión de hoy resuelta, con el mensaje del entrenador', async ({ page }) => {
    // La sesión sembrada está en 2026-08-17: fijamos el reloj del navegador a ese día para que
    // "hoy" (derivado en cliente, `todayIsoDate()`) case con la fila devuelta por el mock.
    await page.clock.setFixedTime(new Date('2026-08-17T10:00:00'));
    await mockApi(page);

    await page.goto('/mi-plan');

    await expect(page.getByText('Tu plan de esta semana')).toBeVisible();
    await expect(page.getByText('Tempo')).toBeVisible();
    await expect(page.getByText('8000 m')).toBeVisible();
    await expect(page.getByText('4:00 /km')).toBeVisible();
    await expect(page.getByText('Cuidado con el sóleo, para si molesta.')).toBeVisible();
  });

  test('sin sesión hoy muestra el empty state único', async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-08-17T10:00:00'));
    await mockApi(page, { plan: { semana: '2026-08-17', sesiones: [] } });

    await page.goto('/mi-plan');

    await expect(page.getByText('Hoy no hay sesión programada')).toBeVisible();
    await expect(page.getByText('Habla con tu entrenador si crees que es un error')).toBeVisible();
  });

  test('un ritmo relativo sin marca invita a añadirla, sin ritmo numérico', async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-08-17T10:00:00'));
    await mockApi(page, {
      plan: {
        semana: '2026-08-17',
        sesiones: [{ dia: '2026-08-17', tipo: 'TEMPO', ritmo: { faltaMarca: '10K' } }],
      },
    });

    await page.goto('/mi-plan');

    await expect(page.getByText('Añade tu marca de 10K')).toBeVisible();
  });

  test('un ENTRENADOR no puede entrar en la pantalla del alumno', async ({ page }) => {
    await mockApi(page, { role: 'ENTRENADOR' });

    await page.goto('/mi-plan');

    await expect(page).not.toHaveURL(/\/mi-plan$/);
  });

  test('no tiene violaciones de accesibilidad WCAG 2.1 AA', async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-08-17T10:00:00'));
    await mockApi(page);
    await page.goto('/mi-plan');
    await expect(page.getByText('Tu plan de esta semana')).toBeVisible();

    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(resultados.violations).toEqual([]);
  });

  test('cerrar sesión vuelve al login', async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-08-17T10:00:00'));
    await mockApi(page);
    await page.route('**/api/sesion/cierre', (route) => route.fulfill({ status: 204 }));
    await page.goto('/mi-plan');
    await expect(page.getByText('Tu plan de esta semana')).toBeVisible();

    await page.getByRole('button', { name: 'Cerrar sesión' }).click();

    await expect(page).toHaveURL(/\/login$/);
  });
});

/**
 * Reporte de sesión (LAL-30): side sheet / modal sobre `/mi-plan`, decisión explícita del usuario
 * (no la pantalla aparte del wireframe). Cubre el loop H1 completo: crear → publicar → ejecutar →
 * reportar.
 */
test.describe('Reporte de sesión del alumno', () => {
  test('marcar HECHO con valoracion envia el reporte y refresca el estado del dia', async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-08-17T10:00:00'));
    await mockApi(page);
    let cuerpoEnviado: unknown;
    await page.route('**/api/me/reportes/2026-08-17', (route) => {
      cuerpoEnviado = route.request().postDataJSON();
      return route.fulfill({
        json: {
          ...SEMANA_CON_SESION.sesiones[0],
          reporte: { estado: 'HECHO', valoracion: 4, marcaDolor: false, reportadoEn: '2026-08-17T10:05:00Z' },
        },
      });
    });

    await page.goto('/mi-plan');
    await page.getByRole('button', { name: 'Marcar como hecho' }).click();
    await expect(page.getByRole('heading', { name: 'Reportar sesión' })).toBeVisible();

    await page.getByText('Hecho (tal cual)').click();
    await page.getByRole('radio', { name: '4' }).click();
    await page.getByRole('button', { name: 'Enviar' }).click();

    await expect(page.getByRole('heading', { name: 'Reportar sesión' })).not.toBeVisible();
    expect(cuerpoEnviado).toMatchObject({ estado: 'HECHO', valoracion: 4 });
  });

  test('NO_HECHO exige un motivo antes de poder enviar', async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-08-17T10:00:00'));
    await mockApi(page);

    await page.goto('/mi-plan');
    await page.getByRole('button', { name: 'Marcar como hecho' }).click();
    await page.getByText('No hecho').click();

    await expect(page.getByRole('button', { name: 'Enviar' })).toBeDisabled();

    await page.getByText('Cansancio').click();

    await expect(page.getByRole('button', { name: 'Enviar' })).toBeEnabled();
  });

  test('una sesion ya reportada precarga el formulario y ofrece Actualizar', async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-08-17T10:00:00'));
    await mockApi(page, {
      plan: {
        semana: '2026-08-17',
        sesiones: [
          {
            ...SEMANA_CON_SESION.sesiones[0],
            reporte: { estado: 'PARCIAL', valoracion: 2, marcaDolor: false, reportadoEn: '2026-08-17T09:00:00Z' },
          },
        ],
      },
    });

    await page.goto('/mi-plan');
    await page.getByRole('button', { name: 'Editar reporte' }).click();

    await expect(page.getByText('Editando reporte enviado')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Actualizar' })).toBeVisible();
  });

  test('no tiene violaciones de accesibilidad WCAG 2.1 AA con el dialogo de reporte abierto', async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-08-17T10:00:00'));
    await mockApi(page);
    await page.goto('/mi-plan');

    await page.getByRole('button', { name: 'Marcar como hecho' }).click();
    await expect(page.getByRole('heading', { name: 'Reportar sesión' })).toBeVisible();

    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(resultados.violations).toEqual([]);
  });

  test('sin consentimiento vigente, el envio ofrece un enlace a Mi cuenta (LAL-128 PR2)', async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-08-17T10:00:00'));
    await mockApi(page);
    await page.route('**/api/me/reportes/2026-08-17', (route) =>
      route.fulfill({
        status: 403,
        json: { code: 'CONSENTIMIENTO_NO_VIGENTE', message: 'Necesitas dar tu consentimiento' },
      }),
    );
    // La navegación tras el enlace monta /mi-cuenta de verdad: se mockea su GET para que no falle.
    await page.route('**/api/me/consentimiento', (route) =>
      route.fulfill({ json: { estado: 'PENDIENTE' } }),
    );

    await page.goto('/mi-plan');
    await page.getByRole('button', { name: 'Marcar como hecho' }).click();
    await page.getByText('Hecho (tal cual)').click();
    await page.getByRole('radio', { name: '4' }).click();
    await page.getByRole('button', { name: 'Enviar' }).click();

    const enlace = page.getByRole('link', { name: 'Ir a Mi cuenta' });
    await expect(enlace).toBeVisible();

    await enlace.click();
    await expect(page).toHaveURL(/\/mi-cuenta$/);
  });
});
