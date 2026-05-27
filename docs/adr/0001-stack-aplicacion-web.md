# ADR-0001 — Stack de la aplicación web: Spring Boot + Angular

- **Estado**: Propuesto
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: `vision.md` (alcance MVP: web responsive), ADR-0002 (modelo de datos), ADR-0003 (autenticación), ADR-0004 (base de datos), ADR-0006 (infraestructura), ADR-0007 (monolito modular), ADR-0008 (arquitectura hexagonal y DDD), ADR-0010 (CI/CD)

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

## Drivers de la decisión

- El equipo que construirá el MVP trabajará en el **ecosistema JVM** (input de negocio).
- El equipo será **interno y de 4 personas**. Eso resta peso a "máximo pool de contratación posible" y desplaza el criterio hacia la productividad del equipo concreto.
- El equipo de backend tendrá **raíz Spring**. Cuanto más cerca esté el modelo mental del frontend del de Spring, más fácil le será al equipo entenderlo y ser full-stack — algo necesario con solo 4 personas repartidas entre dos stacks.
- **Sinergia con la futura app móvil Android** (post-MVP): Kotlin es el lenguaje nativo de Android, así que un backend en Kotlin acerca al mismo equipo a ese desarrollo.
- El modelo de datos (ADR-0002) tiene mucha **opcionalidad** (metadata de tag opcional, ritmos de tres tipos, "sin carrera") → la seguridad frente a nulos en el lenguaje aporta valor real.
- Velocidad para llegar a un MVP usable con el club piloto.
- Varias pantallas del MVP son **interactivas** (filtros en vivo, calendario editable) → el frontend debe sostener esa UX con comodidad.
- Coste de mantenimiento bajo en fase beta.

## Opciones consideradas (arquitectura)

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

Tres decisiones de fondo: arquitectura, lenguaje del backend y framework del frontend.

### Arquitectura — Opción A: Spring Boot como API REST + SPA

Una SPA sostiene las pantallas interactivas del MVP (constructor de grupos, editor semanal) sin forzar la arquitectura, y la API REST deja la puerta abierta a una futura app nativa sin reescribir el backend. Se descarta la Opción B pese a su simplicidad operativa porque las pantallas críticas del MVP son justamente las interactivas, y forzarlas con HTMX comprometería la UX recién validada con el club piloto. Se descarta Ktor (Opción C) por madurez frente a Spring Boot.

### Lenguaje del backend — Kotlin

El equipo es interno, lo que desactiva el argumento de "máximo pool de contratación" que favorecía a Java. Además Kotlin **no es un ecosistema aparte**: misma JVM, mismo Spring Boot, mismas librerías y build — un perfil JVM/Spring es productivo en Kotlin con una curva de días, no de meses. A favor de Kotlin pesan dos cosas concretas de este proyecto:

- **Seguridad frente a nulos en el sistema de tipos**: el modelo de datos (ADR-0002) está lleno de opcionalidad. Kotlin obliga a tratar esos casos en tiempo de compilación; en Java los NPE seguirían siendo una clase de bug viva.
- **Sinergia con la app móvil Android** (post-MVP): Kotlin es el lenguaje nativo de Android. El mismo equipo de backend comparte *lenguaje* con el futuro desarrollo Android — lo que reduce el salto, aunque no lo elimina (el desarrollo Android sigue siendo una disciplina propia).

### Framework del frontend — Angular

Opciones valoradas: **Angular** (elegida), **React** y **Vue**.

Angular gana por **afinidad con el equipo de backend**. Su modelo mental —inyección de dependencias, servicios, módulos, TypeScript de serie, orientación a objetos— es casi calcado al de Spring. Un desarrollador de Kotlin/Spring entiende y mantiene un frontend Angular con mucha menos fricción que uno React, y la opción de un equipo full-stack se vuelve realista — clave con solo 4 personas. "Spring + Angular" es una pareja clásica precisamente por esa alineación.

Segundo motivo: **baterías incluidas**. Angular trae de fábrica router, cliente HTTP y formularios reactivos como piezas oficiales. Eso da un **conjunto de herramientas controlado** —menos fatiga de decisión, menos inconsistencia entre desarrolladores— frente a React, donde cada pieza es una elección à la carte. Los formularios reactivos + RxJS de Angular encajan particularmente bien con las pantallas de filtro en vivo del MVP (constructor de grupos).

Se descarta **React** pese a su mayor ecosistema y pool de contratación: ese pool pesa menos con un equipo interno (mismo razonamiento que llevó a Kotlin frente a Java), y no aporta la afinidad con Spring. Se descarta **Vue** por tener menos presencia en entornos *enterprise* y no ofrecer la afinidad estructural de Angular.

### Detalles de implementación

**Stack y versiones**

- **Backend**: Kotlin sobre Spring Boot. Suelo de versiones: JVM LTS vigente al iniciar (no inferior a Java 21), Spring Boot línea 3.x.
- **Frontend**: Angular + TypeScript (TypeScript es nativo en Angular). Versión reciente de Angular con **componentes *standalone*** (coherente con ADR-0008).
- **Sin SSR**: la SPA es *login-walled* — no hay requisito de SEO ni de *first paint* público, así que el renderizado en servidor no aporta nada y se omite.
- **Lazy loading por ruta** obligatorio en Angular: la vista del alumno es de uso diario en móvil y los bundles de Angular pesan; la carga diferida evita penalizar la carga inicial en conexiones móviles.
- **Comunicación**: API REST/JSON. Sin GraphQL en MVP (complejidad innecesaria para este alcance).

**Repositorio — monorepo**

Backend y frontend conviven en un **único repositorio git** (el mismo `runcriticon` que ya alberga toda la documentación de discovery, wireframes y ADR). Un cambio que toca la API y su consumidor Angular va en un solo PR, se revisa junto y no queda a medias. El CI usa *triggers por path* para no recompilar el backend cuando solo cambió el frontend.

**Contrato de API — contract-first**

La especificación **OpenAPI escrita a mano es la fuente de verdad**. De ella se generan, mediante generadores de código: los *stubs*/interfaces del servidor (Kotlin) y el **cliente TypeScript tipado** del frontend (Angular). Un **test de contrato** en CI verifica que la implementación real del backend cumple la spec — así la spec no puede derivar de la realidad en silencio, que es el único punto débil del contract-first. La spec vive en el monorepo, visible para ambos lados.

**Serving y sesión — mismo dominio**

La SPA y la API se sirven bajo un **único origen** (p. ej. la SPA en `app.runcriticon.com/` y la API en `app.runcriticon.com/api`). Eso mantiene la cookie de sesión de ADR-0003 como **first-party** (`SameSite=Lax`): la opción más simple y más segura, sin CORS con credenciales y con menor superficie de CSRF. El mismo origen se materializa con la **propia app Spring sirviendo los estáticos de Angular** (decidido en ADR-0006). Cruces: ADR-0003 (sesión por cookie), ADR-0006 (infraestructura).

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
- **Sobre-ingeniería del frontend** → mantener Angular simple en MVP: componentes *standalone*, sin sobre-modularizar, sin librerías de estado pesadas hasta que haga falta.
- **La spec OpenAPI deriva de la implementación** → test de contrato en CI, obligatorio en *verde* para mergear.

## Notas

- Este ADR incorpora los resultados de una **revisión de arquitectura** (2026-05-20): se añadieron las decisiones de monorepo, contrato de API contract-first y serving en mismo dominio, los requisitos no funcionales explícitos y la justificación de la ausencia de SSR.
- Si en el futuro se quiere ir más allá de "compartir lenguaje" con Android, **Kotlin Multiplatform (KMP)** permitiría compartir código real (modelos, validaciones, lógica de dominio) entre backend y app móvil. No es una decisión del MVP, pero elegir Kotlin ahora la deja disponible.
- Las versiones exactas (JVM, Spring Boot, Angular) se fijan al iniciar el desarrollo respetando los suelos indicados en "Detalles de implementación".
