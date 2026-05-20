# ADR-0001 — Stack de la aplicación web: Spring Boot + Angular

- **Estado**: Propuesto
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: `vision.md` (alcance MVP: web responsive), ADR-0002 (modelo de datos), ADR-0004 (base de datos), ADR-0006 (infraestructura)

## Contexto y problema

Hay que construir el MVP de Runcriticon como **aplicación web responsive** (sin app nativa — decisión cerrada en `vision.md`). El MVP son 19 funcionalidades MUST, varias con UI interactiva no trivial: el constructor de grupos con vista previa en vivo, el editor del plan semanal y la vista "hoy" del alumno.

Hay que elegir el stack antes de programar. La decisión condiciona contratación, velocidad de desarrollo y los ADR de base de datos e infraestructura.

## Drivers de la decisión

- El equipo que construirá el MVP trabajará en el **ecosistema JVM** (input de negocio).
- El equipo será **probablemente interno** (no agencia). Eso reduce el peso de "máximo pool de contratación posible" y desplaza el criterio hacia la productividad del equipo concreto.
- El equipo de backend tendrá **raíz Spring**. Cuanto más cerca esté el modelo mental del frontend del de Spring, más fácil le será al equipo entenderlo y, llegado el caso, ser full-stack.
- **Sinergia con la futura app móvil Android** (post-MVP): Kotlin es el lenguaje nativo de Android, así que un backend en Kotlin acerca al mismo equipo a ese desarrollo.
- El modelo de datos (ADR-0002) tiene mucha **opcionalidad** (metadata de tag opcional, ritmos de tres tipos, "sin carrera") → la seguridad frente a nulos en el lenguaje aporta valor real.
- Velocidad para llegar a un MVP usable con el club piloto.
- Varias pantallas del MVP son **interactivas** (filtros en vivo, calendario editable) → el frontend debe sostener esa UX con comodidad.
- Coste de mantenimiento bajo en fase beta (pocos usuarios, un club).

## Opciones consideradas (arquitectura)

- **Opción A** — Spring Boot como API REST + SPA como frontend.
- **Opción B** — Spring Boot con renderizado en servidor (Thymeleaf) + HTMX para interactividad.
- **Opción C** — Ktor en lugar de Spring Boot para la API.

### Opción A — Spring Boot + SPA

Backend con Spring Boot exponiendo una API REST/JSON; frontend como Single Page Application servida aparte.

- 👍 Separación limpia front/back: permite que en el futuro una app nativa consuma la misma API.
- 👍 Una SPA sostiene sin fricción las pantallas interactivas (constructor de grupos, editor semanal).
- 👍 Spring Boot es el framework JVM más maduro y documentado.
- 👎 Son **dos bases de código y dos toolchains** de build. Más superficie que mantener.
- 👎 Más piezas de despliegue que un monolito renderizado en servidor.

### Opción B — Spring Boot + Thymeleaf + HTMX

Monolito que renderiza HTML en el servidor; HTMX añade interactividad parcial sin escribir apenas JavaScript.

- 👍 **Un solo lenguaje, un solo desplegable, un solo perfil.** Lo más simple de mantener para un equipo pequeño o un único desarrollador.
- 👍 Menos build, menos complejidad operativa.
- 👎 Las pantallas más interactivas (vista previa en vivo del constructor de grupos, drag-drop del editor semanal) se vuelven incómodas o forzadas con HTMX.
- 👎 Si en el futuro hace falta app móvil nativa, no hay API que reutilizar.

### Opción C — Ktor en lugar de Spring Boot

Misma arquitectura SPA + API que la Opción A, pero con Ktor (framework JVM ligero) como backend.

- 👍 Ktor es ligero y moderno.
- 👎 Menos maduro y con menos ecosistema que Spring Boot — seguridad, acceso a datos y testing menos completos "de serie".
- 👎 Sin ventaja que compense renunciar a la madurez de Spring Boot en un MVP.

## Decisión

Tres decisiones: arquitectura, lenguaje del backend y framework del frontend.

### Arquitectura — Opción A: Spring Boot como API REST + SPA

Una SPA sostiene las pantallas interactivas del MVP (constructor de grupos, editor semanal) sin forzar la arquitectura, y la API REST deja la puerta abierta a una futura app nativa sin reescribir el backend. Se descarta la Opción B pese a su simplicidad operativa porque las pantallas críticas del MVP son justamente las interactivas, y forzarlas con HTMX comprometería la UX recién validada con el club piloto. Se descarta Ktor (Opción C) por madurez frente a Spring Boot.

### Lenguaje del backend — Kotlin

El equipo será probablemente interno, lo que desactiva el argumento de "máximo pool de contratación" que favorecía a Java. Además Kotlin **no es un ecosistema aparte**: misma JVM, mismo Spring Boot, mismas librerías y build — un perfil JVM/Spring es productivo en Kotlin con una curva de días, no de meses. A favor de Kotlin pesan dos cosas concretas de este proyecto:

- **Seguridad frente a nulos en el sistema de tipos**: el modelo de datos (ADR-0002) está lleno de opcionalidad. Kotlin obliga a tratar esos casos en tiempo de compilación; en Java los NPE seguirían siendo una clase de bug viva.
- **Sinergia con la app móvil Android** (post-MVP): Kotlin es el lenguaje nativo de Android. El mismo equipo de backend comparte *lenguaje* con el futuro desarrollo Android — lo que reduce el salto, aunque no lo elimina (el desarrollo Android sigue siendo una disciplina propia).

### Framework del frontend — Angular

Opciones valoradas: **Angular** (elegida), **React** y **Vue**.

Angular gana por **afinidad con el equipo de backend**. Su modelo mental —inyección de dependencias, servicios, módulos, TypeScript de serie, orientación a objetos— es casi calcado al de Spring. Un desarrollador de Kotlin/Spring entiende y mantiene un frontend Angular con mucha menos fricción que uno React, y la opción de un equipo full-stack se vuelve realista. "Spring + Angular" es una pareja clásica precisamente por esa alineación.

Segundo motivo: **baterías incluidas**. Angular trae de fábrica router, cliente HTTP y formularios reactivos como piezas oficiales. Eso da un **conjunto de herramientas controlado** —menos fatiga de decisión, menos inconsistencia entre desarrolladores— frente a React, donde cada pieza (router, data-fetching, estado, formularios) es una elección à la carte. Los formularios reactivos + RxJS de Angular encajan particularmente bien con las pantallas de filtro en vivo del MVP (constructor de grupos).

Se descarta **React** pese a su mayor ecosistema y pool de contratación: ese pool pesa menos con un equipo interno (mismo razonamiento que llevó a Kotlin frente a Java), y no aporta la afinidad con Spring. Se descarta **Vue** por tener menos presencia en entornos *enterprise* y no ofrecer la afinidad estructural de Angular.

### Detalles

- **Backend**: Kotlin sobre Spring Boot (Spring da soporte oficial a Kotlin desde 2017). JVM LTS vigente.
- **Frontend**: Angular + TypeScript (TypeScript es nativo en Angular).
- **Comunicación**: API REST/JSON. Sin GraphQL en MVP (complejidad innecesaria para este alcance).

## Consecuencias

### Positivas

- El frontend (Angular) es mentalmente cercano al backend (Spring) → el equipo interno entiende el front con menos fricción y el full-stack es viable.
- **Tooling controlado**: las baterías incluidas de Angular acotan las herramientas y reducen la inconsistencia entre desarrolladores.
- Frontend preparado para la interactividad ya validada en los wireframes.
- API REST reutilizable si se decide construir app nativa post-MVP.
- **Seguridad frente a nulos de Kotlin**: menos NPE en un dominio con mucha opcionalidad.
- El equipo de backend comparte lenguaje (Kotlin) con la futura app Android.

### Negativas / coste asumido

- Dos bases de código, dos toolchains de build (Kotlin/Spring y TypeScript/Angular). Se asume conscientemente a cambio de la UX y de la coherencia con el equipo.
- Angular tiene una **curva de aprendizaje más empinada** y es más pesado que React — más ceremonia para un MVP pequeño. Se acepta a cambio de la afinidad con Spring y la estructura impuesta.
- Pool de contratación de Angular menor que el de React en términos absolutos — mitigado porque el equipo es interno.
- Más complejidad de despliegue que un monolito (se aborda en ADR-0006).

### Riesgos y mitigaciones

- **Un equipo muy pequeño se reparte mal entre dos stacks** → si el MVP lo arranca un único desarrollador, considerar perfiles full-stack o empezar por la API y un frontend mínimo; en el extremo, la Opción B (monolito) vuelve a la mesa.
- **Sobre-ingeniería con Angular en un MVP** → mantenerlo simple: componentes *standalone*, sin sobre-modularizar, sin librerías de estado pesadas hasta que haga falta.
- **El equipo final acaba siendo una agencia genérica de Java/React** → reconsiderar lenguaje y framework: Java y/o React seguirían siendo válidos sobre la misma arquitectura, sin tocar el resto del ADR.

## Notas

- Revisar esta decisión si el equipo final resultara ser una sola persona sin perfil frontend: en ese escenario la Opción B (monolito) vuelve a la mesa.
- Si en el futuro se quiere ir más allá de "compartir lenguaje" con Android, **Kotlin Multiplatform (KMP)** permitiría compartir código real (modelos, validaciones, lógica de dominio) entre backend y app móvil. No es una decisión del MVP, pero elegir Kotlin ahora la deja disponible.
- Las versiones concretas (JVM LTS, Spring Boot, Angular) se fijan al iniciar el desarrollo, no en este ADR.
