import { defineConfig, devices } from '@playwright/test';

/**
 * E2E contra el backend real (`e2e-real/`), aparte de `playwright.config.ts`: aquel mockea la red con
 * `page.route()` y no necesita nada levantado; este necesita el entorno de `infrastructure/e2e` (la imagen con la
 * SPA, la API y Postgres, en `http://localhost:8080`) y no mockea nada. Se lanza con `npm run e2e:real`.
 */
export default defineConfig({
  testDir: './e2e-real',
  // Un solo recorrido sobre un entorno compartido y con estado: en serie, sin paralelismo entre ficheros.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env['CI'],
  // Sin reintentos: un recorrido que solo pasa a la segunda tiene una condición de carrera que hay que ver.
  retries: 0,
  reporter: process.env['CI'] ? 'github' : 'list',
  // El recorrido espera a proyecciones y entregas asíncronas del outbox: más holgura que el e2e con mocks.
  timeout: 120_000,
  expect: { timeout: 15_000 },
  use: {
    baseURL: process.env['E2E_REAL_BASE_URL'] ?? 'http://localhost:8080',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
