# ADR-0015 — Temas de arquitectura aplazados fuera del MVP

- **Estado**: Propuesto
- **Fecha**: 2026-05-22
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0001 (stack y NFR), ADR-0003 (sesión / Redis), ADR-0008 (lenguaje ubicuo), ADR-0012 (frontend), `docs/notas/tipos-de-base-de-datos.md`

## Contexto y problema

La auditoría de coherencia de los ADR identificó varios temas de arquitectura que **se han dejado deliberadamente fuera del MVP**. El riesgo de no registrarlos es que más adelante se reabran como si fueran **olvidos** — o, peor, que alguien los "resuelva" por su cuenta sin ver que la omisión era intencionada.

Este ADR los recoge como **no-decisiones conscientes**: qué se aplaza, por qué, cuál es la situación por defecto mientras tanto y cuándo conviene reabrir cada uno.

## Decisión

Se deja constancia de los siguientes temas como **aplazados de forma consciente**. No son huecos: son alcance recortado a propósito para un MVP mono-club con un equipo de 4.

### Versionado de la API

- **Por qué se aplaza**: en el MVP hay **un único cliente** (la SPA Angular), en un monorepo, con el contrato OpenAPI compartido (ADR-0001). No hay consumidores externos a los que versionar.
- **Situación por defecto**: el contrato evoluciona junto al código, en el mismo PR.
- **Cuándo reabrir**: cuando aparezca un cliente externo (app nativa, integración de terceros).

### Estrategia de caché

- **Por qué se aplaza**: la carga del MVP es baja (~550 usuarios, ADR-0001); PostgreSQL bien indexado va sobrado.
- **Situación por defecto**: sin caché de aplicación. Redis ya está anticipado como adición futura (sesión compartida en ADR-0003; ver la nota de tipos de base de datos).
- **Cuándo reabrir**: cuando una señal real de rendimiento lo justifique, o al escalar a varias instancias.

### Internacionalización (i18n)

- **Por qué se aplaza**: el MVP es un club en España; el producto es en castellano, igual que el lenguaje ubicuo (ADR-0008).
- **Situación por defecto**: textos en castellano, sin capa de i18n.
- **Cuándo reabrir**: si llega un club fuera de España o se requiere soporte multi-idioma.

### Objetivo formal de accesibilidad (WCAG)

- **Por qué se aplaza**: no hay un objetivo formal de conformidad WCAG fijado para el MVP.
- **Situación por defecto**: se aprovecha la **accesibilidad de serie de Angular Material** (ADR-0012) — no se parte de cero —, pero sin auditoría ni nivel de conformidad comprometido.
- **Cuándo reabrir**: si hay un requisito legal o contractual de accesibilidad.

### Soporte de zonas horarias

- **Por qué se aplaza**: el MVP es un club en una única zona horaria (España).
- **Situación por defecto**: las fechas y horas se almacenan de forma consistente (**UTC** en la base de datos) y se presentan en la zona del club; no se construye soporte multi-zona.
- **Cuándo reabrir**: multi-club con clubes en husos horarios distintos.

## Consecuencias

### Positivas

- Estos temas dejan de ser ambigüedades: queda escrito que están fuera del MVP **a propósito**.
- Cada uno tiene un criterio claro de reapertura — no se reabren por capricho ni se olvidan.
- El equipo no invierte esfuerzo del MVP en problemas que el MVP no tiene.

### Negativas / coste asumido

- Hay que **respetar la situación por defecto**: p. ej. almacenar siempre las fechas en UTC desde el día 1, aunque no haya multi-zona, para no tener que retrofitear.

### Riesgos y mitigaciones

- **Que un tema aplazado se reabra como urgencia tardía** → cada entrada tiene un disparador de reapertura explícito; revisarlos al planificar cada evolución.
- **Que la "situación por defecto" se incumpla** (p. ej. fechas en hora local) → revisión de código; tenerlo presente en la guía de estructura de módulo.

## Notas

- Este ADR es una **lista viva**: se amplía si aparecen nuevos temas que se decida aplazar.
- Las decisiones de alcance ya recogidas en otros ADR no se repiten aquí: el "sin GraphQL" está en ADR-0001; la app móvil nativa fuera del MVP, en `vision.md` y ADR-0001.
