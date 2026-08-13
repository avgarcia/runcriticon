# Módulo `planificacion`

Bounded context de **Planificación**. Planes semanales en borrador de un grupo, con sus sesiones y
personalizaciones por alumno como entidades hijas del agregado `WeeklyPlan`. Arranque del módulo (LAL-114): solo
el alta y el listado del borrador — el editor de sesión (LAL-24), la publicación con snapshot (LAL-25) y la
personalización real (LAL-26) llegan con sus propias historias.

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
