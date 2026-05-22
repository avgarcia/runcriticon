# Plan de implementación del MVP — Runcriticon

> Documento vivo. Recoge **cómo se construye** el MVP: el orden, las fases y los hitos. **No fija fechas** — no hay fecha objetivo; el plan avanza por hitos y el equipo estima la cadencia al ejecutarlo. Se apoya en las decisiones ya tomadas: los 15 ADR (`docs/adr/`), el `backlog.md` y el discovery.

## Punto de partida

- Proyecto **greenfield**: el repositorio contiene toda la documentación (discovery, wireframes, 15 ADR, planes de formación, glosario, guía de estructura de módulo) y **ningún código de aplicación**.
- Las **decisiones de arquitectura están tomadas** — ADR-0001 a ADR-0015.
- El **alcance del MVP** son las funcionalidades MUST del `backlog.md`, para un único club.
- **Equipo**: 4 personas, constituido y disponible. **Sin fecha objetivo** — el plan avanza por **hitos**.

## Principios de construcción

Decididos con negocio / jefe de equipo; gobiernan todo el plan:

1. **Esqueleto andante primero.** La primera meta no es una funcionalidad, sino una rebanada de extremo a extremo que se despliega sola. Desriesga la infraestructura y la integración desde el día 1.
2. **Camino crítico primero (*journey-first*).** Tras el esqueleto, se construye el *loop* entrenador↔alumno de punta a punta —lo mínimo de cada módulo para ese ciclo— antes de rellenar lo secundario.
3. **Kanban.** Flujo continuo con límites de WIP, sin *sprints*. **Demo quincenal con los usuarios finales** del club piloto, que dan feedback y validan si el flujo encaja en su día a día.
4. **El *loop* central es intocable.** Válvula de escape si hay que aligerar: lo secundario de cada módulo; lo primero aplazable es el panel de alertas y la vista de salud del club.
5. **La arquitectura no se renegocia sobre la marcha.** Los ADR son la referencia; la guía [`docs/arquitectura/estructura-de-un-modulo.md`](arquitectura/estructura-de-un-modulo.md) fija cómo se construye cada módulo.

## Prerrequisitos

Antes de la primera línea de código de funcionalidad:

- **Aprobar los ADR**: el equipo, ya constituido, revisa y aprueba los 15 ADR — pasan de *Propuesto* a *Aceptado*.
- **Aprovisionar la infraestructura** con Terraform (ADR-0006): cuenta AWS, región `eu-west-1` (ADR-0014), RDS PostgreSQL, App Runner, entornos `staging` y `producción`.
- **Formación en paralelo**: el equipo arranca los planes de `docs/formacion/` mientras construye — continuo, no bloqueante.
- Los **pendientes jurídicos de RGPD** (ADR-0014) no bloquean empezar a programar, pero **sí deben estar cerrados antes de la beta** con datos reales.

## Fases

### Fase 0 — Esqueleto andante

La rebanada de extremo a extremo, sin funcionalidades de negocio:

- Monorepo (ADR-0001); *build* de backend (Kotlin/Spring Boot) y frontend (Angular).
- *Pipeline* de CI/CD (ADR-0010): *quality gates*, imagen a GHCR, despliegue automático a `staging`.
- Infraestructura como código (Terraform) desplegada.
- Los **4 módulos vacíos** montados con Spring Modulith; estructura hexagonal según la guía de módulo.
- Primera migración Flyway; un esquema por módulo (ADR-0004).
- **Login mínimo** funcionando (sienta las bases de ADR-0003) y una pantalla trivial.

**En paralelo** — Claude produce el **diseño visual**: *mockups* hi-fi en HTML/CSS con estilo Material (ADR-0012) de las pantallas del camino crítico, que alimentan el frontend.

**Hito H0:** un *commit* llega solo a `staging`, se puede iniciar sesión y se ve una pantalla. El camino del *commit* al despliegue queda probado.

### Fase 1 — Camino crítico (el *loop* entrenador↔alumno)

Lo **mínimo** de cada módulo para que funcione el ciclo completo:

- **Identidad y acceso**: invitaciones, activación, magic link, roles; el primer admin por semilla (ADR-0003).
- **Club y taxonomía**: el club, el editor de taxonomía (TagKey/TagValue), alta de alumnos y entrenadores, grupos como consulta sobre tags (ADR-0002); alta de alumnos por delegación a entrenadores.
- **Planificación**: editor de plan semanal, sesiones, publicación a un grupo con *snapshot* de membresía.
- **Seguimiento**: el alumno ve su plan y **reporta una sesión**.

Cada funcionalidad pasa los *quality gates* de ADR-0010. Comunicación entre módulos *events-first* desde el primer evento.

**Hito H1 — arranque de la BETA:** el *loop* crear plan → publicar → ejecutar → reportar funciona de extremo a extremo en `producción` y pasa un *smoke test*. **El club piloto empieza a usar Runcriticon de verdad**; las funcionalidades siguientes llegan con el club ya dentro.

### Fase 2 — Rellenar (funcionalidades secundarias)

Con el club piloto ya usando la app, se completan las funcionalidades secundarias de cada módulo en flujo continuo (Kanban), priorizadas con el feedback de las demos quincenales. Aquí entran, entre otras, el panel de alertas y la vista de salud del club — que son también la **válvula de escape** si hay que aligerar. Claude produce por delante el diseño de las pantallas nuevas.

### Fase 3 — Endurecimiento y consolidación

Transversal, se intensifica hacia el final:

- Tests de **carga/rendimiento** para validar los NFR de ADR-0001 (p95 de latencia).
- **DAST** de seguridad y revisión de accesibilidad.
- Observabilidad afinada con datos reales (ADR-0011).
- Cierre confirmado de los pendientes jurídicos de RGPD.

## Hitos

| Hito | Significado |
|------|-------------|
| **H0 — Esqueleto andante** | Un *commit* se despliega solo a `staging`; login y una pantalla. |
| **H1 — Beta** | El *loop* entrenador↔alumno funciona en `producción`; el club piloto empieza a usarlo. |
| **H2 — MVP completo** | Todas las funcionalidades MUST del `backlog.md` entregadas. |
| **H3 — Consolidación** | NFR validados, seguridad y accesibilidad revisadas, RGPD cerrado. |

> El plan fija el **orden y los hitos**, no fechas. La estimación en calendario, si se necesita, la hace el equipo al ejecutar.

## Seguimiento y feedback

- **Kanban** con límites de WIP; tablero visible.
- **Demo quincenal con los usuarios finales** del club piloto: dan feedback y, según se añaden funcionalidades, trabajan con la app para comprobar si el flujo encaja en su día a día.
- El feedback de las demos prioriza la cola de la Fase 2.

## Criterio de éxito de la beta

El éxito de la beta se mide por el **grado de adopción real**: que entrenadores y alumnos usen Runcriticon **en su día a día**, en lugar de las herramientas que usaban antes (Excel, WhatsApp…). Una adopción real significa que la aplicación está bien construida y que los usuarios la encuentran útil — valida la hipótesis central del discovery.

## Lo que NO entra en el MVP

Recordatorio (ver `backlog.md` y ADR-0015): app móvil nativa, login con Google, importación masiva de alumnos, ritmos relativos en la UI, versionado de API, caché, i18n. Son evoluciones posteriores, no olvidos.

## Riesgos de la implementación

- **Equipo nuevo en un stack con curva** (hexagonal, DDD, events-first) → planes de formación en paralelo, la guía de estructura de módulo como referencia, y empezar por el esqueleto antes que por funcionalidades.
- **Consistencia eventual mal gestionada** (events-first) → un módulo de ejemplo bien hecho como patrón, tests de las proyecciones, consumidores idempotentes.
- **Alcance que se infla** → la válvula de escape del principio 4; el *journey-first* permite recortar lo secundario sin tocar el *loop*.
- **La beta depende del email** (R10) y de los pendientes de RGPD → cerrar ambos antes del Hito H1.
