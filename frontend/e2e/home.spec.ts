import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * E2E de muestra de la pantalla "home" (H0). Demuestra que Playwright + axe-core
 * funcionan end-to-end. Las pantallas críticas reales (login, plan, vista alumno)
 * tendrán su check axe AA obligatorio en Fase 1 (ADR-0012 D6/D7).
 */
test.describe('Home (esqueleto andante)', () => {
  test('muestra el título Runcriticon', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByText('Runcriticon')).toBeVisible();
  });

  test('no tiene violaciones de accesibilidad WCAG 2.1 AA', async ({ page }) => {
    await page.goto('/');
    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    expect(resultados.violations).toEqual([]);
  });

  test('no carga recursos de terceros: fuentes autoalojadas (LAL-58)', async ({ page }) => {
    const peticiones: string[] = [];
    page.on('request', (req) => peticiones.push(req.url()));
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    const hostPropio = new URL(page.url()).hostname;
    const externas = peticiones.filter((u) => new URL(u).hostname !== hostPropio);
    expect(externas).toEqual([]);
  });
});
