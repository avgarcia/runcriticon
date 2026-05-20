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
- **Quién construye el MVP está aún por decidir** → el stack debe ser *mainstream* y bien documentado, para no depender de perfiles raros ni de un proveedor concreto.
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

### Opción C — Kotlin + Ktor + React

Backend en Kotlin (lenguaje JVM moderno) con el framework Ktor + frontend React.

- 👍 Kotlin es más conciso y agradable que Java; sigue siendo JVM.
- 👎 Menor pool de contratación que Java/Spring — arriesgado con el equipo sin decidir.
- 👎 Ktor es menos maduro y con menos ecosistema que Spring Boot.

## Decisión

**Opción A: Spring Boot (Java) como API REST + React (TypeScript) como SPA.**

Es el stack más *mainstream* dentro del ecosistema JVM elegido, lo que minimiza el riesgo de "equipo por decidir": cualquier perfil Java y cualquier perfil React encajan sin curva rara. React sostiene las pantallas interactivas del MVP sin forzar la arquitectura, y la API REST deja la puerta abierta a una futura app nativa sin reescribir el backend.

Se descarta la Opción B pese a su simpli­cidad operativa porque las pantallas críticas del MVP (constructor de grupos, editor semanal) son justamente las interactivas, y forzarlas con HTMX comprometería la UX que se acaba de validar con el club piloto.

Detalles de la decisión:

- **Backend**: Java (LTS vigente) + Spring Boot. Kotlin queda permitido como evolución futura (interopera en la misma JVM) pero el MVP se escribe en Java por contratación.
- **Frontend**: React + TypeScript. TypeScript no es opcional — el tipado reduce errores y ayuda a un equipo que entra frío.
- **Comunicación**: API REST/JSON. Sin GraphQL en MVP (complejidad innecesaria para este alcance).

## Consecuencias

### Positivas

- Contratación amplia tanto de backend como de frontend.
- Frontend preparado para la interactividad ya validada en los wireframes.
- API reutilizable si se decide construir app nativa post-MVP.

### Negativas / coste asumido

- Dos bases de código, dos toolchains de build, potencialmente dos perfiles. Se asume conscientemente a cambio de la UX y la contratación.
- Más complejidad de despliegue que un monolito (se aborda en ADR-0006).

### Riesgos y mitigaciones

- **Un equipo muy pequeño se reparte mal entre dos stacks** → si el MVP lo arranca un único desarrollador, considerar perfiles full-stack JS/Java o empezar por la API y un frontend mínimo.
- **Sobre-ingeniería del frontend** → mantener React simple en MVP: sin librería de estado global pesada hasta que haga falta; routing y fetching estándar.

## Notas

- Revisar esta decisión si el equipo final resultara ser una sola persona sin perfil frontend: en ese escenario la Opción B vuelve a la mesa.
- Las versiones concretas (Java LTS, Spring Boot, React) se fijan al iniciar el desarrollo, no en este ADR.
