// Jest para unit + component tests (ADR-0012 D21 — Jest, no Karma).
// Usa jest-preset-angular. E2E va por separado con Playwright (playwright.config.ts).

module.exports = {
  preset: 'jest-preset-angular',
  setupFilesAfterEnv: ['<rootDir>/setup-jest.ts'],
  // Solo se descubren unit/component tests bajo src/. Los e2e (Playwright) viven en e2e/
  // y se ejecutan con `npm run e2e`. Se usa `roots` en vez de `testPathIgnorePatterns`
  // con `<rootDir>/e2e/`: en Windows `<rootDir>` se expande con backslashes y rompe el
  // regex del ignore, colando los specs de Playwright en jest (no así en el CI Linux).
  roots: ['<rootDir>/src'],
  moduleFileExtensions: ['ts', 'html', 'js', 'json', 'mjs'],
  collectCoverage: true,
  coverageDirectory: '<rootDir>/coverage',
  coverageReporters: ['text-summary', 'lcov'],
  // Cobertura > 70% en lógica de presentación (ADR-0012 NFR). Sin umbral ciego en H0;
  // se activa cuando haya componentes reales.
};
