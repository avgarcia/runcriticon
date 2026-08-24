# Módulo `club_taxonomia`

Bounded context de **Club y taxonomía**. Tags del club, proyección local de alumnos y entrenadores (alimentada por eventos de `identidad`), grupos como consultas sobre tags con excepciones manuales, y la asignación de entrenadores a grupos.

Consume de `identidad` (`AlumnoInvitado`, `EntrenadorInvitado`, `AlumnoActivado`, `EntrenadorActivado`, `AlumnoEliminado`, `EntrenadorEliminado`, `AdminEliminado`) para mantener su proyección local de personas — `AdminEliminado` (LAL-126) es la excepción: un admin nunca se proyecta, así que solo anonimiza su `actor_id` en `evento_auditoria`, no borra nada de `persona`. Desde LAL-94 también **publica** eventos propios.

## Eventos publicados

| Evento | Cuándo | Schema | Consumido por |
|---|---|---|---|
| `MembresiaDeGrupoCambiada` v1 | La membresía de alumnos de un grupo cambia — snapshot completo, no delta (crear grupo, override, quitar override, cambio de tags de un alumno) | `schemas/club_taxonomia/membresia-de-grupo-cambiada-v1.json` | `planificacion` (`GroupMembersProjectionListener`) |
| `EntrenadorAsignadoAGrupo` v1 | Un entrenador queda vinculado a un grupo (`AssignCoachToGroupCommand`) | `schemas/club_taxonomia/entrenador-asignado-a-grupo-v1.json` | `planificacion` (`GroupMembersProjectionListener`) |
| `EntrenadorEliminadoDeGrupo` v1 | Un entrenador queda desvinculado de un grupo (`UnassignCoachFromGroupCommand`) | `schemas/club_taxonomia/entrenador-eliminado-de-grupo-v1.json` | `planificacion` (`GroupMembersProjectionListener`) |

> El contrato de cada evento lo valida el job `contractTest` contra su JSON Schema.
> Un cambio rompiente exige `…-v2.json` + dual-publishing 4 semanas (ver `schemas/README.md`).

## `MembresiaDeGrupoCambiada` sustituye a `AlumnoAsignadoAGrupo`/`AlumnoEliminadoDeGrupo` (LAL-94, retirados)

Aquellos dos eventos solo cubrían la excepción manual de pertenencia, nunca la pertenencia por tags — el camino
normal de entrada a un grupo. Un consumidor que construyera su proyección solo con ellos veía **exclusivamente
las excepciones manuales**, nunca la membresía completa. No era una carencia de payload: esa semántica no podía
llegar a ser nunca una fuente completa de membresía por diseño.

`MembresiaDeGrupoCambiada` es distinto en forma, no solo en cobertura: lleva el **snapshot completo** de alumnos
del grupo (`alumnos: List<UUID>`), no un delta. Un consumidor reemplaza su proyección de ese grupo entera con lo
que trae el evento — así un evento perdido o reordenado no la corrompe, el siguiente que llegue ya trae el
estado completo. `GroupMembershipPublisher` (`application/usecases/groups/`) centraliza el cálculo y la
publicación; lo llaman seis puntos:

| Caso de uso | Grupos que recalcula |
|---|---|
| `CreateGroupCommand` | el grupo recién creado |
| `OverrideGroupMembershipCommand` | el del override (con la membresía que ya calculó `findDetail`, sin repetir la consulta) |
| `ClearGroupMembershipOverrideCommand` | el del override quitado — **antes no publicaba nada**, ahora sí: con el snapshot completo ya no hace falta saber si el alumno queda dentro o fuera para decidir qué evento emitir |
| `AssignStudentTagCommand` / `UnassignStudentTagCommand` / `ReplaceStudentTagsCommand` | los grupos cuyo filtro toca `Δ` (diferencia simétrica entre los tags de antes y después) — calculado dentro de `StudentClassification.classify`, con la query inversa `GroupRepository.findGroupIdsByAnyRequiredTagValue` |

`resolveMembers` (el SQL que resuelve estos seis puntos) ahora también filtra `rol = 'ALUMNO'` vía JOIN con
`persona`, alineado con `findDetail`/`listSummaries`: antes no lo hacía, con el argumento de que su único
consumidor era "el snapshot de publicación", que hoy ya existe.

### Qué sigue sin disparar recálculo (huecos conocidos, no cerrados en este ticket)

- **Cambiar el filtro de un grupo existente, renombrarlo o borrarlo** — no existen esos casos de uso
  (`GroupRepository.save` es solo alta). Cuando se implementen, cada uno conocerá su `groupId` y publicar será
  trivial.
- **Archivar/reactivar/renombrar un `TagKey`/`TagValue`** — no cambia la membresía resuelta: ninguna consulta de
  resolución hace JOIN contra `tag_key`/`tag_value` ni mira `archivado_en`, y el archivado no cascadea a
  `alumno_tag`. Verificado, no hace falta publicar desde `application/usecases/taxonomy/`.
- **Borrado RGPD de un alumno** — no necesita evento nuevo: `PersonErasureJdbc.erase` no recibe `clubId` y borra
  tags/overrides antes de devolver, así que emitir desde ahí exigiría rehacer el puerto. No hace falta:
  `planificacion.PlanificacionDeletionListener` ya consume `AlumnoEliminado` de `identidad` y limpia
  `miembro_grupo` por su cuenta.
