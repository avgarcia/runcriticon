# Módulo `seguimiento`

Bounded context de **Seguimiento**. LAL-29 arrancó el módulo con la primera proyección de solo lectura
(`plan_resuelto_por_alumno`, la vista semanal del alumno); LAL-30 añade su primer agregado propio con
escritura, `reporte_sesion`, y el primer evento publicado del módulo.

## Vista semanal del alumno (LAL-29)

`GetMyWeekQuery` resuelve la semana en curso (o la pedida por parámetro) contra `plan_resuelto_por_alumno`,
alimentada por `ResolvedPlanProjectionListener` al consumir `PlanPublicado` de `planificacion`. El día se
identifica por `(alumno_id, plan_id, dia)`: un alumno en dos grupos cuyos entrenadores publican planes
distintos para el mismo día tiene dos filas, y `findWeek`/`findDay` desempatan con
`DISTINCT ON (dia) … ORDER BY dia, last_processed_event_ts DESC, plan_id DESC` — el evento más reciente gana,
determinista incluso si dos publicaciones llegan en el mismo milisegundo.

`mensaje_al_alumno` y `es_personalizada` se persisten ya (columnas creadas, siempre `NULL`/`FALSE`) porque
`PlanPublicado` no lleva todavía datos de personalización — llegan con LAL-26, sin migración adicional.

## Reporte de sesión del alumno (LAL-30)

`SubmitSessionReportCommand` permite al alumno marcar una sesión como `HECHO`/`PARCIAL`/`NO_HECHO`, con
valoración 1-5 (obligatoria si `HECHO`/`PARCIAL`), motivo (obligatorio si `NO_HECHO`) y notas — la definición
completa de `docs/glosario.md` §Seguimiento, salvo el texto libre de descripción del dolor (columna
`descripcion_dolor` creada, sin rellenar; pregunta jurídica abierta).

**Envío idempotente**: la PK de `reporte_sesion` es `(alumno_id, plan_id, dia)`, espejo de
`plan_resuelto_por_alumno` — reportar dos veces el mismo día es editar (`SessionReportRepositoryJdbc.upsert`
vía `ON CONFLICT`), nunca duplica. Dos planes de grupos distintos el mismo día son dos reportes distintos.

`SessionReport.create` impone los invariantes de dominio (motivo `MOLESTIAS` activa `marca_dolor`
automáticamente, nunca es un input directo del cliente); la migración repite los mismos CHECK como defensa en
profundidad, alcanzables solo si algo escribe la tabla por fuera de la aplicación.

**Orden de guardas** en el caso de uso: RBAC (`AuthorizationMatrix`) → resolver el día contra
`ResolvedPlanReader.findDay` (anti-IDOR: `alumnoId` siempre `actor.userId`, nunca un parámetro) → rechazo de
días futuros → invariantes de dominio → persistencia → evento `ReporteRegistrado`.

**No se emite `AccesoADatosSensibles`**: `rgpd-en-modulos.md` §5 excluye explícitamente la lectura/escritura
del propio perfil del usuario, y es exactamente lo que hace este caso de uso. La auditoría de acceso a datos
de salud aplicará cuando un tercero (el entrenador) lea reportes ajenos — ver `RGPD.md`.

## Contrato (`api/openapi.yaml`)

| Endpoint | Caso de uso |
|---|---|
| `GET /me/plan` | `GetMyWeekQuery` |
| `PUT /me/reportes/{dia}` | `SubmitSessionReportCommand` — crea o reemplaza, `200` devuelve la sesión del día con el reporte aplicado |

## Eventos consumidos

| Evento | De | Alimenta | Consumido por |
|---|---|---|---|
| `PlanPublicado` v1 | `planificacion` | `plan_resuelto_por_alumno` | `ResolvedPlanProjectionListener` |
| `AlumnoEliminado` v1 | `identidad` | Borrado RGPD físico de `plan_resuelto_por_alumno` y `reporte_sesion` | `SeguimientoDeletionListener` |

## Eventos publicados

| Evento | Cuándo | Consumido por |
|---|---|---|
| `ReporteRegistrado` v1 | Al enviar o editar el reporte de una sesión (LAL-30) | Ningún consumidor todavía — LAL-116 (panel de alertas) lo consumirá |

Spring Modulith solo crea fila en `event_publication` (el outbox) por cada **listener registrado** de un
evento. Sin consumidor, `ReporteRegistrado` **no deja rastro en el outbox** — se publica (verificado por
`SubmitSessionReportCommandTest`) pero no persiste como pendiente. No es un bug: es el comportamiento
esperado hasta que LAL-116 registre el primer `@ApplicationModuleListener`.

## Métricas

| Métrica | Tipo | Tags | Qué mide |
|---|---|---|---|
| `seguimiento.projection_lag_seconds` | Gauge | `module`, `projection` | Retraso de `plan_resuelto_por_alumno` (ADR-0009 D9) |
| `seguimiento.reportes_total` | Counter | `module`, `estado` | Reportes registrados, por estado |

## Huecos conocidos, no cerrados en este ticket

- El campo *"¿Cuánto hiciste?"* del estado `PARCIAL` está en el mockup del spec 07 pero no en el glosario
  (fuente autoritativa) — diferido.
- Etiquetas textuales bajo la escala de valoración y adjuntar FIT/GPX: `docs/backlog.md` los marca **SHOULD**,
  post-MVP.
- El enlace "¿mover lo que falta a otro día?" y el Flujo B de reajuste completo: LAL-33.
- El panel de alertas que consume `ReporteRegistrado`: LAL-116.
