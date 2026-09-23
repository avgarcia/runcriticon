# Frontend — Runcriticon

SPA Angular 22 + spartan.ng + Tailwind CSS v4 del proyecto. Reglas de capa en [`CLAUDE.md`](CLAUDE.md); decisiones en ADR-0012.

## Estado (Hito H0 en curso)

Estructura por features con carga diferida (ADR-0012 D10). Lo que hay:

- `features/identidad/` — login, activación, magic link, reseteo y cambio forzado de contraseña, home.
- `features/club/` — ajustes del club, taxonomía, alumnos, entrenadores, grupos y salud del club.
- `features/planificacion/` — listado y detalle de planes.
- `features/seguimiento/` — semana del alumno y alertas del entrenador.
- `features/marcas/` y `features/cuenta/` — marcas del alumno y su cuenta.
- `core/` (guards, servicios con Signals, interceptores), `shared/` (shell autenticado, formularios, diálogos) y `ui/` (helm de spartan.ng copiados).
- `HttpClient` con CSRF configurado (`X-XSRF-TOKEN`, cruce ADR-0003 D14).
- Cliente HTTP generado desde `../api/openapi.yaml` con `ng-openapi-gen` en `src/app/api/generated/` (ignorado por git, ADR-0001 D10).
- Tests Jest por componente/servicio y E2E Playwright con axe-core en `e2e/`.
- Bundle budgets en `angular.json` (ADR-0012 D22).

## Bootstrap único

```bash
cd frontend
npm install
npm run gen:api                   # genera el cliente OpenAPI; sin él fallan start, build y test
npx playwright install chromium   # navegador para los E2E
```

## Comandos

```bash
npm run gen:api      # regenera el cliente OpenAPI tras cambiar api/openapi.yaml
npm start            # ng serve en :4200 (proxya /api y /actuator al backend :8080)
npm run build        # build de producción → dist/runcriticon/browser
npm test             # Jest (unit + component)
npm run test:watch   # Jest en watch
npm run e2e          # Playwright + axe-core (arranca el dev-server en local)
npm run lint         # ESLint (angular-eslint)
npm run format       # Prettier --write
```

## Desarrollo local con el backend

```bash
# 1. Desde la raíz: BD + MailHog
docker-compose up -d
# 2. Backend en :8080
cd backend && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
# 3. Frontend en :4200 (proxy a :8080)
cd frontend && npm start
```

## Stack (versiones en `package.json`)

| Pieza | Tecnología | ADR |
|---|---|---|
| Framework | Angular 22 (standalone, Signals) | 0001, 0012 |
| Componentes | spartan.ng (`@spartan-ng/brain` + helm copiados en `src/app/ui/`) sobre CDK | 0012 D1/D2 |
| Estilos | Tailwind CSS v4 (tokens en `src/styles.css`) | 0012 D3-D5 |
| Estado | Signals + servicios (sin NgRx) | 0012 D16 |
| Build | esbuild (`@angular/build:application`) | 0012 D11 |
| Unit/component | Jest + jest-preset-angular | 0012 D21 |
| E2E + a11y | Playwright + @axe-core/playwright | 0012 D21, D7 |
| Estilo | ESLint (angular-eslint) + Prettier | 0012 D11 |
| Cliente HTTP | Generado desde OpenAPI (`ng-openapi-gen`) | 0001 D10, 0012 D12 |

## Convenciones

- **Prefijo de selectores**: `rc` (`rc-root`, `rc-home`).
- **Standalone components** por defecto, `OnPush`.
- **Lazy loading por feature** (ADR-0012 D10): cada feature en `src/app/{feature}/` con su `*.routes.ts`.
- **Idioma** (ADR-0008 D4): identificadores de código en inglés (componentes, servicios, propiedades); el vocabulario de negocio del glosario y los textos visibles de la UI, en castellano.
- **`/me/permissions`** para ocultar botones (ayuda de UX, NO barrera — ADR-0009 D18).
- **Sin tokens en JS**: la sesión es cookie httpOnly del backend (ADR-0003 D10).

## Versiones

`package.json` fija Angular 22 (con TypeScript 6 y Node 22 vía `.nvmrc`). Dependabot vigila las actualizaciones (`.github/dependabot.yml`).
