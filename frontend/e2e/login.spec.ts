import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * E2E de la pantalla de login (pantalla crítica, ADR-0012 D6/D7; maqueta identidad-acceso).
 * Sin backend no hay sesión, así que `/` redirige aquí; se verifica el formulario y el check
 * axe AA obligatorio.
 */
test.describe('Login (identidad y acceso)', () => {
  test('muestra el formulario de la maqueta', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Inicia sesión' })).toBeVisible();
    await expect(page.getByLabel('Email')).toBeVisible();
    await expect(page.getByLabel('Contraseña')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Entrar' })).toBeDisabled();
    await expect(page.getByRole('link', { name: 'Entrar con un enlace mágico' })).toBeVisible();
  });

  test('no tiene violaciones de accesibilidad WCAG 2.1 AA', async ({ page }) => {
    await page.goto('/login');
    const resultados = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    expect(resultados.violations).toEqual([]);
  });
});
