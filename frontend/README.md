# Frontend — Runcriticon

SPA Angular 22 + Material 3 del proyecto. Reglas de capa en [`CLAUDE.md`](CLAUDE.md); decisiones en ADR-0012.

## Estado (H0 Bloque 2A)

Esqueleto de build. Compila y produce un `dist/` válido. Lo que hay:

- Shell standalone (`AppComponent`) + pantalla trivial (`HomeComponent`) con Material 3.
- Router con lazy loading (`app.routes.ts`).
- `HttpClient` con CSRF configurado (`X-XSRF-TOKEN`, cruce ADR-0003 D14).
- 1 test Jest (`home.component.spec.ts`) + 1 E2E Playwright con axe-core (`e2e/home.spec.ts`).
- Bundle budgets en `angular.json` (ADR-0012 D22).

Sin las pantallas del camino crítico (Fase 1) ni cliente OpenAPI generado (llega cuando el backend exponga su spec).

## Bootstrap único

```bash
cd frontend
npm install
npx playwright install chromium   # navegador para los E2E
```

## Comandos

```bash
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
| Componentes | Angular Material 3 + CDK | 0012 D1/D2/D3 |
| Estado | Signals + servicios (sin NgRx) | 0012 D16 |
| Build | esbuild (`@angular/build:application`) | 0012 D11 |
| Unit/component | Jest + jest-preset-angular | 0012 D21 |
| E2E + a11y | Playwright + @axe-core/playwright | 0012 D21, D7 |
| Estilo | ESLint (angular-eslint) + Prettier | 0012 D11 |
| Cliente HTTP | Generado desde OpenAPI (Fase 1) | 0012 D12 |

## Convenciones

- **Prefijo de selectores**: `rc` (`rc-root`, `rc-home`).
- **Standalone components** por defecto, `OnPush`.
- **Lazy loading por feature** (ADR-0012 D10): cada feature en `src/app/{feature}/` con su `*.routes.ts`.
- **Lenguaje ubicuo en castellano** (ADR-0008): nombres de componentes, rutas, modelos.
- **`/me/permissions`** para ocultar botones (ayuda de UX, NO barrera — ADR-0009 D18).
- **Sin tokens en JS**: la sesión es cookie httpOnly del backend (ADR-0003 D10).

## Versiones

`package.json` fija Angular 22 (con TypeScript 6 y Node 22 vía `.nvmrc`). Dependabot vigila las actualizaciones (`.github/dependabot.yml`).
