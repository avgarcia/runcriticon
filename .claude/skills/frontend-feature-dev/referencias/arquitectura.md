# Arquitectura del frontend — Runcriticon

Fuente: ADR-0001 D5/D10/D11, ADR-0012 D10/D11/D22

## Estructura de carpetas

```
frontend/src/app/
  core/              # servicios singleton, interceptores, guards, modelos del principal
  shared/            # componentes reutilizables sin lógica de negocio
  features/
    identidad/       # login, activación, perfil
    club/            # gestión de club y taxonomía de grupos
    salud/           # reportes, marcas, vista de salud
    planificacion/   # editor de plan semanal, drag-drop, vista hoy
  layouts/           # shell, navegación, error boundary
```

Cada feature contiene:

```
features/{feature}/
  {feature}.routes.ts         # definición de rutas de la feature
  {feature}.component.ts      # componente página principal
  {feature}.component.html
  {feature}.component.scss
  {feature}.component.spec.ts
  {feature}.service.ts        # lógica y estado de la feature
  pages/                      # subpáginas si la feature es compleja
  components/                 # componentes específicos de la feature (no reutilizables)
```

**Regla**: `core/` solo se importa una vez (en el bootstrap). `shared/` se puede importar en cualquier feature.

## Lazy loading

- Toda feature carga por lazy loading (`loadChildren` o `loadComponent`).
- Sin imports estáticos de features no usados en el arranque.
- El shell (`AppComponent`) no conoce las features — solo las rutas las cargan.

Ejemplo en `app.routes.ts`:

```typescript
{
  path: 'grupos',
  loadChildren: () =>
    import('./features/club/grupos/grupos.routes').then(m => m.GRUPOS_ROUTES),
},
```

## Bundle budget (ADR-0012 D22)

Configurado en `angular.json`:

| Budget | Warning | Error |
|--------|---------|-------|
| **Initial bundle** | 270 KB gzipped | 350 KB gzipped |
| **Per lazy route** | 90 KB gzipped | 120 KB gzipped |
| **AnyComponentStyle** | 4 KB | 6 KB |

- CI falla la build si se supera el límite de error.
- Analizar con `source-map-explorer` cuando se acerque al warning.

## NFRs de rendimiento (ADR-0001, ADR-0012)

| Métrica | Objetivo |
|---------|----------|
| Time to Interactive (TTI) en 3G | < 5 s |
| First Contentful Paint (FCP) | < 1.5 s |
| Largest Contentful Paint (LCP) | < 2.5 s |
| Bundle initial gzipped | < 300 KB |
| Bundle lazy route | < 100 KB |
| Lighthouse Accessibility | > 95 |
| WCAG 2.1 AA en pantallas críticas | 100 % |
| Cobertura tests (lógica presentación) | > 70 % |

## Sin SSR

La app es login-walled (no hay landing pública). Sin SSR, sin GraphQL. La SPA se sirve estáticamente desde Spring Boot bajo el mismo origen (`/` para la SPA, `/api` para el backend).

## Correspondencia con módulos del backend

Las carpetas de features se alinean deliberadamente con los módulos del backend (ADR-0007):

| Feature frontend | Módulo backend |
|-----------------|----------------|
| `identidad/` | identidad |
| `club/` | club |
| `salud/` | salud |
| `planificacion/` | planificacion |
