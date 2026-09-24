import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright para E2E + accesibilidad (ADR-0012 D21). axe-core se integra en los
 * specs de pantallas críticas (ADR-0012 D7). Solo recorridos críticos, no todo
 * de extremo a extremo.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  // 1 retry automático en CI (ADR-0010 D21) — en local, sin red de seguridad (los devs deben ver el fallo directo).
  retries: process.env['CI'] ? 1 : 0,
  reporter: process.env['CI'] ? 'github' : 'list',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    trace: 'on-first-retry',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  // En local arranca el dev-server automáticamente; en CI se asume ya levantado.
  webServer: process.env['CI']
    ? undefined
    : {
        command: 'npm start',
        url: 'http://localhost:4200',
        reuseExistingServer: true,
        timeout: 120_000,
      },
});
