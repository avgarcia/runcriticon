// Jest para unit + component tests (ADR-0012 D21 — Jest, no Karma).
// Usa jest-preset-angular. E2E va por separado con Playwright (playwright.config.ts).

module.exports = {
  preset: 'jest-preset-angular',
  setupFilesAfterEnv: ['<rootDir>/setup-jest.ts'],
  testPathIgnorePatterns: ['<rootDir>/node_modules/', '<rootDir>/dist/', '<rootDir>/e2e/'],
  moduleFileExtensions: ['ts', 'html', 'js', 'json', 'mjs'],
  collectCoverage: true,
  coverageDirectory: '<rootDir>/coverage',
  coverageReporters: ['text-summary', 'lcov'],
  // Cobertura > 70% en lógica de presentación (ADR-0012 NFR). Sin umbral ciego en H0;
  // se activa cuando haya componentes reales.
};
