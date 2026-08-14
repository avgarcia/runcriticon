# Módulo `planificacion`

Bounded context de **Planificación**. Planes semanales en borrador de un grupo, con sus sesiones y
personalizaciones por alumno como entidades hijas del agregado `WeeklyPlan`. LAL-114 arrancó el módulo (alta y
listado del borrador); LAL-24 añade el editor de sesión (tipo, volumen, ritmo y notas); LAL-25 añade la
publicación con snapshot de membresía congelado. La personalización real (LAL-26) y el ritmo relativo en la UI
(LAL-27) llegan con sus propias historias.

## Publicación con snapshot congelado (LAL-25)

`WeeklyPlan.publish()` pasa el plan a `PUBLICADO` y congela en `plan_snapshot_alumno` los alumnos resueltos en
ese momento (ADR-0002 D5): cambios posteriores de tags o de overrides no alteran un plan ya publicado. El
snapshot sale de `GroupMembersProjection.findStudents`, la misma proyección que corrigió LAL-117.

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
`personalizacion` y `miembro_grupo` en este módulo. Diverge de ADR-0004 D16, que pide anonimizar (no borrar)
los datos derivados sin PII directa — pero ese mismo D16 también pide anonimizar `personalizacion`, que ya se
borra físicamente desde antes de este ticket; seguir D16 solo en la tabla nueva habría dejado dos criterios
distintos dentro del mismo módulo. Pendiente: ticket para reconciliar ADR-0004 D16 con ADR-0014 D6 (que si
categoriza el borrado de PII primaria como físico) y decidir un criterio único.

**Fuera de este ticket**: el wireframe de publicación lleva un switch "Avisar por email a los alumnos" que no
se construye — `EmailSender` es interno a `identidad` (no es named interface), sus métodos son uno por tipo de
correo, este módulo no tiene ningún email de alumno (solo `persona_id`), y ADR-0007 fija un DAG donde
"Identidad y acceso → publica eventos (no consume de nadie)", así que un listener ahí también costaría revisar
el ADR. Ninguno de los AC de LAL-25 lo pide. Pendiente: ticket propio que decida dónde vive la capacidad de
notificar cuando el hecho lo produce un módulo distinto de `identidad`.

## Editor de sesión (LAL-24) — recorte deliberado de campos

`Session` solo modela `tipo`, `volumen` (distancia **o** tiempo, nunca los dos), `ritmo` y `notas` — los cuatro
campos que pide el AC. El wireframe hi-fi de referencia (`docs/diseno/editor-sesion.html`) añade repeticiones,
recuperación, calentamiento y vuelta a la calma, pero **la tarjeta de la vista semanal**
(`docs/diseno/editor-plan-semanal.html`) solo pinta esos cuatro campos y mete la estructura de series como texto
libre en las notas (p. ej. "8×400 m, recuperación de 200 m entre series") — los dos mockups se contradicen entre
sí, y se sigue el que coincide con el AC. Sin punto de entrada al side sheet animado del wireframe tampoco: el
frontend usa una rejilla de 7 días con el editor como diálogo (`plan-detail.component.ts` +
`session-editor-dialog.component.ts`), no la vista semanal completa, que no existe todavía y no tiene ticket que
la cubra.

Invariantes nuevos en `WeeklyPlan`/`Session` (LAL-24):
- **Una sesión por día y plan** (`sesion_plan_dia_uk`, `UNIQUE (plan_id, dia)`) — `WeeklyPlan.addSession` la
  rechaza en dominio antes de tocar la BD, `PlanificacionError.DuplicateSessionDay` (409).
- **El día debe caer dentro de la semana del plan** (`week`..`week+6`).
- **`DESCANSO` no admite volumen ni ritmo** — `Session.create` lo rechaza.
- **El día de una sesión no se edita**: `UpdateSessionCommand`/`PUT .../sesiones/{sesionId}` no lo aceptan; mover
  una sesión de día es borrarla y crear otra.
- **Ritmo `RELATIVO` en el contrato, no en la UI**: el dominio lo soporta desde LAL-114, pero el editor de
  LAL-24 solo escribe `ABSOLUTO` (AC2) — el conmutador y la caja de privacidad del wireframe llegan con LAL-27.

## Eventos publicados

| Evento | Cuándo | Consumido por |
|---|---|---|
| `PlanPublicado` v1 | Al publicar un plan (LAL-25) | Ningún consumidor todavía — Seguimiento lo consumirá para `plan_resuelto_por_alumno` cuando exista el módulo |

## Eventos consumidos

| Evento | De | Alimenta | Consumido por |
|---|---|---|---|
| `MembresiaDeGrupoCambiada` v1 | `club_taxonomia` | `miembro_grupo` (rol ALUMNO, reemplazo mayorista del snapshot) | `GroupMembersProjectionListener` |
| `EntrenadorAsignadoAGrupo` v1 | `club_taxonomia` | `miembro_grupo` (rol ENTRENADOR) | `GroupMembersProjectionListener` |
| `EntrenadorEliminadoDeGrupo` v1 | `club_taxonomia` | `miembro_grupo` (borra la fila) | `GroupMembersProjectionListener` |
| `AlumnoEliminado` v1 | `identidad` | Borrado RGPD (personalizaciones, `miembro_grupo`) | `PlanificacionDeletionListener` |
| `EntrenadorEliminado` v1 | `identidad` | Borrado RGPD (planes enteros, `miembro_grupo`) | `PlanificacionDeletionListener` |

> `MembresiaDeGrupoCambiada` sustituye a los antiguos `AlumnoAsignadoAGrupo`/`AlumnoEliminadoDeGrupo` (LAL-94):
> aquellos solo cubrían la excepción manual, nunca la pertenencia por tags. El nuevo evento lleva el snapshot
> **completo** de alumnos del grupo (LAL-117, prerrequisito de LAL-25), y `GroupMembersProjectionListener` lo
> aplica como reemplazo mayorista, no como delta — `miembro_grupo_version` guarda el order-guard por grupo,
> aparte de `miembro_grupo` (un snapshot que deja el grupo vacío no puede perder la referencia de orden).

## Recorte deliberado: `CoachGroupLookup` sin puerta de proyección `stale`

`CoachGroupLookup.isCoachOfGroup` comprueba la relación entrenador↔grupo contra `miembro_grupo` con una
consulta directa, **sin** calcular `projection_lag_seconds` ni aplicar la política fail-closed de ADR-0009 D9.
Es correcto para AC4 de LAL-114 (crear un borrador tolera unos segundos de proyección desactualizada) y para
publicar (la autorización de "¿eres entrenador de este grupo?" no depende de que la lista de *alumnos* esté al
día). La puerta de frescura de LAL-25 vive en `ProjectionFreshness`, aparte, y mide la proyección de
**alumnos**, no la de entrenadores.

## Otros huecos conocidos, no cerrados en este ticket

- La pantalla de planes en borrador (`/planificacion/grupos/:grupoId/planes`) no tiene todavía un punto de
  entrada enlazado desde el listado de grupos de `club_taxonomia`: se navega por URL directa. Enlazarla es
  trabajo de UX, no de arranque de módulo.
- El bloque "Personalizaciones" del wireframe hi-fi del editor de sesión (contador + avatares + "Gestionar →") no
  se construye: es explícitamente alcance de LAL-26.
- El switch de email al publicar y la reconciliación ADR-0004 D16 / ADR-0014 D6 (ver arriba): ambos con ticket
  propio pendiente.
