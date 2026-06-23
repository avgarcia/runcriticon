// ESLint flat config para Angular (ADR-0012 D11). angular-eslint + typescript-eslint.
// @ts-check
const eslint = require('@eslint/js');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

module.exports = tseslint.config(
  // Archivos generados por ng-openapi-gen — no analizar (ADR-0001 D10)
  { ignores: ['src/app/api/generated/**'] },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...tseslint.configs.stylistic,
      ...angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'rc', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'rc', style: 'kebab-case' },
      ],
      // Cero any injustificado (frontend/CLAUDE.md)
      '@typescript-eslint/no-explicit-any': 'error',
    },
  },
  {
    files: ['**/*.html'],
    extends: [
      ...angular.configs.templateRecommended,
      ...angular.configs.templateAccessibility,
    ],
    rules: {},
  },
);
