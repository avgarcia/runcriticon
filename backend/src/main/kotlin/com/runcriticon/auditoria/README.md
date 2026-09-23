# Módulo `auditoria`

Bounded context **Auditoría** (ADR-0009 D15-D17). Consumidor puro: no decide, no es invocado
síncronamente por nadie — persiste en `auditoria.evento` los dos eventos de autorización que publican los
módulos de negocio, y expone una consulta forense de solo lectura para ADMIN.

## Alcance actual

Arrancó con **LAL-93** AC3 (asignar entrenadores a grupos): `PublishPlanCommand` (`planificacion`) fue la
primera prueba end-to-end del mecanismo, emitiendo `AccesoDenegado` en sus 4 puntos de rechazo (RBAC, plan no
encontrado/ajeno, entrenador sin relación con el grupo, proyección de membresía atrasada).

**Productores de `AccesoDenegado` hoy** (retrofit de LAL-120):

- `identidad` — `IdentidadAccessAuditor`, solo rechazos RBAC de la matriz (no los de autenticación pura:
  login, magic link, activación, reseteo — esos van a `identidad.evento_auditoria`, ADR-0003 D15).
- `club_taxonomia` — `ClubTaxonomiaAccessAuditor`.
- `planificacion` — `PlanificacionAccessAuditor` para los casos de uso que no tenían un `denegado(...)`
  propio, más `PublishPlanCommand`, `SetPersonalizationCommand` y `RemovePersonalizationCommand`, que
  publican `AccesoDenegado` directamente.
- `seguimiento` — **ninguno**: sus casos de uso devuelven `SeguimientoError.Forbidden` sin emitir
  `AccesoDenegado`.

**`AccesoADatosSensibles`** lo publica `shared.rgpd.AuditAccessAspect` sobre los métodos anotados con
`@AuditAccess`. Hoy el único es `seguimiento.ListCoachAlertsQuery` (LAL-116): el entrenador leyendo reportes
de salud de sus alumnos, un evento por alumno con alerta activa.

**No cubre el AC3 de LAL-87**: los tags de un alumno no son "datos de salud" según el alcance explícito de D15
(marcas, sesiones, lesiones, observaciones médicas). Ese AC se resolvió con una auditoría local propia en
`club_taxonomia.evento_auditoria` (`V202608230001`), no con este módulo.

**Job de purga**: `AuditoriaRetentionJob` purga `auditoria.evento` a los 24 meses (ADR-0014 D10 categoría 3,
mecanismo de ADR-0017 — LAL-133). Detalle en `RGPD.md`.

## Los dos eventos

Viven en `shared.api.events`, **no** en `auditoria` ni en el módulo que los publica cada vez — a diferencia del
resto de eventos del repo (cada uno vive en el módulo que lo origina), `AccesoDenegado`/`AccesoADatosSensibles`
los puede producir potencialmente cualquier módulo de negocio y solo `auditoria` los consume, así que ninguno
de los dos extremos puede ser su dueño sin imponerle al otro una dependencia. Vivieron en `auditoria.api.events`
hasta que `identidad` necesitó publicar `AccesoDenegado` (LAL-120) y formó un ciclo con la dependencia inversa
`auditoria → identidad` (anonimización, más abajo); `shared` es módulo `OPEN`, exento de la detección de ciclos.
Ver el KDoc de `AccesoDenegado` para el detalle.

| Evento | Cuándo | Schema | Quién lo publica hoy |
|---|---|---|---|
| `AccesoDenegado` v1 | Cualquier `Either.Left(XxxError.Forbidden)` o `ProjectionStale` (D15-D16) | `schemas/shared/acceso-denegado-v1.json` | `identidad`, `club_taxonomia`, `planificacion` (ver arriba) |
| `AccesoADatosSensibles` v1 | `@AuditAccess` — lectura/modificación de datos de salud de un tercero con éxito (D15) | `schemas/shared/acceso-datos-sensibles-v1.json` | `shared.rgpd.AuditAccessAspect` sobre `seguimiento.ListCoachAlertsQuery` |

## Eventos consumidos

| Evento | De | Efecto | Listener |
|---|---|---|---|
| `AccesoDenegado` v1 | `shared.api.events` (publicado por módulos de negocio) | Fila nueva en `auditoria.evento`, tipo `ACCESO_DENEGADO` | `AuditEventListener` |
| `AccesoADatosSensibles` v1 | `shared.api.events` | Fila nueva en `auditoria.evento`, tipo `ACCESO_DATOS_SENSIBLES` | `AuditEventListener` |
| `AlumnoEliminado` v1 | `identidad` | Anonimiza (`actor_id`/`sujeto_id` → `NULL`), no borra | `AuditTrailAnonymizationListener` |
| `EntrenadorEliminado` v1 | `identidad` | Igual que arriba | `AuditTrailAnonymizationListener` |
| `AdminEliminado` v1 | `identidad` | Igual que arriba, solo `actor_id` (un admin nunca es `sujeto_id`) — LAL-126 | `AuditTrailAnonymizationListener` |

Ambos listeners son idempotentes vía `auditoria.evento_procesado(listener, event_id)` y restauran el MDC con
`MdcRestorerForEvents`.

## Consulta forense

`GET /api/auditoria/eventos` (`AuditEventController` → `ListAuditEventsQuery`) — solo ADMIN
(`AUDIT_EVENT:LIST`). Filtros `actorId`, `sujetoId`, `tipo`, `desde`/`hasta`; `clubId` sale siempre del
principal, nunca de un parámetro. Sin paginación todavía — no hay ningún endpoint paginado precedente en el
repo; el repositorio acota con `LIMIT 500`, del más reciente al más antiguo. Se amplía a paginación real si el
volumen lo exige.

## Tablas

| Tabla | Migración | Categoría RGPD |
|---|---|---|
| `auditoria.evento` | `V202608190001` | `AUDITORIA_AUTORIZACION` (categoría 3) — ver `RGPD.md` |
| `auditoria.evento_procesado` | `V202608190001` | Idempotencia de listeners |

## Métricas

| Métrica | Tipo | Tags | Qué mide |
|---|---|---|---|
| `auditoria.eventos_total` | Counter | `module`, `event_type` | Eventos de auditoría persistidos, por tipo (`AuditoriaMetrics`) |
| `auditoria.retention_purge.rows_deleted` | Counter | `module`, `table` | Filas purgadas por `AuditoriaRetentionJob` (`AuditoriaRetentionMetrics`) |

## Dependencias

- Núcleo compartido: `shared.autorizacion` (`Principal`, `AuthorizationMatrix`, `PrincipalProvider`) y
  `shared.api.events` (`AccesoDenegado`, `AccesoADatosSensibles`).
- `identidad.api.events` (`AlumnoEliminado`, `EntrenadorEliminado`, `AdminEliminado`) — misma dependencia pública
  que ya usa `club_taxonomia.StudentDeletionListener`.

## Quién depende de este módulo

- Nadie: `AccesoDenegado`/`AccesoADatosSensibles` viven en `shared.api.events` (ver "Los dos eventos" arriba),
  no en `auditoria` — así que ningún módulo productor depende de `auditoria` para publicarlos.
