import axe from 'axe-core';

/**
 * Ejecuta axe-core sobre un elemento ya renderizado y falla con la lista de violaciones legible (ADR-0012 D7: los
 * componentes reutilizables llevan también un test unitario con axe-core, además del e2e de las pantallas críticas).
 *
 * jsdom no calcula estilos ni layout, así que se desactiva la regla de contraste de color: la cubren los e2e de
 * Playwright con `@axe-core/playwright`, que sí corren en un navegador real.
 */
export async function expectNoA11yViolations(element: HTMLElement): Promise<void> {
  // axe necesita el nodo dentro del documento para resolver ids, `for` y roles.
  const attached = element.isConnected;
  if (!attached) document.body.appendChild(element);
  try {
    const results = await axe.run(element, { rules: { 'color-contrast': { enabled: false } } });
    const report = results.violations
      .map(
        (v) =>
          `${v.id} (${v.impact}): ${v.help}\n` + v.nodes.map((n) => `  - ${n.html}`).join('\n'),
      )
      .join('\n');
    expect(report).toBe('');
  } finally {
    if (!attached) element.remove();
  }
}
