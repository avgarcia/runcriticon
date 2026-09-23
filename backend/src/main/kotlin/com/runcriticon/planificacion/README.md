# Módulo `planificacion`

Bounded context de **Planificación**. Planes semanales en borrador de un grupo, con sus sesiones y
personalizaciones por alumno como entidades hijas del agregado `WeeklyPlan`. El módulo arrancó con el alta y
listado del borrador; después se añadió el editor de sesión (tipo, volumen, ritmo y notas); luego la
publicación con snapshot de membresía congelado; y más tarde `setPersonalization`/`removePersonalization`
sobre el agregado — permitido tanto en `BORRADOR` como en `PUBLICADO`, a diferencia de las mutaciones de
sesión, que `publish()` congela. Por último se añadió el editor de ritmo relativo a marca en la UI (el dominio
y el contrato ya lo soportaban desde el arranque del módulo).

## Endpoints REST

Todos en `PlanController`, bajo `/api/planes`, con `@Authorize` en el handler; la RBAC contra la matriz y la
comprobación de nivel de objeto viven en el caso de uso (ADR-0009).

| Endpoint | Caso de uso | Permiso |
|---|---|---|
| `GET /api/planes` | `ListDraftPlansQuery` | `PLAN:LIST` |
| `POST /api/planes` | `CreateDraftPlanCommand` | `PLAN:CREATE` |
| `GET /api/planes/{planId}` | `GetPlanQuery` | `PLAN:LIST` |
| `POST /api/planes/{planId}/sesiones` | `AddSessionCommand` | `PLAN:UPDATE` |
| `PUT /api/planes/{planId}/sesiones/{sesionId}` | `UpdateSessionCommand` | `PLAN:UPDATE` |
| `DELETE /api/planes/{planId}/sesiones/{sesionId}` | `DeleteSessionCommand` | `PLAN:UPDATE` |
| `POST /api/planes/{planId}/publicacion` | `PublishPlanCommand` | `PLAN:PUBLISH` |
| `PUT /api/planes/{planId}/sesiones/{sesionId}/personalizaciones/{alumnoId}` | `SetPersonalizationCommand` | `PLAN:PERSONALIZE` |
| `DELETE /api/planes/{planId}/sesiones/{sesionId}/personalizaciones/{alumnoId}` | `RemovePersonalizationCommand` | `PLAN:PERSONALIZE` |

**`AccesoDenegado`** (ADR-0009 D15-D16): `PublishPlanCommand`, `SetPersonalizationCommand` y
`RemovePersonalizationCommand` lo publican con su propio `denegado(...)` privado; el resto de casos
de uso, vía `PlanificacionAccessAuditor`.

## Tablas

| Tabla | Migración de creación | Qué guarda |
|---|---|---|
| `plan_semanal`, `sesion`, `personalizacion` | `V202608130001` (+ `V202608130003` añade los campos del editor de sesión) | Agregado `WeeklyPlan` con sus sesiones y personalizaciones |
| `miembro_grupo`, `evento_procesado` | `V202608130002` | Proyección local de membresía de grupos (alumnos y entrenadores) e idempotencia de listeners |
| `miembro_grupo_version` | `V202608140002` | Order-guard por grupo de la proyección `miembro_grupo` (ver "Eventos consumidos") |
| `plan_snapshot_alumno` | `V202608140003` | Snapshot congelado de alumnos al publicar |

Este módulo **no tiene** hoy bean de métricas (`PlanificacionMetrics`) ni gauge `projection_lag_seconds` de
`miembro_grupo`, ni `RGPD.md` propio: la categorización y el borrado de sus tablas se describen en este README
(párrafo **RGPD** de "Publicación con snapshot congelado", abajo) y en el KDoc de `PlanificacionDeletionListener`.

## Publicación con snapshot congelado

`WeeklyPlan.publish()` pasa el plan a `PUBLICADO` y congela en `plan_snapshot_alumno` los alumnos resueltos en
ese momento (ADR-0002 D5): cambios posteriores de tags o de overrides no alteran un plan ya publicado. El
snapshot sale de `GroupMembersProjection.findStudents`, la misma proyección que se corrigió para publicar la
membresía completa del grupo (recálculo por cambio de tags) en vez de un delta parcial.

**Publicar congela el plan por completo**: una vez `PUBLICADO`, `addSession`/`updateSession`/`removeSession`
rechazan con `PlanAlreadyPublished` (409), igual que un segundo intento de publicar. El wireframe
(`docs/diseno/publicacion-plan.html`) promete que "cada cambio les llegará en tiempo real" tras publicar, pero
eso exigiría eventos de modificación y un consumidor en Seguimiento que no existen hoy, y rompería la
congelación que pide D5 — se deja fuera a propósito.

**Puerta fail-closed de ADR-0009 D9**: `ProjectionFreshnessJdbc.membersProjectionLagSeconds()` mide
`now() - MIN(publication_date)` de las publicaciones **pendientes** (`completion_date IS NULL`) en el outbox
(`event_publication`) cuyo `event_type` es el de `MembresiaDeGrupoCambiada`; 0 si no hay ninguna. Publicar
rechaza con `ProjectionStale` (503) si el lag llega a 60 s. Se filtra por `event_type` (nombre de clase del
evento, estable) y no por `listener_id` (formato interno de Spring Modulith sin garantía documentada) — un
literal de `listener_id` mal adivinado haría que la puerta fallara **abierta** en silencio, justo lo contrario
de lo que exige D9; `PublishPlanIntegrationTest` verifica el valor real contra Postgres.

**El evento `PlanPublicado`** es auto-contenido por exigencia expresa de ADR-0007 D15: lleva el snapshot
completo de alumnos y las sesiones de la semana embebidas (`PublishedSession`, en `api/` — no en `api/events/`,
porque no es en sí mismo un `IntegrationEvent` y `DomainEventArchTest`/`IntegrationEventArchTest` exigen que
todo lo que resida en `api.events` lo implemente).

**RGPD**: `plan_snapshot_alumno` se borra físicamente en `PlanificacionDeletionListener`, mismo criterio que
`personalizacion` y `miembro_grupo` en este módulo — sigue ADR-0014 D5/D6 (categoría 1, PII primaria → borrado
físico). ADR-0004 D16 fijaba antes una categorización propia que contradecía a D5/D6 en estas mismas tablas;
corregido en la revisión de D16 para que remita a ADR-0014 en vez de duplicarla.

**Fuera de este ticket**: el wireframe de publicación lleva un switch "Avisar por email a los alumnos" que no
se construye — `EmailSender` es interno a `identidad` (no es named interface), sus métodos son uno por tipo de
correo, este módulo no tiene ningún email de alumno (solo `persona_id`), y ADR-0007 fija un DAG donde
"Identidad y acceso → publica eventos (no consume de nadie)", así que un listener ahí también costaría revisar
el ADR. Ninguno de los criterios de aceptación de la publicación lo pide. Pendiente de decidir dónde vive la capacidad
de notificar cuando el hecho lo produce un módulo distinto de `identidad`.

## Editor de sesión — recorte deliberado de campos

`Session` solo modela `tipo`, `volumen` (distancia **o** tiempo, nunca los dos), `ritmo` y `notas` — los cuatro
campos que pide el AC. El wireframe hi-fi de referencia (`docs/diseno/editor-sesion.html`) añade repeticiones,
recuperación, calentamiento y vuelta a la calma, pero **la tarjeta de la vista semanal**
(`docs/diseno/editor-plan-semanal.html`) solo pinta esos cuatro campos y mete la estructura de series como texto
libre en las notas (p. ej. "8×400 m, recuperación de 200 m entre series") — los dos mockups se contradicen entre
sí, y se sigue el que coincide con el AC. Sin punto de entrada al side sheet animado del wireframe tampoco: el
frontend usa una rejilla de 7 días con el editor como diálogo (`plan-detail.component.ts` +
`session-editor-dialog.component.ts`), no la vista semanal completa, que no existe todavía y no tiene ticket que
la cubra.

Invariantes nuevos en `WeeklyPlan`/`Session` (del editor de sesión):
- **Una sesión por día y plan** (`sesion_plan_dia_uk`, `UNIQUE (plan_id, dia)`) — `WeeklyPlan.addSession` la
  rechaza en dominio antes de tocar la BD, `PlanificacionError.DuplicateSessionDay` (409).
- **El día debe caer dentro de la semana del plan** (`week`..`week+6`).
- **`DESCANSO` no admite volumen ni ritmo** — `Session.create` lo rechaza.
- **El día de una sesión no se edita**: `UpdateSessionCommand`/`PUT .../sesiones/{sesionId}` no lo aceptan; mover
  una sesión de día es borrarla y crear otra.
- **Ritmo `RELATIVO`**: el dominio lo soporta desde el arranque del módulo; el editor de sesión inicial solo
  escribía `ABSOLUTO` (el criterio de aceptación correspondiente lo limitaba a ritmo absoluto) y más tarde se
  añadió el editor de ritmo relativo a marca (`session-editor-dialog.component.ts`).

## Eventos publicados

| Evento | Cuándo | Schema | Consumido por |
|---|---|---|---|
| `PlanPublicado` v1 | Al publicar un plan; lleva también las personalizaciones creadas antes de publicar, que no tienen evento propio | `schemas/planificacion/plan-publicado-v1.json` | `seguimiento.ResolvedPlanProjectionListener` |
| `PersonalizacionAplicada` v1 | Al aplicar/sustituir una personalización sobre un plan ya `PUBLICADO` | `schemas/planificacion/personalizacion-aplicada-v1.json` | `seguimiento.PersonalizationProjectionListener` |
| `PersonalizacionRetirada` v1 | Al retirar una personalización de un plan ya `PUBLICADO` | `schemas/planificacion/personalizacion-retirada-v1.json` | `seguimiento.PersonalizationProjectionListener` |
| `AccesoDenegado` v1 (`shared.api.events`) | Rechazo de autorización en un caso de uso (ver "Endpoints REST") | `schemas/shared/acceso-denegado-v1.json` | `auditoria` (`AuditEventListener`) |

## Eventos consumidos

| Evento | De | Alimenta | Consumido por |
|---|---|---|---|
| `MembresiaDeGrupoCambiada` v1 | `club_taxonomia` | `miembro_grupo` (rol ALUMNO, reemplazo mayorista del snapshot) | `GroupMembersProjectionListener` |
| `EntrenadorAsignadoAGrupo` v1 | `club_taxonomia` | `miembro_grupo` (rol ENTRENADOR) | `GroupMembersProjectionListener` |
| `EntrenadorEliminadoDeGrupo` v1 | `club_taxonomia` | `miembro_grupo` (borra la fila) | `GroupMembersProjectionListener` |
| `AlumnoEliminado` v1 | `identidad` | Borrado RGPD (personalizaciones, `miembro_grupo`) | `PlanificacionDeletionListener` |
| `EntrenadorEliminado` v1 | `identidad` | Borrado RGPD (planes enteros, `miembro_grupo`) | `PlanificacionDeletionListener` |

> `MembresiaDeGrupoCambiada` sustituye a los antiguos `AlumnoAsignadoAGrupo`/`AlumnoEliminadoDeGrupo`: aquellos
> solo cubrían la excepción manual, nunca la pertenencia por tags. El nuevo evento lleva el snapshot
> **completo** de alumnos del grupo (prerrequisito de la publicación con snapshot de membresía), y
> `GroupMembersProjectionListener` lo
> aplica como reemplazo mayorista, no como delta — `miembro_grupo_version` guarda el order-guard por grupo,
> aparte de `miembro_grupo` (un snapshot que deja el grupo vacío no puede perder la referencia de orden).

## Recorte deliberado: `CoachGroupLookup` sin puerta de proyección `stale`

`CoachGroupLookup.isCoachOfGroup` comprueba la relación entrenador↔grupo contra `miembro_grupo` con una
consulta directa, **sin** calcular `projection_lag_seconds` ni aplicar la política fail-closed de ADR-0009 D9.
Es correcto para crear un borrador (el criterio de aceptación tolera unos segundos de proyección desactualizada)
y para publicar (la autorización de "¿eres entrenador de este grupo?" no depende de que la lista de *alumnos*
esté al día). La puerta de frescura de la publicación vive en `ProjectionFreshness`, aparte, y mide la
proyección de **alumnos**, no la de entrenadores.

## Otros huecos conocidos, no cerrados en este ticket

- La pantalla de planes en borrador (`/planificacion/grupos/:grupoId/planes`) no tiene todavía un punto de
  entrada enlazado desde el listado de grupos de `club_taxonomia`: se navega por URL directa. Enlazarla es
  trabajo de UX, no de arranque de módulo.
- El switch de email al publicar (ver arriba): ticket propio pendiente.
