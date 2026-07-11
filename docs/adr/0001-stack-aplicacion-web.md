# ADR-0001 — Stack de la aplicación web: Spring Boot + Angular

- **Estado**: Aceptado
- **Fecha**: 2026-05-20 · revisado 2026-05-27 (reorganización Nivel 1: índice + numeración de sub-decisiones; cierre de coste real del contract-first; criterios de éxito a 6 meses) · revisado 2026-06-13 (registro en D12 del salto Angular 19→22 en H0) · revisado 2026-06-15 (registro en D12 del salto Spring Boot 3.4→4.0 / Modulith 1.3→2.0 en H0) · **aceptado 2026-05-27**
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: `vision.md` (alcance MVP: web responsive), ADR-0002 (modelo de datos), ADR-0003 (autenticación), ADR-0004 (base de datos), ADR-0006 (infraestructura), ADR-0007 (monolito modular), ADR-0008 (arquitectura hexagonal y DDD), ADR-0010 (CI/CD), ADR-0011 (observabilidad)

## Índice de sub-decisiones

Este ADR fija una **decisión arquitectónica compuesta**. Las doce sub-decisiones se listan abajo con su anclaje; cada una es accionable y revisable de forma independiente, pero todas comparten contexto y argumento (en particular, la *afinidad estructural Spring↔Angular* que sostiene buena parte de las elecciones del frontend). Si una sub-decisión se cambia en el futuro, basta con tocar su sección y dejar registro en *Notas*; no es necesario abrir un ADR nuevo salvo que cambie alguna **premisa heredada**.

| #   | Sub-decisión                                                                          | Capa             |
|-----|---------------------------------------------------------------------------------------|------------------|
| D1  | [Arquitectura: SPA + API REST](#d1)                                                   | Estratégica      |
| D2  | [Lenguaje del backend: Kotlin](#d2)                                                   | Estratégica      |
| D3  | [Framework del frontend: Angular](#d3)                                                | Estratégica      |
| D4  | [TypeScript en modo `strict` completo](#d4)                                           | Disciplina FE    |
| D5  | [Componentes *standalone* (sin `NgModule`)](#d5)                                      | Disciplina FE    |
| D6  | [Estado del frontend: *signals* + servicios](#d6)                                     | Disciplina FE    |
| D7  | [Observabilidad del frontend: Sentry (errores + Web Vitals)](#d7)                     | Disciplina FE    |
| D8  | [Build del frontend: esbuild via builder oficial `application`](#d8)                  | Disciplina FE    |
| D9  | [Repositorio: monorepo](#d9)                                                          | Operativa        |
| D10 | [Contrato de API: contract-first con OpenAPI + generadores](#d10)                     | Operativa        |
| D11 | [Serving y sesión: SPA + API en mismo origen](#d11)                                   | Operativa        |
| D12 | [Política de actualización del stack](#d12)                                           | Operativa        |

## Contexto y problema

Hay que construir el MVP de Runcriticon como **aplicación web responsive** (sin app nativa — decisión cerrada en `vision.md`). El MVP son 21 funcionalidades MUST, varias con UI interactiva no trivial: el constructor de grupos con vista previa en vivo, el editor del plan semanal y la vista "hoy" del alumno.

Hay que elegir el stack antes de programar. La decisión condiciona contratación, velocidad de desarrollo y los ADR de base de datos e infraestructura.

## Requisitos no funcionales (restricciones)

El MVP es mono-club y de carga baja. Estas cifras son **restricciones** y justifican mantener el stack simple — ninguna exige arquitectura distribuida, caché especializada ni escalado horizontal:

| Dimensión | Valor |
|-----------|-------|
| **Usuarios** | ~550 en el club piloto (≈500 alumnos + ~10 entrenadores + 1 admin). Dimensionar con holgura para ~1.000 (margen para un 2.º club). |
| **Concurrencia pico** | Decenas de usuarios simultáneos; dimensionar holgado para < 100 concurrentes. |
| **Latencia** | p95 de la API < 400 ms; consultas interactivas (vista previa del constructor de grupos) < 200 ms percibidos. |
| **Volumen de datos** | ~10⁵ reportes de sesión al año — trivial para PostgreSQL (ADR-0004). |
| **Disponibilidad** | Best-effort de beta: ~99 % (ventana de mantenimiento aceptable). Single-AZ con *backups* automáticos — la durabilidad del dato está cubierta aunque la disponibilidad sea modesta. Se sube a HA cuando varios clubes dependan del servicio. |

Estas cifras se sostienen con **PostgreSQL bien indexado (ADR-0004) + JVM en caliente** sin caché aplicativa, sin Redis y sin CDN para los datos de la API. Las consultas más exigentes —resolución de pertenencia a grupo y vista previa del constructor— están a una distancia indexada del *plan SQL* objetivo. **No se introduce caché en MVP**: añadirla preventivamente sumaría una pieza más (invalidación, consistencia, observabilidad) sin beneficio mensurable a este orden de tráfico. Si la beta confirma un orden de magnitud más de carga, se evalúa con datos reales, no por anticipación.

## Premisas heredadas (no se revisan en este ADR)

Estas premisas vienen como **input cerrado** del contexto del proyecto. **No se revisan en este ADR** — se asumen y condicionan toda la decisión que sigue. Si alguna cambia (por rotación del equipo, cambio de objetivos de producto, etc.), este ADR deja de ser válido y hay que abrir uno nuevo.

- **Ecosistema JVM**. El equipo trabajará sobre la JVM. Alternativas como Node, Python o Go no se evalúan aquí: cualquiera de ellas implicaría cambio de equipo, no de stack, y esa decisión es de negocio. Lenguaje concreto (Java vs Kotlin) **sí** se decide aquí.
- **Equipo interno y estable de 4 personas**. Resta peso al criterio "pool de contratación máximo" — un argumento dominante en proyectos abiertos — y desplaza el centro de gravedad hacia la productividad del equipo concreto que ya tenemos.
- **Web responsive como única plataforma del MVP**. Sin app nativa, sin desktop. Cerrado en `vision.md`. Una posible app móvil llega post-MVP y se considera solo como factor de futuro, no como requisito.
- **Aplicación login-walled — sin landing pública en MVP**. **No** habrá página de marketing, *home* pública ni contenido accesible sin autenticación dentro del alcance del MVP. El primer pixel que ve el usuario está tras autenticación (ADR-0003). Consecuencia directa: sin SEO que servir, sin *first paint* público que optimizar, **no hay SSR** en la SPA. Si en el futuro se decide tener una landing de marketing, **se sirve aparte** (sitio estático separado) y no introduce SSR retroactivo en esta SPA — el dominio principal sigue siendo login-walled.

## Drivers de la decisión

- El equipo de backend tendrá **raíz Spring**. Cuanto más cerca esté el modelo mental del frontend del de Spring, más fácil le será al equipo entenderlo y ser full-stack — algo necesario con solo 4 personas repartidas entre dos stacks.
- **Sinergia con la futura app móvil Android** (post-MVP): Kotlin es el lenguaje nativo de Android, así que un backend en Kotlin acerca al mismo equipo a ese desarrollo.
- El modelo de datos (ADR-0002) tiene mucha **opcionalidad** (metadata de tag opcional, ritmos de tres tipos, "sin carrera") → la seguridad frente a nulos en el lenguaje aporta valor real.
- Velocidad para llegar a un MVP usable con el club piloto.
- Varias pantallas del MVP son **interactivas** (filtros en vivo, calendario editable) → el frontend debe sostener esa UX con comodidad.
- Coste de mantenimiento bajo en fase beta.

## Opciones consideradas (arquitectura — para D1)

- **Opción A** — Spring Boot como API REST + SPA como frontend.
- **Opción B** — Spring Boot con renderizado en servidor (Thymeleaf) + HTMX para interactividad.
- **Opción C** — Ktor en lugar de Spring Boot para la API.

### Opción A — Spring Boot + SPA

Backend con Spring Boot exponiendo una API REST/JSON; frontend como Single Page Application.

- 👍 Separación limpia front/back: permite que en el futuro una app nativa consuma la misma API.
- 👍 Una SPA sostiene sin fricción las pantallas interactivas (constructor de grupos, editor semanal).
- 👍 Spring Boot es el framework JVM más maduro y documentado.
- 👎 Son **dos bases de código y dos toolchains** de build. Más superficie que mantener.
- 👎 Más piezas de despliegue que un monolito renderizado en servidor.

### Opción B — Spring Boot + Thymeleaf + HTMX

Monolito que renderiza HTML en el servidor; HTMX añade interactividad parcial sin escribir apenas JavaScript.

- 👍 **Un solo lenguaje, un solo desplegable, un solo perfil.** Lo más simple de mantener.
- 👍 Menos build, menos complejidad operativa.
- 👎 Las pantallas más interactivas (vista previa en vivo del constructor de grupos, drag-drop del editor semanal) se vuelven incómodas o forzadas con HTMX.
- 👎 Si en el futuro hace falta app móvil nativa, no hay API que reutilizar.

### Opción C — Ktor en lugar de Spring Boot

Misma arquitectura SPA + API que la Opción A, pero con Ktor (framework JVM ligero) como backend.

- 👍 Ktor es ligero y moderno.
- 👎 Menos maduro y con menos ecosistema que Spring Boot — seguridad, acceso a datos y testing menos completos "de serie".
- 👎 Sin ventaja que compense renunciar a la madurez de Spring Boot en un MVP.

## Decisión

Las doce sub-decisiones se desarrollan a continuación en este orden: tres **estratégicas** (D1-D3) que fijan stack y framework, cinco de **disciplina del frontend** (D4-D8) que fijan cómo se trabaja dentro de Angular, y cuatro **operativas** (D9-D12) sobre repositorio, contrato, despliegue y mantenimiento.

<a id="d1"></a>
### D1 — Arquitectura: SPA + API REST (Opción A)

Una SPA sostiene las pantallas interactivas del MVP (constructor de grupos, editor semanal) sin forzar la arquitectura, y la API REST deja la puerta abierta a una futura app nativa sin reescribir el backend. Se descarta la Opción B pese a su simplicidad operativa porque las pantallas críticas del MVP son justamente las interactivas, y forzarlas con HTMX comprometería la UX recién validada con el club piloto. Se descarta Ktor (Opción C) por madurez frente a Spring Boot.

<a id="d2"></a>
### D2 — Lenguaje del backend: Kotlin

El equipo es interno, lo que desactiva el argumento de "máximo pool de contratación" que favorecía a Java. Además Kotlin **no es un ecosistema aparte**: misma JVM, mismo Spring Boot, mismas librerías y build — un perfil JVM/Spring es productivo en Kotlin con una curva de días, no de meses. A favor de Kotlin pesan dos cosas concretas de este proyecto:

- **Seguridad frente a nulos en el sistema de tipos**: el modelo de datos (ADR-0002) está lleno de opcionalidad. Kotlin obliga a tratar esos casos en tiempo de compilación; en Java los NPE seguirían siendo una clase de bug viva.
- **Sinergia con la app móvil Android** (post-MVP): Kotlin es el lenguaje nativo de Android. El mismo equipo de backend comparte *lenguaje* con el futuro desarrollo Android — lo que reduce el salto, aunque no lo elimina (el desarrollo Android sigue siendo una disciplina propia).

<a id="d3"></a>
### D3 — Framework del frontend: Angular

Opciones valoradas: **Angular** (elegida), **React** y **Vue**.

Angular gana por **afinidad con el equipo de backend**. Su modelo mental —inyección de dependencias, servicios, módulos, TypeScript de serie, orientación a objetos— es casi calcado al de Spring. Un desarrollador de Kotlin/Spring entiende y mantiene un frontend Angular con mucha menos fricción que uno React, y la opción de un equipo full-stack se vuelve realista — clave con solo 4 personas. "Spring + Angular" es una pareja clásica precisamente por esa alineación.

Segundo motivo: **baterías incluidas**. Angular trae de fábrica router, cliente HTTP y formularios reactivos como piezas oficiales. Eso da un **conjunto de herramientas controlado** —menos fatiga de decisión, menos inconsistencia entre desarrolladores— frente a React, donde cada pieza es una elección à la carte. Los formularios reactivos + RxJS de Angular encajan particularmente bien con las pantallas de filtro en vivo del MVP (constructor de grupos).

Se descarta **React** pese a su mayor ecosistema y pool de contratación: ese pool pesa menos con un equipo interno (mismo razonamiento que llevó a Kotlin frente a Java), y no aporta la afinidad con Spring.

Se descarta **Vue**, técnicamente sólido (Vue 3 + Composition API es maduro y competitivo), por el mismo motivo que descarta a React: **no aporta la afinidad estructural Angular↔Spring**. Vue es reactivo-funcional; Spring es objetos con inyección de dependencias. Un perfil JVM/Spring puede aprender Vue con poco esfuerzo, pero al cambiar de paradigma cognitivo se pierde la palanca de equipo full-stack que justifica todo el resto de la decisión.

### Disciplina del frontend (D4–D8)

Las cinco sub-decisiones siguientes acompañan a D3 y forman un bloque coherente: definen no **qué** framework usamos, sino **cómo** lo usamos desde el día 1. Subirlas al cuerpo del ADR (en lugar de relegarlas a *Detalles de implementación*) es deliberado: son decisiones de arquitectura del frontend, no detalles de configuración.

<a id="d4"></a>
#### D4 — TypeScript en modo `strict` completo

- **`strict: true`** en `tsconfig.json` raíz. Activa todos los *strict flags* en bloque (`noImplicitAny`, `strictNullChecks`, `strictFunctionTypes`, `strictBindCallApply`, `strictPropertyInitialization`, `noImplicitThis`, `alwaysStrict`, `useUnknownInCatchVariables`).
- **`strictTemplates: true`** en `tsconfig.app.json` (Angular). Extiende el *type-checking* a las plantillas HTML; sin esto, los errores de tipo en bindings no se detectan en compilación.

Razón: el backend en Kotlin es *null-safe* en compilación (D2); sin **paridad** en el frontend esa garantía se pierde en cuanto cruzamos la frontera HTTP y el cliente trabaja con tipos que pueden ser `undefined` sin que el compilador lo advierta. `strict: true` + `strictTemplates` cierra el círculo y traslada al frontend la misma disciplina que hace a Kotlin valioso en el backend.

`strictPropertyInitialization` colisiona ocasionalmente con la inyección de Angular: se acepta el uso puntual de *definite assignment assertion* (`!`) en propiedades inyectadas; **no** se desactiva la regla globalmente.

<a id="d5"></a>
#### D5 — Componentes *standalone* (sin `NgModule`)

Toda la aplicación se construye con **componentes, directivas y pipes *standalone***. No se usan `NgModule` salvo si una librería externa los exige (en cuyo caso se importan donde toque, no se replica el patrón en código propio).

Razón: desde Angular 17 los *standalone* son el modelo recomendado y la documentación oficial los presenta como la opción por defecto; los `NgModule` quedan como modo de compatibilidad. Para un proyecto greenfield arrancar con *standalone* significa **menos andamiaje**, *lazy loading* directo por ruta (sin `loadChildren` apuntando a módulos), árbol de dependencias más cercano al árbol de componentes y mejor *tree-shaking*. Es también la única forma coherente con D6 (estado por inyección/signals, sin store global): los `NgModule` añadirían un nivel de agrupación que no aprovecharíamos.

Consecuencia operativa: cada componente declara sus propios `imports`. No hay un "barrel" central. El equipo se acostumbra a importar explícitamente lo que usa — paralelo al `import` de Kotlin en el backend.

<a id="d6"></a>
#### D6 — Estado del frontend: *signals* + servicios inyectables

El estado del frontend vive en **servicios de Angular** (`@Injectable({ providedIn: 'root' })` o por *feature* cuando proceda), expuestos al exterior como **signals** (API nativa estable desde Angular 16). **RxJS se usa puntualmente** donde aporta valor real: *debounce* de inputs, *switchMap* en búsquedas, fuentes asíncronas que necesitan composición. No se introduce un store global (ni NgRx clásico ni equivalentes) en el MVP.

Razón: mantiene la **afinidad estructural** que sostiene todo el ADR — un servicio de Angular se parece a un *service* de Spring, lo que es bueno para un equipo full-stack. NgRx clásico añadiría un patrón Redux (actions, reducers, effects, selectors) con boilerplate significativo cuyo *payoff* no se justifica para 21 MUSTs en un mono-club: para casi todas las pantallas, la complejidad del store está bien cubierta con un servicio bien diseñado.

Regla de promoción explícita (para futuro, no para arrancar): si en algún momento un servicio termina **gestionando estado de varias *features* simultáneamente**, o aparece una necesidad real de *time-travel debugging*, o el equipo detecta estado duplicado entre servicios, se promueve esa porción concreta a **NgRx SignalStore** (no a NgRx clásico). SignalStore mantiene el modelo mental de *signals*, evita el peso del Redux clásico y la migración es localizada (no obliga a reescribir el resto del frontend). Es una palanca que tomamos solo si el dolor se manifiesta — no como prevención.

<a id="d7"></a>
#### D7 — Observabilidad del frontend: Sentry (errores + Web Vitals)

El frontend reporta a **Sentry** (SaaS, región **EU**) con un alcance acotado al MVP:

- **Errores JS no capturados** y **rechazos de promesas**, con *stack trace* legible. *Source maps* subidos a Sentry en cada *deploy* (parte del pipeline de ADR-0010); sin esto, los stacks minificados no aportan.
- **Errores HTTP 4xx/5xx** vistos desde el cliente, con la ruta y el método. Útil para detectar problemas que el backend no ve (caches, *retries* del navegador, redes intermedias).
- **Core Web Vitals** (LCP, INP, CLS) capturados como métricas de rendimiento percibido. Especialmente relevantes para la vista "hoy" del alumno, que se usa a diario en móvil y es donde el NFR p95 < 400 ms se traduce a UX real.
- **Tiempo de carga por ruta perezosa**, para detectar regresiones en el *bundle splitting*.

**Lo que NO entra en MVP**: *session replay* y captura completa de RUM. El *replay* abre frente RGPD que el ADR-0014 aún no cierra (puede capturar PII inadvertida) y para 550 usuarios el *payoff* es menor que el riesgo. Si en el futuro el dolor de depurar bugs reportados se hace insoportable, se reabre como decisión propia con RGPD ya cerrado.

Razón de elegir Sentry frente a las alternativas valoradas (un endpoint propio en Postgres; reutilizar la pila del backend que cierre ADR-0011):

- **Integración Angular oficial** y *source maps* automáticos vía su CLI — días, no semanas, de instrumentación.
- **Región EU** disponible, evita el frente RGPD básico de "los datos no salen de la UE".
- **Plan gratuito** generoso (≥ 5k errores/mes) que cubre con margen un mono-club de 550 usuarios. Si crece, escalar a plan de pago es trivial.
- **No depende** de la decisión de ADR-0011 — el backend puede ir a Datadog, Grafana, CloudWatch o lo que sea sin afectar al frontend. Cuando ADR-0011 cierre, se evalúa si conviene **correlacionar** trazas (Sentry tiene integración con la mayoría de pilas backend mediante *trace propagation*).

**Cruce con ADR-0011**: este ADR fija solo la observabilidad del frontend. La del backend la cierra ADR-0011, que debe decidir si introduce *trace propagation* (W3C *traceparent*) para que un error en Sentry tenga su traza correlativa en el lado servidor. Esa correlación es deseable, no imprescindible en MVP.

<a id="d8"></a>
#### D8 — Build del frontend: esbuild via builder oficial `application`

Angular se construye con el builder oficial **`@angular/build:application`** (basado en **esbuild** para producción y **Vite** para el servidor de desarrollo). Es el builder por defecto desde Angular 17 y la dirección oficial del framework; el builder anterior (`@angular-devkit/build-angular:browser`, basado en Webpack) queda en *legacy* para proyectos nuevos.

Razón: builds varios órdenes de magnitud más rápidos que Webpack (especialmente en local), HMR del dev server vía Vite, y nada que customizar porque la elección la lleva el propio Angular CLI. No se introduce Nx ni otro meta-toolchain encima — sobreingeniería para un repo con un solo frontend.

Política para el equipo: **no introducir configuraciones de builder personalizadas** salvo necesidad real documentada; las opciones del builder oficial cubren los casos de MVP.

<a id="d9"></a>
### D9 — Repositorio: monorepo

Backend y frontend conviven en un **único repositorio git** (el mismo `runcriticon` que ya alberga toda la documentación de discovery, wireframes y ADR). Un cambio que toca la API y su consumidor Angular va en un solo PR, se revisa junto y no queda a medias. El CI usa *triggers por path* para no recompilar el backend cuando solo cambió el frontend.

<a id="d10"></a>
### D10 — Contrato de API: contract-first con OpenAPI + generadores

La especificación **OpenAPI 3.x escrita a mano es la fuente de verdad**. De ella se generan, mediante generadores de código: los **modelos** (DTOs) Kotlin del servidor y el cliente TypeScript tipado del frontend (Angular) — **no** *stubs*/interfaces de servidor: `openApiGenerate` solo produce `models`, deliberadamente (ver comentario en `backend/build.gradle.kts`), para evitar el conflicto `ResponseEntity<T>` vs `ResponseEntity<*>` con `Either.fold`. Los controladores son clases Kotlin escritas a mano que no implementan ninguna interfaz generada; la seguridad de tipos en compilación cubre los DTOs, no la firma de los endpoints. Para que la spec no derive de la realidad en silencio sin esa red de compilación, un **test de contrato runtime** en CI (`SessionOpenApiContractTest`, `com.atlassian.oai:swagger-request-validator-core`) arranca el backend real (Testcontainers) y valida sus respuestas HTTP contra `api/openapi.yaml`. Cobertura actual: el flujo de sesión (login, consulta, cierre); ampliarlo a más endpoints es trabajo incremental, no un rediseño.

Decisiones operativas:

- **Ubicación**: la spec vive en **`api/openapi.yaml`** en la raíz del monorepo, fuera de `backend/` y `frontend/`. Es visible para ambos lados y refuerza visualmente que es un **contrato compartido**, no propiedad de ninguno de los dos. Cualquier cambio entra por PR que requiere aprobación de mantenedores de ambos lados.
- **Generador del backend**: **`openapi-generator`** con el template `kotlin-spring`, invocado como plugin de Gradle antes de compilar Kotlin. Genera solo modelos (DTOs); los controladores son código a mano, sin interfaz generada que implementar (ver arriba).
- **Generador del frontend**: **`ng-openapi-gen`**, invocado como script `npm` antes del build de Angular. Produce servicios inyectables con `HttpClient` nativo que devuelven `Observable<T>` — el estilo idiomático que encaja con D6 (*signals + RxJS puntual*) sin fricción. Se descartó `typescript-angular` de `openapi-generator` por menor calidad de la salida en escenarios polimórficos y por estilo menos idiomático.
- **Código generado NO se commitea**: se regenera en cada build, tanto local como en CI. La salida vive en `build/generated-src/openapi/` (backend, ignorada por git) y en `src/app/api/generated/` (frontend, ignorada por git). Razón: el código generado es un *artefacto*, no un *fuente*; commitearlo crea deriva silenciosa cuando alguien edita el generado a mano "porque pica una vez".
- **Versionado**: las versiones de `openapi-generator` y `ng-openapi-gen` se fijan en `build.gradle.kts` y `package.json` respectivamente. Se actualizan vía Dependabot (D12) como cualquier otra dependencia.
- **Test de contrato**: ya descrito arriba (`SessionOpenApiContractTest`); obligatorio en *verde* para mergear (ADR-0010), corre en el `test` de Gradle normal (Testcontainers).

**Coste real de mantenimiento — workflow y responsabilidades**

El contract-first solo es barato si el equipo lo trabaja con disciplina. Sin reglas explícitas se degrada a *"el back implementa, alguien actualiza la spec si se acuerda"* — el peor de los mundos. Reglas vinculantes:

- **Propiedad de la spec**: la spec **no tiene un único owner**. La mantiene **el desarrollador que introduce el cambio** en el endpoint correspondiente. La autoría se ve en `git blame`; las dudas sobre intención se resuelven mirando el PR que introdujo o tocó la regla.
- **PR de cambio de API: spec primero, en el mismo PR**. Cualquier cambio que toque la API entra como un **único PR** que contiene, en este orden lógico (no temporal — todo en el mismo commit es legítimo):
  1. Modificación de `api/openapi.yaml`.
  2. Regeneración del código (CI verifica que está alineado; los artefactos no se commitean — D10 más arriba).
  3. Implementación del cambio en backend y frontend.
  4. Tests, incluyendo el test de contrato.
- **Revisión cruzada obligatoria**: el PR requiere **al menos un aprobador del lado opuesto**. Si el cambio nace en backend, lo revisa alguien que típicamente trabaja en frontend, y viceversa. Coste real: un *reviewer* más por PR de API. Vale la pena: detecta convenciones rotas que el autor no ve.
- **Cambios breaking** (eliminar campo, cambiar tipo, cambiar URL, cambiar status code): **no entran en el mismo PR que cambios no breaking**. Van en un PR propio etiquetado `breaking-api`, con análisis de impacto en el cuerpo (qué consumidores se rompen y cómo se actualizan). En MVP, como front y back se despliegan juntos desde el monorepo, "consumidores" significa solo la SPA — el coste de coordinación es bajo, pero el etiquetado disciplina al equipo para cuando llegue una app móvil que rompa este supuesto.
- **Sin versionado de API en MVP**: una sola URL `app.runcriticon.com/api/...` sin `/v1/`. Cuando aparezca un segundo consumidor que no se despliegue con la SPA (típicamente una app móvil futura), se evaluará introducir versionado — es un ADR aparte llegado el momento.
- **Coste estimado por PR de API**: el añadido sobre un cambio de código equivalente sin contract-first es del orden de **15-30 minutos** (editar YAML + revisar la regeneración + revisión cruzada). Se asume conscientemente como inversión en evitar deriva front↔back, que es el dolor real que el contract-first ataca.

<a id="d11"></a>
### D11 — Serving y sesión: SPA + API en mismo origen

La SPA y la API se sirven bajo un **único origen** (p. ej. la SPA en `app.runcriticon.com/` y la API en `app.runcriticon.com/api`). Eso mantiene la cookie de sesión de ADR-0003 como **first-party** (`SameSite=Lax`): la opción más simple y más segura, sin CORS con credenciales y con menor superficie de CSRF. El mismo origen se materializa con la **propia app Spring sirviendo los estáticos de Angular** (decidido en ADR-0006). Cruces: ADR-0003 (sesión por cookie), ADR-0006 (infraestructura).

<a id="d12"></a>
### D12 — Política de actualización del stack

La regla general es **estabilidad sobre novedad**: ninguna versión recién publicada entra en producción sin un periodo de espera. Por componente:

- **Java**: nos quedamos en la **LTS vigente**. Cada nueva LTS dispara una revisión en el **primer trimestre tras su salida**. La adopción se hace en la siguiente ventana de mantenimiento planificada, no como cambio urgente.
- **Spring Boot**: seguimos la **línea menor vigente** dentro de la mayor adoptada. Cada nueva línea mayor (3.x → 4.x, salida típicamente anual) dispara una revisión en los **6 meses siguientes**; entre líneas mayores se aplican releases menores automáticamente vía Dependabot.
- **Angular**: releases mayores cada **6 meses**, con ventana de soporte LTS de **18 meses**. Nos comprometemos a estar siempre en una versión con soporte oficial; la actualización a la siguiente LTS se planifica como **bloque de trabajo identificado en backlog**, no como tarea de relleno. Se evita saltar dos majors a la vez.
- **TypeScript**: sigue a Angular — cada versión de Angular fija el rango compatible.
- **Dependencias menores**: actualización continua vía **Dependabot** (nativo en GitHub, sin servicio externo añadido, coherente con ADR-0010), con merge automático si CI verde y la dependencia está clasificada como "menor / parche".

Política transversal:

- **No** adoptar `.0` recién salidos en producción; esperar al primer `.1` o `.2` como mínimo.
- **No** saltar versiones mayores (p. ej. Angular 17 → 19 directo). Migrar incrementalmente.
- Cada actualización mayor exige re-ejecutar los *quality gates* del ADR-0010 (tests de contrato, ArchUnit, etc.) en una rama dedicada antes de merge.

> **Nota (revisado 2026-06-13) — salto Angular 19 → 22 en H0.** El frontend saltó de Angular 19 a 22 (tres líneas mayores) en un único bloque de trabajo, aparentemente en tensión con *"evitar saltar dos majors a la vez"*. Se registra como **excepción consciente y acotada**, no como cambio de la política:
> - **Migración incremental real**: el salto se ejecutó encadenando `ng update` 19 → 20 → 21 → 22, aplicando los *schematics* oficiales de cada versión y validando los *quality gates* (lint + tests + build) entre escalones. Cumple *"migrar incrementalmente"*; lo que se condensó fue el calendario, no los pasos.
> - **Coste mínimo por contexto**: se hizo en H0 con el frontend en estado de esqueleto (login, home, *guard*, sesión) — superficie casi nula. Diferirlo hasta tener las 21 pantallas MUST habría multiplicado el coste.
> - **Disparador**: Angular 19 salía de su ventana de soporte LTS de 18 meses; la política obliga a estar siempre en una versión con soporte oficial.
> - **Arrastre coordinado**: el salto fijó TypeScript 6.0 (Angular 22 exige `>=6.0.0 <6.1.0`, derivado de D12 *"TypeScript sigue a Angular"*), Node 22.22.3 y el *stack* de testing (jest 30 / jest-preset-angular 16). Todo en el mismo PR del bloque.
>
> Para futuros saltos con el frontend ya poblado sigue vigente la regla general: **una línea mayor por bloque de trabajo planificado**.

> **Nota (revisado 2026-06-15) — salto Spring Boot 3.4 → 4.0 / Modulith 1.3 → 2.0 en H0.** El backend saltó de Spring Boot 3.4 a 4.0 (con Spring Modulith 1.3 → 2.0). A diferencia del salto de Angular, esto **no es una excepción a la política**: es su aplicación directa — D12 establece que *"cada nueva línea mayor (3.x → 4.x) dispara una revisión en los 6 meses siguientes"*. Se registra el resultado de esa revisión:
> - **Disparador**: salida de la línea mayor Spring Boot 4.0; la propia D12 obliga a revisar el upgrade dentro de los 6 meses.
> - **Coste mínimo por contexto**: ejecutado en H0 con el backend en esqueleto (módulo `identidad` + núcleo `shared/`), antes de poblar los cinco módulos. Diferirlo habría multiplicado la superficie de migración.
> - **Quality gates en verde**: ArchUnit, tests de límites de Spring Modulith, Testcontainers y contract tests re-ejecutados en rama dedicada antes de merge (ADR-0010), conforme a la política transversal de D12.
> - **No `.0` a ciegas**: se adoptó **4.0.6** (no el `4.0.0`), respetando *"esperar al primer `.1`/`.2` como mínimo"*.
> - **Arrastre coordinado**: Spring Modulith **2.0.6** (requiere SB4) — que añade tres columnas a `event_publication` (`completion_attempts`, `status`, `last_resubmission_date`; migraciones `_shared/V202606140001-2`); eliminación de `io.spring.dependency-management` (BOMs declarados con `platform()` en Gradle nativo); `spring-boot-flyway` como módulo explícito; y `TestRestTemplate` → `RestTemplate` con `DefaultResponseErrorHandler`. Todo en el mismo bloque (PR #108).

## Detalles de implementación

Configuración menor que **no constituye decisión propia**: deriva de las sub-decisiones D1-D12.

- **Stack inicial al arrancar**: Java 21 (LTS), Spring Boot 3.x (última estable), Angular LTS vigente. Las reglas de evolución viven en D12.
- **Sin SSR**: derivado de D1 + premisa *login-walled*. La SPA no renderiza en servidor; el bundle se sirve estático.
- **Lazy loading por ruta** obligatorio: derivado de D5 + UX móvil. La vista del alumno se carga sin penalizar el primer paint en conexiones móviles.
- **API REST/JSON**: sin GraphQL en MVP. Para este alcance, el contract-first + OpenAPI (D10) cubre la necesidad sin la complejidad operativa de un servidor GraphQL.

## Consecuencias

### Positivas

- El frontend (Angular) es mentalmente cercano al backend (Spring) → el equipo de 4 entiende el front con menos fricción y el full-stack es viable.
- **Tooling controlado**: las baterías incluidas de Angular acotan las herramientas y reducen la inconsistencia entre desarrolladores.
- **Monorepo**: cambios atómicos front↔back en un solo PR; el contrato de API vive junto al código que lo usa.
- **Contrato de API contract-first con test de contrato**: front y back no pueden divergir sin que CI lo detecte.
- Frontend preparado para la interactividad ya validada en los wireframes.
- API REST reutilizable si se decide construir app nativa post-MVP.
- **Seguridad frente a nulos de Kotlin**: menos NPE en un dominio con mucha opcionalidad.
- El equipo de backend comparte lenguaje (Kotlin) con la futura app Android.

### Negativas / coste asumido

- Dos bases de código y dos toolchains de build (Kotlin/Spring y TypeScript/Angular). Se asume conscientemente a cambio de la UX y de la coherencia con el equipo.
- Angular tiene una **curva de aprendizaje más empinada** y es más pesado que React — más ceremonia para un MVP pequeño. Se acepta a cambio de la afinidad con Spring y la estructura impuesta.
- Mantener la spec OpenAPI a mano (contract-first) es trabajo explícito — mitigado por el test de contrato, que evita que ese trabajo se desincronice.
- Más complejidad de despliegue que un monolito (se aborda en ADR-0006).

### Riesgos y mitigaciones

- **Un equipo de 4 personas repartido entre dos stacks (Kotlin y Angular)** → la afinidad estructural Angular↔Spring es justamente la mitigación: un perfil de backend entra en Angular con poca fricción, así que el equipo puede operar **full-stack** en lugar de quedar partido en dos mitades rígidas de 2 personas. Conviene que la mayoría del equipo sea cómoda full-stack.
- **Curva real del equipo con Angular**. La "afinidad estructural" reduce la curva, no la elimina. Si **ninguno** de los 4 ha tocado Angular antes en proyecto real, los *días* de adaptación se convierten en *semanas* reales en el frontend. **Mitigación**: (a) auditar la experiencia previa del equipo con Angular antes de arrancar y, si es nula, reservar un bloque de formación interna de 1-2 semanas como parte del Hito H0; (b) *pair programming* deliberado en los primeros PRs full-stack; (c) empezar por componentes simples (login, vista hoy) y dejar el constructor de grupos para cuando el equipo esté en velocidad.
- **Acoplamiento al "mismo origen"** con la API. La decisión de servir SPA y API bajo un único dominio (cookie *first-party*, sin CORS, menos CSRF) es la correcta para el MVP **mientras** todos los clientes son la SPA. Si en el futuro llega una app nativa que consume la API desde otro origen, hay que reabrir CORS, *bearer tokens* o un esquema mixto, y revisar CSRF — no es un cambio trivial. **Mitigación**: aceptar que esa eventual transición requerirá un ADR propio; no se introduce CORS preventivamente porque añadiría superficie de seguridad sin beneficio actual. La API REST en sí ya es reutilizable; lo que cambia es el modelo de sesión, no el contrato.
- **Sobre-ingeniería del frontend** → mantener Angular simple en MVP: componentes *standalone* (D5), signals + servicios sin store global (D6), sin librerías pesadas hasta que el dolor se manifieste. Las sub-decisiones D4-D8 fijan las reglas de partida.
- **La spec OpenAPI deriva de la implementación** → test de contrato en CI, obligatorio en *verde* para mergear (D10).

## Criterios de éxito a 6 meses

Este ADR apuesta por elecciones (afinidad estructural Spring↔Angular, monorepo, contract-first, type-safety en ambos lados) cuya validez se puede medir con datos. Pasados ~6 meses desde el arranque del desarrollo se revisa la tabla siguiente con los datos reales. Si **más de una** métrica está en rojo, se reabre el ADR; con **una sola en rojo** se anota como deuda y se trata en la siguiente revisión trimestral.

| # | Métrica | Umbral verde | Atribuible a | Cómo medirla |
|---|---------|--------------|--------------|--------------|
| M1 | % del equipo capaz de abrir un PR *full-stack* (toca back y front) sin pedir ayuda especializada | **≥ 60 %** | D3, D4-D8 (afinidad estructural Angular↔Spring) | Auto-evaluación trimestral del equipo + observación de quién abre cada PR |
| M2 | Tiempo medio desde apertura a merge de un PR que toca back y front | **≤ 3 días laborables** | D9 (monorepo) + D10 (contract-first ágil) | Métricas de PR de GitHub, ventana móvil de 30 días |
| M3 | Incidentes en producción atribuibles a divergencia front↔back en la API (response distinto a la spec, campo que aparece/desaparece sin acuerdo) | **< 1 al trimestre** | D10 (contract-first + test de contrato en CI) | Tickets de bug + búsqueda en Sentry por patrones |
| M4 | Errores en producción de la familia *null/undefined* (NPE en backend, `Cannot read property 'X' of undefined` en frontend) | **< 5 al mes**, no concentrados en una sola feature | D2 (Kotlin null-safe) + D4 (TS strict) | Sentry + logs estructurados del backend |
| M5 | % de PRs de Dependabot mergeados sin intervención humana (CI verde + dependencia clasificada como menor/parche) | **≥ 80 %** | D12 (política de actualización + Dependabot bien configurado) | Conteo de PRs etiquetados `dependencies` mergeados auto vs cerrados/editados |

**Cómo se interpretan los resultados**:

- **Las cinco verdes** → la apuesta arquitectónica está bien calibrada; se mantiene sin tocar y se vuelve a medir a 12 meses.
- **Una métrica en rojo** → se anota la deuda, se documenta la causa (¿la decisión está fallando? ¿hay un factor externo como rotación de equipo?). Se vuelve a medir a 9 meses. Si sigue en rojo, escala a reabrir el ADR de la sub-decisión correspondiente.
- **Dos o más métricas en rojo** → reabrir el ADR es **mandatorio**, no opcional. Indica que la afinidad estructural, el monorepo o el contract-first no están entregando el valor que justifica su coste; hay que decidir si ajustar o revertir.

**Lo que NO se mide aquí**: rendimiento de los NFRs (latencia p95, throughput) — ya cubierto en la observación operativa diaria de ADR-0011. Adopción del producto por usuarios reales — eso es la beta H1 según `plan-implementacion-mvp.md`. Calidad del código (cobertura, deuda Sonar) — pertenece a ADR-0010 y a la Task #2 (externalización del análisis estático).

## Notas

- Este ADR incorpora los resultados de una **revisión de arquitectura** (2026-05-20): se añadieron las decisiones de monorepo, contrato de API contract-first y serving en mismo dominio, los requisitos no funcionales explícitos y la justificación de la ausencia de SSR.
- **Reorganización del 2026-05-27 (Nivel 1)**: el ADR creció a 12 sub-decisiones al cerrar disciplina del frontend, observabilidad, build y política de actualización. Se añade el índice y la numeración D1-D12 para que cada sub-decisión sea localizable y revisable de forma independiente, manteniendo el contenido cohesivo en un único ADR. Si en el futuro el documento sigue creciendo, se considera dividirlo en dos ADRs (estratégico vs operativo).
- Si en el futuro se quiere ir más allá de "compartir lenguaje" con Android, **Kotlin Multiplatform (KMP)** permitiría compartir código real (modelos, validaciones, lógica de dominio) entre backend y app móvil. No es una decisión del MVP, pero elegir Kotlin (D2) ahora la deja disponible.
- Las versiones exactas (JVM, Spring Boot, Angular) se fijan al iniciar el desarrollo respetando los suelos y la política de actualización (D12).
- **Revisión del 2026-07-11**: D10 corrige la descripción del generador backend (solo modelos, nunca interfaces/*stubs* — así ha sido desde el primer código, `build.gradle.kts` lo documenta explícitamente) y sustituye el "test de contrato" que nunca se implementó por el mecanismo real: `SessionOpenApiContractTest`, un test runtime con `swagger-request-validator-core` que valida las respuestas HTTP reales del backend arrancado contra `api/openapi.yaml`. Detectado por auditoría de drift documentación-código (23 docs, 61 hallazgos).
