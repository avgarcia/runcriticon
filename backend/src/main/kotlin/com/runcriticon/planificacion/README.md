# Módulo `planificacion`

Bounded context de **Planificación**. Planes semanales en borrador de un grupo, con sus sesiones y
personalizaciones por alumno como entidades hijas del agregado `WeeklyPlan`. LAL-114 arrancó el módulo (alta y
listado del borrador); LAL-24 añade el editor de sesión (tipo, volumen, ritmo y notas). La publicación con
snapshot (LAL-25), la personalización real (LAL-26) y el ritmo relativo en la UI (LAL-27) llegan con sus propias
historias.

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

## Eventos consumidos

| Evento | De | Alimenta | Consumido por |
|---|---|---|---|
| `AlumnoAsignadoAGrupo` v1 | `club_taxonomia` | `miembro_grupo` (rol ALUMNO) | `GroupMembersProjectionListener` |
| `AlumnoEliminadoDeGrupo` v1 | `club_taxonomia` | `miembro_grupo` (borra la fila) | `GroupMembersProjectionListener` |
| `EntrenadorAsignadoAGrupo` v1 | `club_taxonomia` | `miembro_grupo` (rol ENTRENADOR) | `GroupMembersProjectionListener` |
| `EntrenadorEliminadoDeGrupo` v1 | `club_taxonomia` | `miembro_grupo` (borra la fila) | `GroupMembersProjectionListener` |
| `AlumnoEliminado` v1 | `identidad` | Borrado RGPD (personalizaciones, `miembro_grupo`) | `PlanificacionDeletionListener` |
| `EntrenadorEliminado` v1 | `identidad` | Borrado RGPD (planes enteros, `miembro_grupo`) | `PlanificacionDeletionListener` |

> Los alumnos se proyectan en `miembro_grupo` ya desde este ticket, aunque ningún caso de uso los lea todavía —
> es la base de la que LAL-25 sacará el snapshot de membresía al publicar.

## Recorte deliberado: `CoachGroupLookup` sin puerta de proyección `stale`

`CoachGroupLookup.isCoachOfGroup` comprueba la relación entrenador↔grupo contra `miembro_grupo` con una
consulta directa, **sin** calcular `projection_lag_seconds` ni aplicar la política fail-closed de ADR-0009 D9
(> 60 s → rechazar). Es correcto para AC4 de LAL-114 (crear un borrador tolera unos segundos de proyección
desactualizada), pero **no** para publicar un plan de verdad: LAL-25 tendrá que añadir esa puerta antes de
usar esta misma proyección para autorizar la publicación, donde una decisión equivocada sí tiene consecuencia
real (un plan publicado al grupo equivocado).

## Otros huecos conocidos, no cerrados en este ticket

- Sin test de integración dedicado para el flujo de borrado RGPD (`PlanificacionDeletionListener`) más allá de
  los tests unitarios de `PlanificacionErasureJdbc` implícitos en el resto de la suite — a añadir si se detecta
  necesario en revisión.
- La pantalla de planes en borrador (`/planificacion/grupos/:grupoId/planes`) no tiene todavía un punto de
  entrada enlazado desde el listado de grupos de `club_taxonomia`: se navega por URL directa. Enlazarla es
  trabajo de UX, no de arranque de módulo.
- Sin guarda de "plan ya publicado" en `addSession`/`updateSession`/`removeSession` (LAL-24): `PlanStatus.PUBLICADO`
  es hoy inalcanzable (no existe `publish()`), así que esa rama la añade LAL-25 junto con el estado que la hace
  posible — no se declara antes para no dejar un `when` con una rama muerta.
- El bloque "Personalizaciones" del wireframe hi-fi del editor de sesión (contador + avatares + "Gestionar →") no
  se construye: es explícitamente alcance de LAL-26.
