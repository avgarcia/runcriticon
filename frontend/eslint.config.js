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
    // ui/** son los componentes helm de spartan.ng copiados (ver bloque de abajo): código
    // upstream que no marcamos para minimizar el drift al re-sincronizar. index.html es el shell
    // raíz del SPA (favicon, meta viewport...), no una plantilla de componente.
    ignores: ['src/app/ui/**', 'src/index.html'],
    extends: [
      ...angular.configs.templateRecommended,
      ...angular.configs.templateAccessibility,
    ],
    rules: {
      // Textos visibles preparados para extracción i18n (ADR-0012 D9). `processInlineTemplates`
      // (arriba) hace que también corra sobre los templates inline de los .ts.
      '@angular-eslint/template/i18n': [
        'error',
        {
          checkText: true,
          // checkId false: Angular genera un ID por hash del contenido si no se da uno propio a
          // mano (`i18n="@@id"`) - exigir IDs manuales en cada mensaje es más carga de la que
          // aporta valor en el MVP.
          checkId: false,
          checkAttributes: true,
          // Además del set por defecto del plugin (autocomplete, class, href, id, role, type...):
          // props de la API de los componentes helm de spartan.ng (no son texto de usuario) y
          // atributos ARIA/HTML nativos de valor fijo, no traducible.
          ignoreAttributes: [
            'autocomplete',
            'class',
            'for',
            'formControlName',
            'id',
            'role',
            'type',
            'variant',
            'size',
            'form',
            'data-slot',
            'aria-live',
            'aria-hidden',
            'content',
            'rel',
          ],
        },
      ],
    },
  },
  // Componentes helm de spartan copiados al repo (ADR-0012 D1, revisión 2026-07): código
  // generado con prefijo hlm y convenciones upstream propias — se relajan las reglas de
  // estilo del repo para minimizar el drift con spartan al re-sincronizar.
  {
    files: ['src/app/ui/**/*.ts'],
    rules: {
      '@angular-eslint/component-selector': 'off',
      '@angular-eslint/directive-selector': 'off',
      '@angular-eslint/no-input-rename': 'off',
      '@angular-eslint/no-output-rename': 'off',
      '@typescript-eslint/consistent-type-definitions': 'off',
    },
  },
);
