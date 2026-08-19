# RGPD — módulo `auditoria`

Espejo aplicado de ADR-0014 y ADR-0009 D15-D17. Si hay conflicto, gana el ADR.

## Categorización

| Tabla | Categoría | Motivo |
|---|---|---|
| `auditoria.evento` | `AUDITORIA_AUTORIZACION` (3) | Rastro de denegaciones de autorización y accesos a datos sensibles |
| `auditoria.evento_procesado` | `SIN_PII` (0) | Idempotencia de listeners, sin dato de cliente |

## Derecho al olvido: anonimización, no borrado físico

**Diverge del patrón `StudentDeletionListener` del resto de módulos**, a propósito: este módulo *es* el
rastro de auditoría, y ese rastro debe sobrevivir a la persona que menciona (D17 explícito: "las filas de
`auditoria.evento` que lo mencionan se anonimizan, no se borran").

`AuditTrailAnonymizationListener` consume `AlumnoEliminado`/`EntrenadorEliminado` (identidad) y ejecuta:

```sql
UPDATE auditoria.evento
SET actor_id = NULL, sujeto_id = NULL
WHERE actor_id = ? OR sujeto_id = ?
```

Idempotente vía `AuditoriaProcessedEventTracker` (`auditoria.evento_procesado`), igual que el resto de
listeners del repo — un reintento del outbox no vuelve a anonimizar (no hace daño si lo hiciera, pero la marca
evita el trabajo de más).

## Lo que no se persiste

`motivo` (el porqué de una denegación) es texto libre pensado para investigación forense, no para PII —
`PublishPlanCommand` solo mete literales técnicos (`"RBAC"`, `"PlanNotFound"`, `"NotCoachOfGroup"`,
`"ProjectionStale(lag=Ns)"`), nunca datos personales. Si un futuro productor de `AccesoDenegado` mete algo
identificable en `motivo`, ese dato **no se anonimiza** — queda como deuda a vigilar en la revisión de PR de
cada nuevo productor, no como automatismo de este módulo.

## Retención — pendiente

D17 no fija un plazo de retención explícito; `docs/arquitectura/rgpd-en-modulos.md` §7 sugiere 24 meses con un
job de purga mensual, pero **no hay ningún `@Scheduled` precedente en el repo** — introducir el primero sin un
AC que lo pida quedó fuera de esta entrega. Pendiente: ticket de seguimiento que decida el mecanismo (Spring
`@Scheduled` vs `pg_cron`, ADR-0006) y lo implemente.
