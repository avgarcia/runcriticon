# ADR-0001 — Stack de la aplicación web: Spring Boot + React

- **Estado**: Propuesto
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: `vision.md` (alcance MVP: web responsive), ADR-0004 (base de datos), ADR-0006 (infraestructura)

## Contexto y problema

Hay que construir el MVP de Runcriticon como **aplicación web responsive** (sin app nativa — decisión cerrada en `vision.md`). El MVP son 19 funcionalidades MUST, varias con UI interactiva no trivial: el constructor de grupos con vista previa en vivo, el editor del plan semanal y la vista "hoy" del alumno.

Hay que elegir el stack antes de programar. La decisión condiciona contratación, velocidad de desarrollo y los ADR de base de datos e infraestructura.

## Drivers de la decisión

- El equipo que construirá el MVP trabajará en el **ecosistema JVM** (input de negocio).
- El equipo será **probablemente interno** (no agencia). Eso reduce el peso de "máximo pool de contratación posible" y abre la puerta a un lenguaje JVM más moderno.
- **Sinergia con la futura app móvil Android** (post-MVP): Kotlin es el lenguaje nativo de Android, así que un backend en Kotlin acerca al mismo equipo a ese desarrollo.
- El modelo de datos (ADR-0002) tiene mucha **opcionalidad** (metadata de tag opcional, ritmos de tres tipos, "sin carrera") → la seguridad frente a nulos en el lenguaje aporta valor real.
- Velocidad para llegar a un MVP usable con el club piloto.
- Varias pantallas del MVP son **interactivas** (filtros en vivo, calendario editable) → el frontend debe sostener esa UX con comodidad.
- Coste de mantenimiento bajo en fase beta (pocos usuarios, un club).

## Opciones consideradas

- **Opción A** — Spring Boot (Java) como API REST + React (SPA) como frontend.
- **Opción B** — Spring Boot con renderizado en servidor (Thymeleaf) + HTMX para interactividad.
- **Opción C** — Kotlin + Ktor en el back + React en el front.

### Opción A — Spring Boot + React SPA

Backend Java con Spring Boot exponiendo una API REST/JSON; frontend React como Single Page Application servida aparte.

- 👍 Java + Spring Boot y React son las dos tecnologías **más demandadas y documentadas** de sus áreas — contratación fácil con el equipo aún sin definir.
- 👍 React sostiene sin fricción las pantallas interactivas (constructor de grupos, editor semanal).
- 👍 Separación limpia front/back: permite que en el futuro una app nativa consuma la misma API.
- 👎 Son **dos bases de código y dos perfiles** (Java y JS/TS). Más superficie que mantener.
- 👎 Más piezas de build y despliegue que un monolito renderizado en servidor.

### Opción B — Spring Boot + Thymeleaf + HTMX

Monolito Java que renderiza HTML en el servidor; HTMX añade interactividad parcial sin escribir apenas JavaScript.

- 👍 **Un solo lenguaje, un solo desplegable, un solo perfil.** Lo más simple de mantener para un equipo pequeño o un único desarrollador.
- 👍 Menos build, menos complejidad operativa.
- 👎 Las pantallas más interactivas (vista previa en vivo del constructor de grupos, drag-drop del editor semanal) se vuelven incómodas o forzadas con HTMX.
- 👎 HTMX es menos *mainstream* — más difícil de contratar con el equipo sin definir.
- 👎 Si en el futuro hace falta app móvil nativa, no hay API que reutilizar.

### Opción C — Ktor en lugar de Spring Boot

Misma arquitectura SPA + API que la Opción A, pero con Ktor (framework JVM ligero) como backend en vez de Spring Boot.

- 👍 Ktor es ligero y moderno.
- 👎 Menos maduro y con menos ecosistema que Spring Boot — seguridad, acceso a datos y testing menos completos "de serie".
- 👎 Sin ventaja que compense renunciar a la madurez de Spring Boot en un MVP.

> Nota: el lenguaje del backend (Java vs Kotlin) es una decisión **ortogonal** a la arquitectura — se trata aparte en la sección "Decisión".

## Decisión

Dos decisiones: una de arquitectura y una de lenguaje del backend.

**Arquitectura — Opción A: Spring Boot como API REST + React (TypeScript) como SPA.**

React sostiene las pantallas interactivas del MVP (constructor de grupos, editor semanal) sin forzar la arquitectura, y la API REST deja la puerta abierta a una futura app nativa sin reescribir el backend. Se descarta la Opción B pese a su simplicidad operativa porque las pantallas críticas del MVP son justamente las interactivas, y forzarlas con HTMX comprometería la UX recién validada con el club piloto. Se descarta Ktor (Opción C) por madurez frente a Spring Boot.

**Lenguaje del backend — Kotlin.**

El equipo será probablemente interno, lo que desactiva el argumento de "máximo pool de contratación" que favorecía a Java. Además Kotlin **no es un ecosistema aparte**: misma JVM, mismo Spring Boot, mismas librerías y build — un perfil JVM/Spring es productivo en Kotlin con una curva de días, no de meses. A favor de Kotlin pesan dos cosas concretas de este proyecto:

- **Seguridad frente a nulos en el sistema de tipos**: el modelo de datos (ADR-0002) está lleno de opcionalidad (metadata de tag opcional, ritmos de tres tipos, "sin carrera"). Kotlin obliga a tratar esos casos en tiempo de compilación; en Java los NPE seguirían siendo una clase de bug viva.
- **Sinergia con la app móvil Android** (post-MVP): Kotlin es el lenguaje nativo de Android. El mismo equipo de backend comparte *lenguaje* con el futuro desarrollo Android — lo que reduce el salto, aunque no lo elimina (el desarrollo Android sigue siendo una disciplina propia).

Detalles:

- **Backend**: Kotlin sobre Spring Boot (Spring da soporte oficial a Kotlin desde 2017: Initializr lo ofrece, la documentación tiene ejemplos en Kotlin). JVM LTS vigente.
- **Frontend**: React + TypeScript. TypeScript no es opcional — el tipado reduce errores y ayuda a un equipo que entra frío.
- **Comunicación**: API REST/JSON. Sin GraphQL en MVP (complejidad innecesaria para este alcance).

## Consecuencias

### Positivas

- Frontend preparado para la interactividad ya validada en los wireframes.
- API reutilizable si se decide construir app nativa post-MVP.
- **Seguridad frente a nulos de Kotlin**: menos NPE en un dominio con mucha opcionalidad.
- El equipo de backend comparte lenguaje (Kotlin) con la futura app Android — onboarding a ese desarrollo más barato.

### Negativas / coste asumido

- Dos bases de código, dos toolchains de build, potencialmente dos perfiles (Kotlin y TS/React). Se asume conscientemente a cambio de la UX.
- Más complejidad de despliegue que un monolito (se aborda en ADR-0006).
- Pool de contratación de "Kotlin + Spring" algo menor que el de "Java + Spring" en términos absolutos — mitigado porque cualquier perfil JVM/Spring entra en Kotlin en días.

### Riesgos y mitigaciones

- **Un equipo muy pequeño se reparte mal entre dos stacks** → si el MVP lo arranca un único desarrollador, considerar perfiles full-stack o empezar por la API y un frontend mínimo.
- **Sobre-ingeniería del frontend** → mantener React simple en MVP: sin librería de estado global pesada hasta que haga falta; routing y fetching estándar.
- **El equipo final acaba siendo una agencia genérica de Java** → reconsiderar el lenguaje: Java seguiría siendo válido sobre la misma arquitectura, sin tocar nada más del ADR.

## Notas

- Revisar esta decisión si el equipo final resultara ser una sola persona sin perfil frontend: en ese escenario la Opción B (monolito) vuelve a la mesa.
- Si en el futuro se quiere ir más allá de "compartir lenguaje" con Android, **Kotlin Multiplatform (KMP)** permitiría compartir código real (modelos, validaciones, lógica de dominio) entre backend y app móvil. No es una decisión del MVP, pero elegir Kotlin ahora la deja disponible.
- Las versiones concretas (JVM LTS, Spring Boot, React) se fijan al iniciar el desarrollo, no en este ADR.
