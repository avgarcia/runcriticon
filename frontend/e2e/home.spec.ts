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
});
