import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * E2E de las rutas que no son pantalla crítica de ADR-0012 D6 pero sí entran en la cobertura de D7: carga
 * sin errores y cero violaciones axe WCAG 2.1 AA. Sin backend: la API se intercepta (mismo patrón que
 * `club-ajustes.spec.ts`). Cualquier endpoint no mockeado de forma explícita devuelve una lista vacía.
 */

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

const PERMISOS_ENTRENADOR = {
  GROUP: ['LIST'],
  PLAN: ['CREATE', 'LIST'],
  COACH: ['LIST'],
};

async function mockApi(page: Page, role: 'ADMIN' | 'ENTRENADOR' | 'ALUMNO'): Promise<void> {
  // Registrada la primera: Playwright prioriza las rutas registradas después.
  await page.route('**/api/**', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/sesion/actual', (route) =>
    route.fulfill({ json: { userId: 'u-1', clubId: 'club-1', role } }),
  );
  await page.route('**/api/me/marcas', (route) => route.fulfill({ json: { marcas: [] } }));
  await page.route('**/api/me/permissions', (route) =>
    route.fulfill({ json: role === 'ALUMNO' ? {} : PERMISOS_ENTRENADOR }),
  );
}

async function sinViolacionesAxe(page: Page): Promise<void> {
  const resultados = await new AxeBuilder({ page }).withTags(WCAG).analyze();
  expect(resultados.violations).toEqual([]);
}

/** Rutas públicas de identidad: no necesitan sesión. */
const RUTAS_PUBLICAS: { ruta: string; nombre: string }[] = [
  { ruta: '/entrar-con-enlace', nombre: 'solicitud de enlace mágico' },
  { ruta: '/entrar', nombre: 'consumo de enlace mágico sin token' },
  { ruta: '/restablecer', nombre: 'solicitud de restablecimiento' },
  { ruta: '/restablecer/nueva', nombre: 'nueva contraseña sin token' },
  { ruta: '/cambiar-contrasena', nombre: 'cambio de contraseña forzado' },
];

test.describe('Rutas públicas de identidad', () => {
  for (const { ruta, nombre } of RUTAS_PUBLICAS) {
    test(`${nombre} (${ruta}) carga y cumple WCAG 2.1 AA`, async ({ page }) => {
      await mockApi(page, 'ALUMNO');
      await page.goto(ruta);
      await expect(page.locator('h1, form').first()).toBeVisible();
      await sinViolacionesAxe(page);
    });
  }
});

/** Pantallas autenticadas de segundo nivel. */
const RUTAS_AUTENTICADAS: {
  ruta: string;
  rol: 'ADMIN' | 'ENTRENADOR' | 'ALUMNO';
  encabezado: string;
}[] = [
  { ruta: '/coaches', rol: 'ADMIN', encabezado: 'Entrenadores' },
  { ruta: '/alertas', rol: 'ENTRENADOR', encabezado: 'Alertas' },
  { ruta: '/mis-marcas', rol: 'ALUMNO', encabezado: 'Mis marcas' },
  { ruta: '/planificacion/grupos/g-1/planes', rol: 'ENTRENADOR', encabezado: 'Planes en borrador' },
];

test.describe('Pantallas autenticadas de segundo nivel', () => {
  for (const { ruta, rol, encabezado } of RUTAS_AUTENTICADAS) {
    test(`${ruta} muestra «${encabezado}» y cumple WCAG 2.1 AA`, async ({ page }) => {
      await mockApi(page, rol);
      await page.goto(ruta);
      await expect(page.getByRole('heading', { name: encabezado, level: 1 })).toBeVisible();
      await sinViolacionesAxe(page);
    });
  }
});
