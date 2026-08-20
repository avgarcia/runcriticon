# Observabilidad — módulo `auditoria`

## Métricas

| Métrica | Tipo | Tags | Descripción |
|---|---|---|---|
| `auditoria.eventos_total` | Counter | `module=auditoria`, `event_type` (`ACCESO_DENEGADO`\|`ACCESO_DATOS_SENSIBLES`) | Asientos persistidos en `auditoria.evento`, por tipo |

`AuditoriaMetrics` (infrastructure) implementa el puerto `AuditEventMetrics` (application) — Micrometer no se
inyecta en `application`, mismo criterio que `IdentidadBusinessMetrics`/`BusinessMetrics`.

## Alarma sugerida

Un pico sostenido de `AccesoDenegado` es la misma señal que documenta ADR-0009 D9/NFR: escaneo, bug recién
desplegado o reorganización del club. Vigilar `auditoria_eventos_total{event_type="ACCESO_DENEGADO"}` con el
mismo umbral (>5 % sostenido >5 min) que ya fija ADR-0009 para la tasa de denegaciones — no se ha dado de alta
la alarma concreta en este ticket (no hay dashboard de AMG configurado para ningún módulo todavía).

## MDC

`AuditEventListener`/`AuditTrailAnonymizationListener` restauran `module=auditoria` + `traceparent` + `clubId`
+ `actorId` del evento consumido, vía `MdcRestorerForEvents`, con `try/finally` — mismo patrón que el resto de
listeners del repo.
