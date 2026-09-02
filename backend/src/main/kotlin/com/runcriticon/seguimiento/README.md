# Módulo `seguimiento`

Bounded context de **Seguimiento**. LAL-29 arrancó el módulo con la primera proyección de solo lectura
(`plan_resuelto_por_alumno`, la vista semanal del alumno); LAL-30 añade su primer agregado propio con
escritura, `reporte_sesion`, y el primer evento publicado del módulo; LAL-31 añade `marca_alumno`, el
segundo agregado con escritura del módulo; LAL-32 cierra el círculo — resuelve los ritmos relativos de
`plan_resuelto_por_alumno` contra esas marcas (ver más abajo); LAL-33 añade `reajuste_dia`, el tercer
agregado con escritura, superpuesto en la ruta de lectura sobre la proyección congelada.

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

**Orden de guardas** en el caso de uso: RBAC (`AuthorizationMatrix`) → consentimiento vigente de datos de
salud (LAL-128) → resolver el día contra `ResolvedPlanReader.findDay` (anti-IDOR: `alumnoId` siempre
`actor.userId`, nunca un parámetro) → rechazo de días futuros → invariantes de dominio → persistencia →
evento `ReporteRegistrado`.

**No se emite `AccesoADatosSensibles`**: `rgpd-en-modulos.md` §5 excluye explícitamente la lectura/escritura
del propio perfil del usuario, y es exactamente lo que hace este caso de uso. La auditoría de acceso a datos
de salud aplicará cuando un tercero (el entrenador) lea reportes ajenos — ver `RGPD.md`.

## Marcas privadas del alumno (LAL-31)

`RecordMarkCommand`/`WithdrawMarkCommand`/`GetMyMarksQuery` gestionan `marca_alumno`: el mejor tiempo del
alumno en una de las cuatro distancias estándar (`RaceDistance`, reusado del catálogo ya existente de
`ResolvedPace`), sin histórico — PK compuesta `(alumno_id, distancia)`, cada envío sobreescribe.

**Privacidad fuerte (ADR-0002 D7), la barrera técnica que exige la historia**: `Resource.MARCA` no tiene
ninguna fila de `ADMIN`/`ENTRENADOR` en `AuthorizationMatrix` — deny-by-default, no una regla negativa. No
hay ningún endpoint, caso de uso ni consulta agregada que exponga marcas fuera de `/me/marcas*`.

**Orden de guardas**, mismo criterio que `SubmitSessionReportCommand`: RBAC → `StudentId.of(actor.userId)`
(anti-IDOR: `alumnoId` nunca es un parámetro) → invariante de dominio (`tiempoSegundos > 0`) → persistencia →
evento. Sin consultar consentimiento (a diferencia del reporte de sesión): la marca no es un dato de sesión
ejecutada cubierto por ADR-0014 D18, es un tiempo de referencia introducido voluntariamente.

**Borrado idempotente**: `WithdrawMarkCommand` siempre responde `204`, con o sin fila previa; `MarcaRetirada`
solo se publica cuando de verdad borró algo, para que su consumidor (`MarkPaceRecalculationListener`, LAL-32)
no reciba ruido.

## Ritmos relativos resueltos por marca (LAL-32)

Cierra el hueco que LAL-29 dejó a propósito (ver el KDoc de `ResolvedPace` en su momento): un ritmo `RELATIVO`
ya no queda varado en "falta marca" para siempre — se resuelve contra la marca real del alumno en cuanto la
tiene, y se recalcula cuando la edita o la retira.

- **Dónde resuelve**: en la proyección, nunca en tiempo de petición (AC4). `ResolvedPlanProjectionListener` y
  `PersonalizationProjectionListener` resuelven al proyectar `PlanPublicado`/`PersonalizacionAplicada`/
  `PersonalizacionRetirada`; `MarkPaceRecalculationListener` (nuevo) recalcula cuando cambia la marca.
- **`MarkPaceRecalculationListener` es el primer listener del repo que consume un `IntegrationEvent` de su
  propio módulo** (`MarcaActualizada`/`MarcaRetirada`) — antes de LAL-32 ninguno de los dos tenía consumidor.
- **No confía en el payload del evento**: relee la marca actual con `StudentMarkLookup.findMark` (puerto
  nuevo, sin `@AuthScope` — corre en el listener del outbox, sin principal) y recalcula contra ese valor. Dos
  ediciones de la misma marca entregadas fuera de orden convergen igual, sin necesitar guarda de orden por
  `occurredAt`.
- **Columna nueva**: `ritmo_delta_seg_por_km` en `plan_resuelto_por_alumno` (`V202609010001`). Las filas
  `RELATIVO` proyectadas antes de esta migración no tienen delta y se quedan en "falta marca" hasta que se
  vuelva a publicar el plan que las originó — no hay backfill posible (`PlanPublicado` no se reemite).
  `ResolvedPace.Relative` documenta el invariante: resuelto (`secondsPerKm != null`) ⟹ delta conocido.
- **El recálculo toca solo columnas `ritmo_*`**: nunca `sesion_resuelta`, `es_personalizada` ni
  `last_processed_event_*` — ese watermark es del flujo plan/personalización y del gauge
  `projection_lag_seconds`; tocarlo desde el recálculo por marca los rompería.
- **Distancias oficiales**: `RaceDistance.meters` (21.097/42.195, no divisiones triviales de 5K).
  `StudentMark.paceSecondsPerKm()` redondea al segundo más cercano; `resolveRelativePace` aplica un suelo de
  1 s/km sobre `marca + delta`.

## Reajuste de día del alumno (LAL-33)

`RescheduleDayCommand`/`WithdrawDayAdjustmentCommand` permiten al alumno mover una sesión a otro día
(≤ +7 días) o marcarla como saltada, con motivo (`AdjustmentReason`: `CANSANCIO`/`MOLESTIAS`/`IMPREVISTO` —
catálogo propio, no una extensión de `NotDoneReason`: son conceptos distintos aunque compartan dos valores en
superficie), sin depender de respuesta del entrenador (`docs/research/findings.md` §P3).

**La proyección `plan_resuelto_por_alumno` nunca se escribe desde este flujo.** El reajuste vive en su propia
tabla, `reajuste_dia`, con PK `(alumno_id, plan_id, dia)` donde `dia` es siempre el día **planificado**.
`ResolvedPlanReaderJdbc` la superpone con `LEFT JOIN` y calcula el día **efectivo** que ve el alumno con
`COALESCE(dia_destino, dia_planificado)` — el plan publicado queda intacto (ADR-0002 D5, snapshot congelado).
Consecuencia directa: `SubmitSessionReportCommand` ancla el reporte a `resolved.plannedDay`, no al día
efectivo bajo el que el alumno vio la sesión ese día.

**Conflicto de día destino**: si el destino ya tiene una sesión efectiva, `RescheduleDayCommand` responde
`409 DIA_DESTINO_OCUPADO` salvo que la petición traiga `resolucionConflicto` (`REEMPLAZAR` marca la sesión
ocupante como saltada; `INTERCAMBIAR` la mueve al día de origen). Ambas resoluciones escriben **dos filas**
que comparten `operationId` — el mismo id que usa `WithdrawDayAdjustmentCommand` para deshacer la operación
completa (`DELETE /me/reajustes/{dia}` idempotente, `204` con o sin fila previa, mismo criterio que
`WithdrawMarkCommand`), nunca una sola fila suelta.

**`marcaDolor` no es input directo del alumno**: `DayAdjustment.create` la deriva del motivo, igual que
`SessionReport.create`. El evento publicado (`DiaReajustado`) lleva `accion` y `motivo` además de
`marcaDolor` — el futuro panel de alertas (LAL-116) necesita `accion` para la regla *"saltó N consecutivas"*
de `docs/wireframes/08-coach-alerts.md`, no solo la bandera de dolor.

## Contrato (`api/openapi.yaml`)

| Endpoint | Caso de uso |
|---|---|
| `GET /me/plan` | `GetMyWeekQuery` |
| `PUT /me/reportes/{dia}` | `SubmitSessionReportCommand` — crea o reemplaza, `200` devuelve la sesión del día con el reporte aplicado |
| `PUT /me/reajustes/{dia}` | `RescheduleDayCommand` — crea o reemplaza, `409` si el destino está ocupado sin `resolucionConflicto` |
| `DELETE /me/reajustes/{dia}` | `WithdrawDayAdjustmentCommand` — idempotente, `204` con o sin reajuste previo, deshace la operación completa |
| `GET /me/marcas` | `GetMyMarksQuery` — las cuatro distancias, con o sin valor |
| `PUT /me/marcas/{distancia}` | `RecordMarkCommand` — crea o reemplaza, sin histórico |
| `DELETE /me/marcas/{distancia}` | `WithdrawMarkCommand` — idempotente, `204` con o sin fila previa |

## Eventos consumidos

| Evento | De | Alimenta | Consumido por |
|---|---|---|---|
| `PlanPublicado` v1 | `planificacion` | `plan_resuelto_por_alumno` | `ResolvedPlanProjectionListener` |
| `PersonalizacionAplicada` v1 | `planificacion` | `plan_resuelto_por_alumno` (sustituye la fila por el override) | `PersonalizationProjectionListener` |
| `PersonalizacionRetirada` v1 | `planificacion` | `plan_resuelto_por_alumno` (restaura la fila a la sesión base) | `PersonalizationProjectionListener` |
| `ConsentimientoConcedido` v1 | `identidad` | `consentimiento_alumno` | `ConsentProjectionListener` |
| `ConsentimientoRevocado` v1 | `identidad` | `consentimiento_alumno` | `ConsentProjectionListener` |
| `AlumnoEliminado` v1 | `identidad` | Borrado RGPD físico de `plan_resuelto_por_alumno` y `reporte_sesion` | `SeguimientoDeletionListener` |

## Eventos publicados

| Evento | Cuándo | Consumido por |
|---|---|---|
| `ReporteRegistrado` v1 | Al enviar o editar el reporte de una sesión (LAL-30) | Ningún consumidor todavía — LAL-116 (panel de alertas) lo consumirá |
| `MarcaActualizada` v1 | Al registrar o editar una marca (LAL-31) | `MarkPaceRecalculationListener` (LAL-32) — resuelve los ritmos relativos que referencien esa distancia |
| `MarcaRetirada` v1 | Al borrar una marca (LAL-31), solo si de verdad había una fila | `MarkPaceRecalculationListener` (LAL-32) — vuelve a "falta marca" el ritmo relativo que dependía de ella |
| `DiaReajustado` v1 | Al mover o saltar el día de una sesión (LAL-33). Un `REEMPLAZAR`/`INTERCAMBIAR` publica un evento por fila escrita | Ningún consumidor todavía — LAL-116 (panel de alertas) lo consumirá |

Spring Modulith solo crea fila en `event_publication` (el outbox) por cada **listener registrado** de un
evento. Sin consumidor, `ReporteRegistrado` **no deja rastro en el outbox** — se publica (verificado por
`SubmitSessionReportCommandTest`) pero no persiste como pendiente. No es un bug: es el comportamiento
esperado hasta que LAL-116 registre el primer `@ApplicationModuleListener`.

## Métricas

| Métrica | Tipo | Tags | Qué mide |
|---|---|---|---|
| `seguimiento.projection_lag_seconds` | Gauge | `module`, `projection` | Retraso de `plan_resuelto_por_alumno` (ADR-0009 D9) |
| `seguimiento.reportes_total` | Counter | `module`, `estado` | Reportes registrados, por estado |
| `seguimiento.reportes_rechazados_total` | Counter | `module`, `motivo` | Reportes rechazados antes de persistir (hoy solo `consentimiento`) |
| `seguimiento.reajustes_total` | Counter | `module`, `accion` | Reajustes aplicados, por acción (LAL-33) |

## Huecos conocidos, no cerrados en este ticket

- El campo *"¿Cuánto hiciste?"* del estado `PARCIAL` está en el mockup del spec 07 pero no en el glosario
  (fuente autoritativa) — diferido.
- Etiquetas textuales bajo la escala de valoración y adjuntar FIT/GPX: `docs/backlog.md` los marca **SHOULD**,
  post-MVP.
- El enlace "¿mover lo que falta a otro día?" del flujo de reporte hacia el de reajuste (spec 07, combinación
  de flujos): diferido, fuera de los criterios de aceptación de LAL-33.
- "Avisar de lesión" (opción 4 del wireframe 07 §Flujo B): cambia el tag `estado` del alumno en
  `clubtaxonomia`, cruza módulo — fuera de alcance de LAL-33, ticket de seguimiento propio.
- El panel de alertas que consume `ReporteRegistrado`/`DiaReajustado`: LAL-116.
- El editor de ritmo relativo del entrenador (LAL-27): la UI de `planificacion` solo ofrece `ABSOLUTO` en el
  editor de sesión hasta entonces — la resolución de LAL-32 funciona igual, solo falta la vía de creación
  desde la UI.
- Histórico de marcas (`marca_alumno_historico`): backlog **COULD**, post-MVP (ADR-0002 D7, notas).
