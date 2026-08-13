# Módulo `club_taxonomia`

Bounded context de **Club y taxonomía**. Tags del club, proyección local de alumnos y entrenadores (alimentada por eventos de `identidad`), grupos como consultas sobre tags con excepciones manuales, y la asignación de entrenadores a grupos.

Consume de `identidad` (`AlumnoInvitado`, `EntrenadorInvitado`, `AlumnoActivado`, `EntrenadorActivado`, `AlumnoEliminado`, `EntrenadorEliminado`) para mantener su proyección local de personas. Desde LAL-94 también **publica** eventos propios.

## Eventos publicados

| Evento | Cuándo | Schema | Consumido por |
|---|---|---|---|
| `AlumnoAsignadoAGrupo` v1 | Un alumno entra en un grupo por excepción manual (`OverrideGroupMembershipCommand`, `included = true`) | `schemas/club_taxonomia/alumno-asignado-a-grupo-v1.json` | Planificación (pendiente de construir) |
| `AlumnoEliminadoDeGrupo` v1 | Un alumno sale de un grupo por excepción manual (`OverrideGroupMembershipCommand`, `included = false`) | `schemas/club_taxonomia/alumno-eliminado-de-grupo-v1.json` | Planificación (pendiente de construir) |
| `EntrenadorAsignadoAGrupo` v1 | Un entrenador queda vinculado a un grupo (`AssignCoachToGroupCommand`) | `schemas/club_taxonomia/entrenador-asignado-a-grupo-v1.json` | Planificación (pendiente de construir) |
| `EntrenadorEliminadoDeGrupo` v1 | Un entrenador queda desvinculado de un grupo (`UnassignCoachFromGroupCommand`) | `schemas/club_taxonomia/entrenador-eliminado-de-grupo-v1.json` | Planificación (pendiente de construir) |

> Payload mínimo: los 6 campos obligatorios + `traceparent` + `groupId`. Sin `name`/`email` — el consumidor ya los
> tiene por los eventos de `identidad`; aquí solo hace falta el par `(groupId, aggregateId)` para mantener una
> proyección de membresía.

> El contrato de cada evento lo valida el job `contractTest` contra su JSON Schema.
> Un cambio rompiente exige `…-v2.json` + dual-publishing 4 semanas (ver `schemas/README.md`).

### Qué NO dispara evento hoy (recorte deliberado, LAL-94)

La pertenencia de un alumno a un grupo es **calculada** (tags + excepción manual, ADR-0002 D1), no una fila guardada
— a diferencia de `grupo_entrenador`, que sí lo es. El camino más común de entrada a un grupo es un cambio de tags, y
ese camino **no publica nada** todavía:

- `ClearGroupMembershipOverrideCommand` (quitar la excepción manual): el resultado depende del filtro de tags
  vigente en ese momento, no lo determina el comando por sí solo.
- La creación de un grupo (`CreateGroupCommand`): los miembros iniciales que ya cumplen el filtro no generan evento.
- Cualquier cambio de tags de un alumno (`application/usecases/studenttags/`): puede meter o sacar alumnos de
  cualquier grupo cuyo filtro toque esos tags, y ese recálculo no está implementado.

Un consumidor que construya su proyección solo con estos cuatro eventos verá **exclusivamente las excepciones
manuales**, no la pertenencia completa de un grupo. Cerrarlo requiere resolver el recálculo de membresía por cambio
de tags, fuera del alcance de este ticket.
