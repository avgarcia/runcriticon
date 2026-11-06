// Jest para unit + component tests (ADR-0012 D21 — Jest, no Karma).
// Usa jest-preset-angular. E2E va por separado con Playwright (playwright.config.ts).

module.exports = {
  preset: 'jest-preset-angular',
  setupFilesAfterEnv: ['<rootDir>/setup-jest.ts'],
  // Zona horaria fija (Europe/Madrid): los bugs de fecha dependen de ella y el CI corre en UTC.
  globalSetup: '<rootDir>/jest-global-setup.js',
  // Solo se descubren unit/component tests bajo src/. Los e2e (Playwright) viven en e2e/
  // y se ejecutan con `npm run e2e`. Se usa `roots` en vez de `testPathIgnorePatterns`
  // con `<rootDir>/e2e/`: en Windows `<rootDir>` se expande con backslashes y rompe el
  // regex del ignore, colando los specs de Playwright en jest (no así en el CI Linux).
  roots: ['<rootDir>/src'],
  moduleFileExtensions: ['ts', 'html', 'js', 'json', 'mjs'],
  // Alias de los componentes helm copiados (espejo de tsconfig.json "paths", ADR-0012 D1)
  moduleNameMapper: {
    '^@spartan-ng/helm/(.*)$': '<rootDir>/src/app/ui/$1/src/index.ts',
  },
  collectCoverage: true,
  coverageDirectory: '<rootDir>/coverage',
  coverageReporters: ['text-summary', 'lcov'],
  // Los helm generados (src/app/ui) no cuentan para la cobertura del proyecto
  coveragePathIgnorePatterns: ['/node_modules/', '/src/app/ui/', '/src/app/api/generated/'],
  // Umbral global de ADR-0012 D21 (> 70 %). Medido en 2026-11: sentencias 92,9 %, ramas 78,2 %, funciones
  // 86,4 %, líneas 95,2 %. Ramas y funciones quedan por encima del mínimo del ADR para que una regresión
  // fuerte falle, sin convertir el umbral en una carga por cada rama nueva.
  coverageThreshold: {
    global: { statements: 85, branches: 72, functions: 80, lines: 88 },
  },
};
