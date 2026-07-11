# ADR-0012 — Frontend: librería de componentes y estrategia de UI

- **Estado**: Aceptado
- **Fecha**: 2026-05-22 · revisado 2026-05-30 (reorganización Nivel 1: premisas heredadas, NFRs propios, sub-decisiones numeradas D1-D22 con anchors; incorporación de: **gestión de estado con Angular Signals**, **cliente HTTP generado desde OpenAPI**, **interceptores CSRF/errores/auth**, **estructura por features con lazy loading**, **testing Jest + Playwright + axe-core**, **i18n con `$localize`**, **nivel WCAG 2.1 AA con tests automáticos**, **bundle budget**, **autorización en UI con `/me/permissions`**, **manejo estructurado de errores 4xx**, **theming Material 3 con tokens**, **skeleton screens**) · **aceptado 2026-05-30** · **revisado 2026-07-10** (D1: Angular Material → **spartan.ng**; D3: tokens Material 3 → **variables CSS estilo shadcn**; D4: estilado primario con **utilidades Tailwind en template**; D5: de "sin Tailwind" a **Tailwind v4 como paradigma único** — ver [Opciones consideradas — revisión 2026-07](#opciones-consideradas--revision-2026-07))
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0001 (stack — Angular, cookie first-party), ADR-0003 (CSRF, sesión), ADR-0006 (la app sirve los estáticos, subdominio por club), ADR-0008 (`Result<T, DomainError>` en backend), ADR-0009 (`/me/permissions` para UX), ADR-0010 (CI/CD — lint y pirámide de tests), ADR-0011 (métricas de negocio), ADR-0014 (RGPD — UI con cuidado con datos sensibles)

## Índice de sub-decisiones

Este ADR fija una **decisión arquitectónica compuesta** sobre frontend. Las veintidós sub-decisiones se agrupan en ocho áreas:

- **Librería de componentes (D1-D2)** — spartan.ng (brain + helm) y CDK.
- **Estilos y theming (D3-D5)** — variables CSS estilo shadcn, utilidades Tailwind, un solo paradigma.
- **Accesibilidad e i18n (D6-D9)** — WCAG 2.1 AA, axe-core, teclado, castellano + `$localize`.
- **Estructura y build (D10-D11)** — features con lazy loading, esbuild.
- **API y comunicación (D12-D15)** — cliente generado desde OpenAPI, interceptores CSRF, errores y auth.
- **Estado y autorización (D16-D18)** — Signals + servicios, `/me/permissions`, route guards.
- **Manejo de errores y UX (D19-D20)** — errores 4xx estructurados, skeleton screens.
- **Testing y performance (D21-D22)** — Jest + Playwright + axe-core, bundle budget.

| #   | Sub-decisión                                                                       | Capa         |
|-----|------------------------------------------------------------------------------------|--------------|
| D1  | [spartan.ng (brain + helm) como librería de componentes](#d1)                      | Estratégica  |
| D2  | [Angular CDK como motor de comportamientos (drag-drop)](#d2)                       | Operativa    |
| D3  | [Theming con variables CSS estilo shadcn (tokens de la maqueta)](#d3)              | Operativa    |
| D4  | [Utilidades Tailwind en template; SCSS residual excepcional](#d4)                  | Operativa    |
| D5  | [Un solo paradigma de estilos: Tailwind v4 + spartan](#d5)                         | Estratégica  |
| D6  | [WCAG 2.1 AA en pantallas críticas](#d6)                                           | Estratégica  |
| D7  | [Tests automáticos de accesibilidad con axe-core en E2E](#d7)                      | Operativa    |
| D8  | [Política de teclado: toda funcionalidad accesible sin ratón](#d8)                 | Operativa    |
| D9  | [i18n: castellano único en MVP, preparado con `$localize`](#d9)                    | Estratégica  |
| D10 | [Estructura por features con lazy loading](#d10)                                   | Estratégica  |
| D11 | [Build con esbuild (estándar Angular 17+)](#d11)                                   | Operativa    |
| D12 | [Cliente HTTP generado desde OpenAPI del backend](#d12)                            | Estratégica  |
| D13 | [Interceptor CSRF (cruce ADR-0003 D14)](#d13)                                      | Operativa    |
| D14 | [Interceptor de errores: 4xx → UI, 5xx → toast + reporte](#d14)                    | Operativa    |
| D15 | [Interceptor de autenticación: 401 → login](#d15)                                  | Operativa    |
| D16 | [Estado con Angular Signals + servicios, sin NgRx en MVP](#d16)                    | Estratégica  |
| D17 | [Ocultar botones con `/me/permissions` (cruce ADR-0009 D18)](#d17)                 | Operativa    |
| D18 | [Route guards con principal del backend](#d18)                                     | Operativa    |
| D19 | [Manejo de errores 4xx: body estructurado → UI](#d19)                              | Operativa    |
| D20 | [Skeleton screens y loading states; optimistic UI para reversibles](#d20)          | Operativa    |
| D21 | [Testing: Jest unit, Playwright E2E, axe-core a11y](#d21)                          | Estratégica  |
| D22 | [Bundle budget en `angular.json`](#d22)                                            | Operativa    |

## Contexto y problema

ADR-0001 fija **Angular** como framework del frontend, pero no decide con qué se construye la **interfaz**: ¿una librería de componentes? ¿cuál? ¿qué estrategia de estilos? Y, sobre todo, **las decisiones operativas que el equipo necesita el día 1**: gestión de estado, cómo se consume la API, interceptores, estructura de carpetas, testing, i18n, accesibilidad concreta, bundle budget. Si no se fijan aquí, el primer desarrollador que abra el repo improvisa — y la primera improvisación en frontend dura años.

El MVP tiene pantallas **interactivas** —el constructor de grupos con vista previa en vivo, el editor de plan semanal (con *drag-drop*), la vista "hoy" del alumno— que necesitan componentes ricos: formularios, tablas, diálogos, *datepickers*. Construir todo eso desde cero, o elegir mal, cuesta caro.

## Premisas heredadas (no se revisan en este ADR)

Estas premisas vienen como **input cerrado** del contexto del proyecto. **No se revisan en este ADR**. Si alguna cambia, este ADR deja de ser válido y hay que abrir uno nuevo.

- **Angular como framework** (ADR-0001 D2/D3). El stack está cerrado.
- **Aplicación login-walled, sin landing pública** (ADR-0001). El primer pixel está tras autenticación.
- **App servida desde el backend, mismo dominio, cookie first-party** (ADR-0001 D11, ADR-0006 D15). Sin CORS, cookie automática en cada petición.
- **CSRF activado en backend con `X-XSRF-TOKEN`** (ADR-0003 D14). El frontend lee la cookie y la reenvía.
- **Cookie de sesión `httpOnly`, `SameSite=Lax`, `Secure`** (ADR-0003 D10). El frontend no la lee, solo se envía.
- **NFR de latencia API p95 < 400 ms** (ADR-0001) — el frontend debe percibirse aún más rápido (skeleton screens, optimistic UI).
- **Endpoint `GET /me/permissions`** (ADR-0009 D18) — para UX, **no como barrera**. La regla de oro del 0009 sigue: la UI nunca es la barrera.
- **`Result<T, DomainError>` en backend** (ADR-0008 D11) — los errores 4xx tienen razón estructurada que el frontend traduce a UI.
- **Datos de salud sensibles** (ADR-0014) — UI con cuidado en qué muestra, dónde y cómo copia.
- **Lint + format obligatorios en CI** (ADR-0010 D7) — heredado para el frontend (ESLint + Prettier).
- **Pirámide de tests definida** (ADR-0010 D8) — cruzar herramientas concretas para el frontend (D21).
- **Subdominio por club al multi-club** (ADR-0006 D16) — el frontend lee `host` para derivar `club_id` cuando proceda.
- **Equipo de 4 personas** — premisa de coste de tiempo: evitar la ceremonia que no paga.

## Requisitos no funcionales

| Dimensión | Valor objetivo |
|---|---|
| **Time to Interactive (TTI)** en 3G | **< 5 s** |
| **First Contentful Paint (FCP)** | **< 1.5 s** |
| **Largest Contentful Paint (LCP)** | **< 2.5 s** |
| **Bundle size budget (initial)** | **< 300 KB** gzipped |
| **Bundle size budget (per lazy route)** | **< 100 KB** gzipped |
| **Lighthouse Accessibility score** | **> 95** |
| **WCAG 2.1 AA compliance** | **100 %** en pantallas críticas |
| **Cobertura de tests** frontend (lógica de presentación) | **> 70 %** |

## Drivers de la decisión

- Equipo interno de 4 personas → **evitar construir componentes de UI desde cero**.
- Pantallas interactivas que necesitan componentes ricos y *drag-drop*.
- **Accesibilidad**: el discovery puso cuidado en la UX y la app maneja datos de salud — la UI debe ser accesible.
- Coherencia con la lógica de ADR-0001 (se eligió Angular por ser "oficial y con baterías incluidas").
- Velocidad de MVP.
- **El equipo no debe improvisar el día 1**: estado, API, estructura, testing, i18n y a11y quedan fijados aquí.

## Opciones consideradas — librería de componentes

- **Opción A** — Angular Material.
- **Opción B** — PrimeNG u otra librería de terceros.
- **Opción C** — Componentes propios sobre Angular CDK.

### Opción A — Angular Material

Librería **oficial del equipo de Angular**, implementa Material Design.

- 👍 Oficial — la misma lógica de "baterías incluidas y afinidad" que llevó a Angular en ADR-0001.
- 👍 **Accesibilidad de serie** vía Angular CDK.
- 👍 Conjunto completo: formularios, tablas, diálogos, *datepickers*; el CDK aporta el **drag-drop** del editor semanal.
- 👍 Gran comunidad y mantenimiento.
- 👎 Estética Material reconocible — personalizable con tokens M3, pero "se nota".

### Opción B — PrimeNG u otra librería de terceros

- 👍 Catálogo enorme de componentes, *widgets* "enterprise".
- 👎 De terceros, con calidad desigual entre componentes; sin la afinidad oficial con Angular.
- 👎 Accesibilidad inconsistente por componente.

### Opción C — Componentes propios sobre Angular CDK

El CDK da primitivas de comportamiento sin estilo; el equipo construye los componentes estilados encima.

- 👍 Control total del aspecto; sin dependencia de una librería de componentes.
- 👎 Se construye **todo** —diálogos accesibles, *datepickers*, desplegables, tablas—; muchísimo trabajo, la ceremonia que no paga en un MVP con equipo de 4.

<a id="opciones-consideradas--revision-2026-07"></a>
## Opciones consideradas — revisión 2026-07

La decisión original (mayo 2026, Opción A: Angular Material) se tomó **sin UI kit**: el propio ADR dejaba el diseño visual como "tarea de diseño aparte" y asumía como riesgo la "UI genérica de Material". En julio de 2026 existe **maqueta hi-fi interactiva** de Identidad y acceso ([`docs/diseno/identidad-acceso.html`](../diseno/identidad-acceso.html)) con una estética propia (navy `#1a3e72`, neutrales slate, tipografía de sistema, radios 8 px) que activa la revisión:

- **Opción A' — Seguir con Material + theming M3 intensivo**. 👍 Cero coste de migración. 👎 La maqueta no es Material: replicarla exige luchar contra los tokens M3 (densidades, elevaciones, ripples, tipografía Roboto) componente a componente; el "se nota" que el ADR original ya admitía.
- **Opción B' — spartan.ng (brain + helm) + Tailwind v4** *(elegida)*. 👍 Los componentes helm se **copian al repo** (modelo shadcn): la maqueta se implementa editando el componente, no sobrescribiendo una librería. 👍 La accesibilidad vive en `@spartan-ng/brain`, headless sobre el **Angular CDK** — D2 y la política de a11y (D6-D8) se conservan intactas. 👍 Peer deps verificadas: Angular `>=21 <23` (compatible con Angular 22 del repo). 👎 Exige **Tailwind CSS v4** (contradice la D5 original — por eso esta revisión). 👎 Los helm copiados son mantenimiento propio.
- **Opción C' — PrimeNG**. 👍 Catálogo enorme. 👎 Mismo problema que A' (theming contra la maqueta) más los contras originales (calidad desigual, a11y inconsistente).

La nota original de D5 ("reabrir requiere un nuevo ADR") se satisface con esta revisión formal: es la vía prevista, con más contexto (maqueta hi-fi) del que había al decidir.

## Decisión

**Original (2026-05): Opción A, Angular Material** con theming Material 3 y SCSS con ámbito. **Revisión 2026-07: Opción B', spartan.ng + Tailwind v4** — se reescriben D1 y D3-D5; las otras dieciocho sub-decisiones no cambian. Ocho son **estratégicas** (D1, D5, D6, D9, D10, D12, D16, D21 — librería, paradigma de estilos, accesibilidad, i18n, estructura, API, estado, testing); el resto son **operativas** y derivan o implementan las anteriores.

<a id="d1"></a>
### D1 — spartan.ng (brain + helm) como librería de componentes

*(Revisada 2026-07-10; original: Angular Material.)*

**spartan.ng** con su arquitectura de dos capas:

- **`@spartan-ng/brain`** — paquete npm con las primitivas **headless y accesibles** (diálogos, focus-trap, ARIA), construidas sobre el Angular CDK (D2). Es la capa que se mantiene como dependencia y aporta la accesibilidad de fábrica que antes daba Material.
- **Componentes "helm"** — la capa visual se **copia al repo** en `frontend/src/app/ui/` (modelo shadcn) vía `ng g @spartan-ng/cli:ui`. Son código propio: se editan para clavar la maqueta (alturas, focus ring, radios) sin pelear con una librería.

Reglas operativas:

- Los helm copiados se tratan como código del repo (lint con override de selector, revisión en PR), pero **no se les añade lógica de negocio**: son presentación.
- Actualizar un helm a una versión nueva de spartan es re-generar y hacer diff, no `npm update` — decisión consciente de ownership sobre conveniencia.
- Si un componente no existe en spartan, se construye a medida sobre brain/CDK (misma válvula que tenía la decisión original).

<a id="d2"></a>
### D2 — Angular CDK como motor de comportamientos (drag-drop)

El Angular CDK (Component Dev Kit) aporta primitivas de comportamiento accesibles:

- **`@angular/cdk/drag-drop`** para el editor de plan semanal.
- **`@angular/cdk/overlay`** para diálogos y desplegables.
- **`@angular/cdk/a11y`** para focus management y keyboard navigation.
- **`@angular/cdk/table`** para tablas accesibles.

El CDK es además la base sobre la que `@spartan-ng/brain` (D1) implementa sus primitivas — esta sub-decisión sale reforzada de la revisión 2026-07. Si un componente muy específico no encaja en spartan, se construye a medida sobre el CDK sin cambiar esta decisión.

<a id="d3"></a>
### D3 — Theming con variables CSS estilo shadcn (tokens de la maqueta)

*(Revisada 2026-07-10; original: tokens Material 3 en `_theme.scss`.)*

El theming son **variables CSS** (convención shadcn que spartan consume) declaradas en `frontend/src/styles.css`, con los valores de la maqueta hi-fi ([`docs/diseno/identidad-acceso.html`](../diseno/identidad-acceso.html)):

- **Marca**: `--primary: #1a3e72` (navy); derivados con `color-mix` (`--primary-hover` 85 % negro, `--primary-soft` 10 % blanco).
- **Neutrales slate**: `--foreground: #0f172a`, `--muted-foreground: #64748b`, `--muted: #f1f5f9`, `--border: #e2e8f0`, `--background: #f8fafc`.
- **Semánticos**: `--destructive: #b91c1c`, `--ring: var(--primary)`, `--radius: 0.5rem`; alertas de éxito/error como colores propios en `@theme` de Tailwind.
- **Tipografía**: fuente de **sistema** (`ui-sans-serif, system-ui, 'Segoe UI', Roboto`) — sin webfont; refuerza el NFR de FCP y la política de cero peticiones a terceros (LAL-58).

Un rebranding (p. ej. subdominio por club, ADR-0006 D16) es cambio de valores de variables, no de estructura — se conserva la propiedad que tenía el theming M3.

<a id="d4"></a>
### D4 — Utilidades Tailwind en template; SCSS residual excepcional

*(Revisada 2026-07-10; original: SCSS con ámbito de componente.)*

- **El estilado primario son utilidades Tailwind en el template** — es el modelo de spartan/shadcn y elimina la mayoría de `.scss` por componente.
- Un componente solo tiene hoja de estilos propia cuando una utilidad **no llega** (animaciones complejas, selectores de estado imposibles en template); sigue aislada con `ViewEncapsulation.Emulated` y se justifica en el PR.
- Estilos globales **solo** en `src/styles.css`: `@import "tailwindcss"`, tokens (D3) y base mínima.
- **`::ng-deep` prohibido** salvo en casos justificados y documentados (penetra el ámbito, fuente de bugs).

<a id="d5"></a>
### D5 — Un solo paradigma de estilos: Tailwind v4 + spartan

*(Revisada 2026-07-10; original: "sin Tailwind ni utility-first" junto a Material. La nota original "reabrir requiere un nuevo ADR" se ejecuta con esta revisión formal — ver [Opciones consideradas — revisión 2026-07](#opciones-consideradas--revision-2026-07).)*

El principio de la sub-decisión original **se mantiene**: un solo paradigma de estilos, sin mezclas. Lo que cambia es cuál: al elegir spartan (D1), **Tailwind v4 pasa a ser el paradigma único** (spartan lo exige como peer dependency y los helm están escritos con sus utilidades).

- **Prohibido** añadir una segunda librería de componentes (Material, PrimeNG, …) o de utilidades junto a Tailwind — misma lógica preventiva que la D5 original, con los papeles invertidos.
- **Prohibido** reintroducir `@angular/material`; el CDK sí permanece (D2, base de brain).
- Tailwind v4 se integra vía `@tailwindcss/postcss` (sin `tailwind.config.js`: configuración CSS-first con `@theme` en `src/styles.css`).
- Cuando haga falta una utilidad propia, se define como token/utility en `@theme` — no se crean clases preventivas.

Decisión preventiva: cuando alguien "quiera añadir una cosita de Material porque ya la conoce", esta sub-decisión es lo único que lo frena antes de volver a los dos paradigmas.

<a id="d6"></a>
### D6 — WCAG 2.1 AA en pantallas críticas

**Pantallas críticas** del MVP:

- Login y activación.
- Dashboard del alumno (vista "hoy").
- Editor de plan semanal (entrenador).
- Reportar sesión (alumno).
- Vista de salud del club (admin/entrenador).
- Gestión de alumnos (entrenador).

Estas pantallas cumplen **WCAG 2.1 nivel AA**. El resto del MVP busca cumplir AA pero la verificación obligatoria en CI (D7) cubre las críticas.

**WCAG 2.2** ya está publicado pero adopción aún incipiente — se revisa en la revisión periódica (Notas).

<a id="d7"></a>
### D7 — Tests automáticos de accesibilidad con axe-core en E2E

- **axe-core** integrado en los tests E2E de Playwright (`@axe-core/playwright`).
- Cada test E2E de las pantallas críticas (D6) ejecuta `axeCheck()` y **falla** si encuentra violaciones AA.
- El CI bloquea PRs con fallos de axe.
- Para componentes reutilizables, test unitario con `axe-core` también.

Sin tests automáticos, la accesibilidad "de fábrica" se erosiona PR a PR.

<a id="d8"></a>
### D8 — Política de teclado: toda funcionalidad accesible sin ratón

- **Toda funcionalidad** del MVP debe ser accesible **solo con teclado** (Tab, Enter, Space, flechas, Esc).
- Las primitivas de `@spartan-ng/brain` lo cumplen de serie (sobre CDK); los componentes propios siguen los patrones del CDK `@angular/cdk/a11y`.
- **Focus management** explícito en diálogos y rutas (al abrir un diálogo, el foco va al primer control; al cerrarlo, vuelve al disparador).
- **Skip links** en el shell para saltar al contenido principal.
- Tests E2E de teclado en las pantallas críticas: navegar todo con Tab y verificar que se puede completar cada flujo.

<a id="d9"></a>
### D9 — i18n: castellano único en MVP, preparado con `$localize`

- **MVP**: castellano único. El cliente piloto es España.
- **Código preparado para extracción**: todos los textos visibles marcados con `i18n` attribute o `$localize` template tag (estándar Angular i18n).
- **No se introduce `@ngx-translate`** en MVP: `$localize` es estándar y suficiente.
- **Mensajes de validación**: también marcados con `i18n`.
- **Disparador para multi-idioma**: entra cliente con otro idioma → traducciones (no refactor de cada componente).

Sin esta preparación, el primer texto fija el patrón mal y la migración a multi-idioma es refactor masivo.

<a id="d10"></a>
### D10 — Estructura por features con lazy loading

Estructura de carpetas:

```
src/app/
  core/        # servicios singletons, interceptores, guards, modelos del principal
  shared/      # componentes reutilizables sin lógica de negocio
  features/
    identidad/    # login, activación, perfil
    club/         # gestión de club y taxonomía
    salud/        # reportes, marcas, vista de salud
    planificacion/ # editor de plan semanal, drag-drop
  layouts/     # shell, navegación, error boundary
```

- **Cada feature es un módulo Angular con su routing** y se carga via **lazy loading** (`loadChildren`).
- **Carpetas alineadas a los módulos del backend** (ADR-0007 D2): identidad, club, salud, planificación, auditoría.
- **`core` solo se importa una vez** (en `AppModule`); `shared` se puede importar en features.
- **Cada feature tiene**:
  - `*.routes.ts` con sus rutas.
  - `pages/` con componentes página.
  - `components/` con componentes específicos de la feature.
  - `services/` con la lógica de la feature.
  - `models/` con los DTOs del backend (generados, D12).

<a id="d11"></a>
### D11 — Build con esbuild (estándar Angular 17+)

- Angular 17+ usa **esbuild** por defecto + **Vite dev server**.
- No se configura webpack ni `ng-packagr` salvo necesidad probada.
- Builds rápidas (segundos), dev server con HMR.
- Configuración estándar en `angular.json`.

<a id="d12"></a>
### D12 — Cliente HTTP generado desde OpenAPI del backend

- El backend Spring Boot expone su API como **OpenAPI 3** via `springdoc-openapi` (configuración estándar).
- El frontend **genera el cliente TypeScript** con **`ng-openapi-gen`** en una task de **npm run gen:api**. (Nota: el texto original citaba `openapi-generator-cli` / `typescript-angular`; la elección real es `ng-openapi-gen`, alineada con ADR-0001 D10, que lo prefiere por mejor manejo de tipos polimórficos. ADR-0001 D10 tiene precedencia.)
- **Tipos de DTOs sincronizados** con el backend automáticamente: cambiar un campo en el backend rompe la build del frontend si no se ajusta el llamante.
- Los clientes generados van a `src/app/api/generated/` y **no se editan a mano ni se commitean** (están en `.gitignore`).
- En CI: `npm run gen:api` antes del lint y el build para detectar drift.

**Pendientes técnicos** (no del ADR, de implementación):

- El backend debe versionar su OpenAPI; un cambio breaking de contrato es PR coordinada backend+frontend.
- Tipos `Result<T, DomainError>` se exportan como `T | DomainError`; el interceptor (D14) los discrimina.

<a id="d13"></a>
### D13 — Interceptor CSRF (cruce ADR-0003 D14)

- Angular se configura con `provideHttpClient(withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }))`.
- Angular lee automáticamente la cookie `XSRF-TOKEN` (sin `httpOnly`) y la reenvía como header `X-XSRF-TOKEN` en peticiones modificadoras (POST, PUT, DELETE, PATCH).
- Cruce directo con la configuración del backend (ADR-0003 D14).
- **Sin interceptor manual**: la configuración nativa cubre el caso.

<a id="d14"></a>
### D14 — Interceptor de errores: 4xx → UI, 5xx → toast + reporte

Interceptor HTTP global que clasifica errores:

| Status | Comportamiento UI | Notas |
|--------|--------------------|-------|
| **400** (validación) | El servicio que llamó traduce `code` → mensaje + marca campo (D19) | No interceptor: el caller maneja |
| **401** (no autenticado) | Redirige a `/login` con `returnUrl` (D15) | |
| **403** (autorizado pero prohibido) | Toast con mensaje neutro: "No tienes permiso para esta acción" | Coherente con ADR-0009 (cuerpo neutro al cliente) |
| **404** | El componente lo maneja (lista vacía, item no encontrado) | No interceptor genérico |
| **409** (conflicto) | El caller traduce a UI (D19) | |
| **429** (rate limit) | Toast: "Demasiados intentos. Espera unos segundos." | Cruce ADR-0003 D12 |
| **5xx** | Toast genérico: "Algo ha ido mal. Vuelve a intentarlo." + reporte a observabilidad | Cruce ADR-0011 D14 |
| **Network / timeout** | Toast: "Sin conexión" | |

El reporte a observabilidad envía `trace_id` (del header de respuesta, cruce ADR-0011 D4) + URL + status para correlación.

<a id="d15"></a>
### D15 — Interceptor de autenticación: 401 → login

- Cualquier respuesta 401 redirige a `/login?returnUrl={ruta-actual}`.
- Tras login exitoso, vuelve a la `returnUrl`.
- La cookie de sesión se envía automáticamente (mismo origen).
- **Sin token bearer en headers**: la sesión vive en la cookie httpOnly del backend (ADR-0003 D10).

<a id="d16"></a>
### D16 — Estado con Angular Signals + servicios, sin NgRx en MVP

- **Estado local del componente**: Signals (`signal()`, `computed()`).
- **Estado compartido entre componentes**: servicios singletons con Signals dentro.
- **Estado del servidor**: en servicios que envuelven el cliente API generado (D12).
- **Sin NgRx** en MVP. Es ceremonia alta que un equipo de 4 no necesita para el volumen del piloto.

**Disparador para NgRx** (por feature, nunca global): aparece una feature con estado complejo y múltiples vistas que comparten, donde la composición de Signals + servicios se vuelve ilegible. Se introduce NgRx **en esa feature** sin tocar las demás.

<a id="d17"></a>
### D17 — Ocultar botones con `/me/permissions` (cruce ADR-0009 D18)

- Al iniciar sesión, el frontend pide **`GET /me/permissions`** y cachea el resultado en un servicio singleton.
- **Directiva `*hasPermission`** para ocultar elementos a los que el usuario no tiene acceso:

  ```html
  <button *hasPermission="'plan.editar'" hlmBtn>Editar plan</button>
  ```

- La directiva lee del cache del servicio singleton.
- **Recordatorio operativo**: la UI **no es la barrera**. El backend autoriza cada petición (regla de oro ADR-0009). Si la UI olvida ocultar un botón, no es vulnerabilidad — el backend devuelve 403.

<a id="d18"></a>
### D18 — Route guards con principal del backend

- **`authGuard`** (`CanActivate`): redirige a `/login` si no hay sesión activa (heurística: presencia de cookie de sesión + llamada a `/me`).
- **`permissionGuard(permission)`** (`CanActivate`): comprueba la cache de `/me/permissions` (D17).
- Sin permission guard, el componente carga pero la primera petición HTTP devuelve 403 (D14).
- **El backend sigue siendo la autoridad**: los guards son UX para evitar pantallas inútiles, no seguridad.

<a id="d19"></a>
### D19 — Manejo de errores 4xx: body estructurado → UI

Backend devuelve errores 4xx con body estructurado (cruce ADR-0008 D11):

```json
{
  "code": "EMAIL_INVALIDO",
  "field": "email",
  "message": "Email no válido",
  "details": {}
}
```

Frontend:

- Servicio que llama traduce **`code`** a mensaje **localizado** (D9).
- Si hay **`field`**: marca el control del formulario con error y muestra el mensaje bajo el campo.
- Si no hay `field`: muestra error general arriba del formulario.
- **No se muestra `message` del backend directamente** al usuario: el frontend lo traduce. Razón: la localización pertenece al frontend; el backend devuelve códigos estables, no textos.

Catálogo de códigos vive en `src/app/core/api/error-codes.ts` con sus traducciones — sincronizado con el backend en revisión PR.

<a id="d20"></a>
### D20 — Skeleton screens y loading states; optimistic UI para reversibles

- **Carga inicial de página**: **skeleton screens** (placeholders con la forma del contenido). Mejor percepción que spinner.
- **Acciones puntuales** (botón submit): spinner inline en el botón.
- **Optimistic UI** para acciones reversibles (marcar como leído, toggle de switch): el cambio se aplica inmediatamente en UI; si la petición falla, se revierte con un toast de error.
- **Error boundary** global en el layout: si una feature falla catastróficamente, se muestra una pantalla de error con botón "Reintentar" y `trace_id` para soporte.

<a id="d21"></a>
### D21 — Testing: Jest unit, Playwright E2E, axe-core a11y

| Tipo | Herramienta | Alcance |
|------|-------------|---------|
| **Unit** | **Jest** (Karma deprecated en Angular 17+) | Lógica de servicios, pipes, utilidades, validadores |
| **Component** | **Jest** + Angular Testing Library | Componentes con interacción del usuario |
| **E2E** | **Playwright** | Flujos completos (login → editar plan → guardar) |
| **A11y** | **axe-core** (via `@axe-core/playwright`) | Integrado en E2E de pantallas críticas (D7) |

- **No se usa Cypress** (sin paralelización nativa en plan free; Playwright es superior para Angular).
- **CI** ejecuta todos los tipos (cruce ADR-0010 D8 catálogo de tests).
- **Coverage > 70 %** en lógica de presentación (NFR).

<a id="d22"></a>
### D22 — Bundle budget en `angular.json`

Dos budgets en `angular.json` (configuración `production`), sobre **tamaño en crudo** (Angular CLI no mide gzip para budgets, solo para el "estimated transfer size" informativo):

- **Initial bundle**: warning a 350 KB, error a 550 KB.
- **AnyComponentStyle**: 4 KB (warning), 8 KB (error).
- CI falla la build si supera el budget de error.
- Análisis con `source-map-explorer` en revisión PR cuando se acerca al warning.

Sin budget, el bundle crece silencioso PR a PR — se descubre tarde y duele.

**Sin budget per-lazy-route**: se evaluó (2026-07) un tercer budget `type: "any"` para acotar cada chunk lazy por separado, pero Angular CLI lo aplica a *todo* fichero de salida — incluidos `main.js` y los chunks de vendor/spartan.ng compartidos entre rutas (varios cientos de KB) — sin forma de excluirlos. Un umbral que no rompiera esos chunks compartidos sería demasiado laxo para detectar bloat real en una ruta concreta, así que no se implementa; acotar bloat por ruta requeriría tooling adicional (bundle analyzer en CI), fuera de alcance de H0.

**Nota (2026-07)**: el bundle inicial real ya supera el warning (~509 KB) por el volumen de spartan.ng/vendor en los chunks compartidos; es deuda conocida, no una regresión de esta sub-decisión — reducirlo (code-splitting más fino, tree-shaking de spartan.ng) queda pendiente de priorizar.

## Lo que este ADR no decide

- **Diseño visual concreto por pantalla**: lo fijan las maquetas hi-fi de [`docs/diseno/`](../diseno/) a medida que existen (Identidad y acceso ya tiene la suya); este ADR solo fija los tokens (D3). Las pantallas sin maqueta aplican los mismos tokens.
- **Iconografía concreta**: en Identidad bastan glifos inline (✓, ○, ✉); si una feature necesita colección de iconos (p. ej. `ng-icons`), se decide en su implementación.
- **Animaciones**: las que traen los helm (`tw-animate-css`) + transiciones CSS; criterios concretos por maqueta.

## Consecuencias

### Positivas

- El equipo no construye componentes de UI desde cero — más velocidad de MVP.
- **Accesibilidad de serie con tests automáticos** (D6-D8): no se erosiona PR a PR.
- Componentes ricos y *drag-drop* disponibles para las pantallas interactivas.
- Un solo sistema de estilos (D5): consistencia preservada.
- **Estructura por features con lazy loading** (D10): bundle inicial pequeño, navegación rápida.
- **Cliente HTTP generado** (D12): types sincronizados con backend, drift detectado en CI.
- **Estado con Signals** (D16): API moderna, ceremonia mínima.
- **Errores 4xx traducidos en UI** (D19): localización en frontend, códigos estables en backend.
- **Bundle budget** (D22): performance vigilada.
- **i18n preparado** (D9): multi-idioma es trabajo de traducción, no refactor.
- La capa de comportamiento (CDK, base de brain) sigue siendo la oficial de Angular — la afinidad de ADR-0001 se conserva donde importa (a11y, overlays).
- **Ownership visual** (revisión 2026-07): la maqueta se implementa editando código propio (helm), sin luchar contra el theming de una librería.

### Negativas / coste asumido

- **Los componentes helm copiados son mantenimiento propio** (revisión 2026-07): actualizar spartan es re-generar y hacer diff, no `npm update`. Aceptado a cambio del ownership visual.
- spartan.ng no es la librería oficial de Angular ni tiene su masa de comunidad — mitigado porque la capa de a11y (brain) se apoya en el CDK oficial y la capa visual es código propio del repo.
- **El chunk de zxcvbn** (medidor de fortaleza de contraseña, cruce ADR-0003 D6) pesa ~200 KB gzipped con los diccionarios en español: excede conscientemente el NFR de 100 KB por ruta lazy. Se carga con `import()` dinámico al primer uso (no bloquea TTI ni entra en el bundle inicial) y se comparte entre las tres pantallas que lo usan.
- **Build de OpenAPI client** añade un paso al pipeline (npm run gen:api).
- **Tests E2E con Playwright** son más lentos que unit; se ejecutan en paralelo para mitigar.
- **Sin NgRx en MVP** implica que cuando una feature crezca en complejidad, el equipo tendrá que evaluar la introducción (D16 disparador).

### Riesgos y mitigaciones

- **Drift entre los helm copiados y spartan upstream** → los helm son código propio; solo se re-sincronizan si un cambio upstream aporta (fix de a11y) — se revisa al actualizar `@spartan-ng/brain`.
- **Accesibilidad erosionada en PRs** → tests automáticos con axe-core en CI (D7) bloquean violaciones AA.
- **Drift entre cliente API y backend** → generación automática en CI (D12) detecta cambios de contrato.
- **Bundle creciendo silencioso** → budget en `angular.json` (D22) + análisis periódico.
- **Errores 4xx mostrando texto del backend al usuario** → política explícita de D19: traducción en frontend.
- **Permisos en UI usados como barrera** → recordatorio operativo (D17): el backend es la autoridad.
- **Sobre-uso de componentes pesados** → usar el componente adecuado a cada caso; no forzar *widgets* complejos donde basta uno simple.

## Notas

- Las premisas heredadas son **invariantes de este ADR**: si cambian (especialmente ADR-0001, ADR-0003 D14, ADR-0009 D18), este ADR se revisita.
- El **UI kit y el diseño visual** se abordan como tarea de diseño separada (plan de discovery).
- Si en el futuro se necesita un componente muy específico que spartan no cubre, se construye a medida sobre brain/CDK — sin cambiar esta decisión.
- **Disparadores para evolución**:
  - **NgRx por feature** cuando una feature crezca en complejidad y Signals + servicios se vuelva ilegible (D16).
  - **`@ngx-translate`** si la carga dinámica de traducciones se hace necesaria (multi-tenant con idiomas distintos por club) — D9.
  - ~~**Tailwind o sistema propio de utilidades** queda descartado por D5; reabrir requiere un nuevo ADR.~~ *Ejecutado en la revisión 2026-07-10: la D5 revisada adopta Tailwind v4 como paradigma único.*
  - **WCAG 2.2** en próxima revisión cuando la adopción se generalice (D6).
- **Revisión periódica**: este ADR se revisa al **lanzamiento del piloto** (validación de NFRs reales) y luego cada **6 meses** o cuando un disparador específico se active.
- **Revisión del 2026-07-10 (Material → spartan.ng)**: con la primera maqueta hi-fi (Identidad y acceso, [`docs/diseno/identidad-acceso.html`](../diseno/identidad-acceso.html)) se constata que la estética propia del producto no es Material. Se reescriben **D1** (spartan.ng: `@spartan-ng/brain` como paquete + helm copiados a `src/app/ui/`), **D3** (variables CSS estilo shadcn con los tokens de la maqueta), **D4** (utilidades Tailwind en template) y **D5** (Tailwind v4 como paradigma único). D2 (CDK) sale reforzada; D6-D22 no cambian. La migración del código existente (identidad, coaches, alumnos, diálogos, toasts) y la desinstalación de `@angular/material` van en PRs encadenadas a esta revisión.
- **Reorganización del 2026-05-30 (Nivel 1)**: el ADR se reestructura con índice de sub-decisiones (párrafo introductorio + tabla), premisas heredadas, NFRs explícitos, numeración D1-D22 con anchors. Decisiones nuevas o explicitadas: theming Material 3 con tokens (D3), un solo paradigma de estilos preventivo (D5), WCAG 2.1 AA en pantallas críticas (D6), tests automáticos axe-core (D7), política de teclado (D8), i18n con `$localize` (D9), estructura por features con lazy loading (D10), cliente HTTP generado desde OpenAPI (D12), interceptores CSRF/errores/auth (D13-D15), estado con Signals + servicios sin NgRx (D16), autorización en UI con `/me/permissions` (D17-D18), manejo de errores 4xx estructurados (D19), skeleton screens y optimistic UI (D20), testing Jest + Playwright + axe-core (D21), bundle budget (D22).
