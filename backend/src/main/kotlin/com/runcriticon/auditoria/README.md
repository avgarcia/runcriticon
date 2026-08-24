# Módulo `auditoria`

Bounded context **Auditoría** (ADR-0009 D15-D17). Consumidor puro: no decide, no es invocado
síncronamente por nadie — persiste en `auditoria.evento` los dos eventos de autorización que publican los
módulos de negocio, y expone una consulta forense de solo lectura para ADMIN.

## Alcance de esta entrega

Cierra el AC3 de **LAL-93** (asignar entrenadores a grupos): `PublishPlanCommand` (`planificacion`) ya emite
`AccesoDenegado` en sus 4 puntos de rechazo (RBAC, plan no encontrado/ajeno, entrenador sin relación con el
grupo, proyección de membresía atrasada). **No cierra el AC3 de LAL-87** — los tags de un alumno no son "datos
de salud" según el alcance explícito de D15 (marcas, sesiones, lesiones, observaciones médicas), así que ese
AC necesita su propio mecanismo, decidido aparte.

**Retrofit deliberadamente fuera de esta entrega**: el resto de casos de uso `Forbidden`/`ProjectionStale` del
repo (identidad, resto de `club_taxonomia` y `planificacion`) no emiten `AccesoDenegado` todavía. `PublishPlanCommand`
es la única prueba end-to-end de que el mecanismo funciona; extenderlo al resto es trabajo de seguimiento,
ticket aparte — cada caso de uso lo añade cuando lo toque, no en un barrido mecánico.

**`AccesoADatosSensibles` sin productor todavía**: el módulo `seguimiento` (dueño de los datos de salud) no
existe en este repo. El evento, el consumidor y la categorización viven ya en este módulo, listos para cuando
llegue — no se instrumenta ningún caso de uso ficticio para "usarlo".

**Sin job de purga todavía**: D17 no lo exige explícitamente y no hay ningún precedente de `@Scheduled` en el
repo — introducirlo aquí habría sido una pieza de infraestructura nueva sin AC que la pidiera. Pendiente,
documentado en `RGPD.md`.

## Los dos eventos

Viven en `auditoria.api.events`, **no** en el módulo que los publica cada vez — a diferencia del resto de
eventos del repo (cada uno vive en el módulo que lo origina), `AccesoDenegado`/`AccesoADatosSensibles` los
puede producir potencialmente cualquier módulo de negocio, y `IntegrationEventArchTest` exige que todo
`IntegrationEvent` resida en un único paquete `api.events` con `@NamedInterface`. `auditoria` es el único
consumidor estable, así que su paquete es el contrato público que cada productor importa — creando una
dependencia `{módulo productor} → auditoria` deliberada, documentada en el Javadoc de cada evento.

| Evento | Cuándo | Quién lo publica hoy |
|---|---|---|
| `AccesoDenegado` v1 | Cualquier `Either.Left(XxxError.Forbidden)` o `ProjectionStale` (D15-D16) | `PublishPlanCommand` (planificacion) |
| `AccesoADatosSensibles` v1 | `@AuditaAcceso` — lectura/modificación de datos de salud con éxito (D15) | Nadie todavía |

## Eventos consumidos

| Evento | De | Efecto |
|---|---|---|
| `AccesoDenegado` v1 | `auditoria.api.events` (publicado por módulos de negocio) | Fila nueva en `auditoria.evento`, tipo `ACCESO_DENEGADO` |
| `AccesoADatosSensibles` v1 | `auditoria.api.events` | Fila nueva en `auditoria.evento`, tipo `ACCESO_DATOS_SENSIBLES` |
| `AlumnoEliminado` v1 | `identidad` | Anonimiza (`actor_id`/`sujeto_id` → `NULL`), no borra — `AuditTrailAnonymizationListener` |
| `EntrenadorEliminado` v1 | `identidad` | Igual que arriba |
| `AdminEliminado` v1 | `identidad` | Igual que arriba, solo `actor_id` (un admin nunca es `sujeto_id`) — LAL-126 |

## Consulta forense

`GET /api/auditoria/eventos` — solo ADMIN (`AUDIT_EVENT:LIST`). Filtros `actorId`, `sujetoId`, `tipo`,
`desde`/`hasta`; `clubId` sale siempre del principal, nunca de un parámetro. Sin paginación todavía — no hay
ningún endpoint paginado precedente en el repo; el repositorio acota con `LIMIT 500`, del más reciente al más
antiguo. Se amplía a paginación real si el volumen lo exige.

## Categoría RGPD

`auditoria.evento` es `AUDITORIA_AUTORIZACION` (categoría 3). Ver `RGPD.md`.

## Dependencias

- Núcleo compartido: `shared.autorizacion` (`Principal`, `AuthorizationMatrix`, `PrincipalProvider`).
- `identidad.api.events` (`AlumnoEliminado`, `EntrenadorEliminado`, `AdminEliminado`) — misma dependencia pública
  que ya usa `club_taxonomia.StudentDeletionListener`.

## Quién depende de este módulo

- `planificacion.PublishPlanCommand` importa `auditoria.api.events.AccesoDenegado` para reportar sus 4
  denegaciones — dependencia deliberada, ver "Los dos eventos" arriba.
