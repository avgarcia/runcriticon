# Convenciones del frontend — Runcriticon

Fuente: ADR-0001 D4, ADR-0012 D4/D5/D9, `frontend/CLAUDE.md`, `docs/glosario.md`

## Prefijo de selectores

Todos los componentes usan el prefijo `rc`:

```typescript
@Component({ selector: 'rc-grupos', ... })
@Component({ selector: 'rc-plan-semanal', ... })
@Component({ selector: 'rc-vista-hoy', ... })
```

Configurado en `eslint.config.js` como regla obligatoria.

## Lenguaje ubicuo — español

Los nombres de dominio van en **español** en todo el código: componentes, rutas, servicios, señales, propiedades. El glosario autoritativo es `docs/glosario.md`.

Términos clave del dominio (ejemplos representativos):

| Término español | Nunca usar |
|-----------------|-----------|
| `alumno` | student, athlete, runner |
| `entrenador` | coach, trainer |
| `sesion` | session, workout, training |
| `plan` | plan (OK), schedule |
| `tag` | tag (permitido — está en el glosario así) |

Tabla completa de equivalencias → skill `glosario-guardian`. Fuente canónica → `docs/glosario.md`.

Permitidos en inglés: identificadores técnicos (nombres de librerías, métodos HTTP, siglas como `HTTP`, `API`, `WCAG`, `CSRF`).

## Rutas URL

En kebab-case español:

```typescript
'/grupos'
'/plan-semanal'
'/vista-hoy'
'/gestion-alumnos'
```

## TypeScript strict (ADR-0001 D4)

- `strict: true` en `tsconfig.json` — activado globalmente.
- `strictTemplates: true` en `tsconfig.app.json` — type-checking en templates HTML.
- Cero `any` injustificados. Si se necesita, comentar por qué.
- `definite assignment assertion` (`!`) aceptable puntualmente en propiedades inyectadas.

## ESLint (flat config, ESLint 9)

Reglas clave:

```javascript
'@typescript-eslint/no-explicit-any': 'error'
'@angular-eslint/component-selector': ['error', { prefix: 'rc', style: 'kebab-case' }]
'@angular-eslint/use-component-change-detection': 'error' // OnPush obligatorio
```

Ejecutar antes de cada commit: `npm run lint`.

## Prettier

Configuración en `.prettierrc.json`:

```json
{
  "printWidth": 100,
  "singleQuote": true,
  "trailingComma": "all",
  "semi": true
}
```

## Componentes standalone (ADR-0001 D5)

- Siempre `standalone: true` — nunca `NgModule` en código propio.
- Cada componente declara sus propios `imports[]` explícitamente.
- No hay barrel de imports centralizado.

## i18n — $localize (ADR-0012 D9)

- MVP en castellano únicamente.
- Todos los textos visibles marcados con `i18n` attribute o `$localize` template tag.
- No se usa `@ngx-translate` en MVP.

```html
<h1 i18n>Mis grupos</h1>
<p i18n>No hay sesiones registradas esta semana.</p>
```

```typescript
const mensaje = $localize`Bienvenido, ${nombre}`;
```

## Un solo paradigma de estilos (ADR-0012 D5)

- Solo Angular Material + SCSS con ámbito de componente.
- **No añadir Tailwind** ni otra librería utility-first.
- `::ng-deep` prohibido salvo en casos justificados y documentados.
- Utilities propias en `src/styles/_utilities.scss`, con criterio (no clases preventivas).
