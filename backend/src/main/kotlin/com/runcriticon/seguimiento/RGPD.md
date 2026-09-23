# RGPD — módulo `seguimiento`

Espejo aplicado de ADR-0014. Si hay conflicto con el ADR, gana el ADR.

## Tablas con datos personales

| Tabla | Categoría | Retención | Borrado al olvido |
|---|---|---|---|
| `seguimiento.plan_resuelto_por_alumno` | 1 — PII primaria | hasta baja + 30 d | Físico (DELETE) |
| `seguimiento.reporte_sesion` | 1 — PII primaria (incluye datos de salud art. 9: sensaciones, marca de dolor) | hasta baja + 30 d | Físico (DELETE) |
| `seguimiento.marca_alumno` | 1 — PII primaria (dato de salud art. 9: rendimiento del corredor) | hasta baja + 30 d | Físico (DELETE) |
| `seguimiento.reajuste_dia` | 1 — PII primaria (el motivo `MOLESTIAS` es dato de salud art. 9) | hasta baja + 30 d | Físico (DELETE) |

Las cuatro llevan `club_id`, filtrado por `@AuthScope(Scope.CLUB)` en todo `@Repository` que las toca.
`marca_alumno` además: **privacidad fuerte** (ADR-0002 D7) — sin fila ADMIN/ENTRENADOR en
`AuthorizationMatrix` sobre `Resource.MARCA`, ni siquiera para lectura agregada.

## Eventos consumidos

| Evento | Origen | Acción |
|---|---|---|
| `PlanPublicado` | Planificación | Alimenta la proyección `plan_resuelto_por_alumno` (no es un evento RGPD, se lista aquí por completitud) |
| `AlumnoEliminado` | Identidad | `SeguimientoDeletionListener` → `SeguimientoErasureJdbc.erase`: `DELETE` en `reporte_sesion` y `reajuste_dia` primero, luego en `plan_resuelto_por_alumno`, luego en `marca_alumno` (evita dejar un reporte/reajuste huérfano visible si el proceso se interrumpe a medias) |

## Eventos publicados

| Evento | Cuándo | Consumido por |
|---|---|---|
| `ReporteRegistrado` | Al enviar o editar un reporte de sesión | Ningún consumidor todavía — no lleva `notas` ni el texto del dolor, solo estado/valoración/motivo/marca |
| `MarcaActualizada` | Al registrar o editar una marca | `MarkPaceRecalculationListener` (ritmos resueltos por alumno) — recalcula `plan_resuelto_por_alumno`, sin propagar `tiempoSegundos` del evento (relee la marca) |
| `MarcaRetirada` | Al borrar una marca, solo si de verdad había fila | `MarkPaceRecalculationListener` (ritmos resueltos por alumno) — misma proyección, vuelve el ritmo relativo a "falta marca" |
| `DiaReajustado` | Al mover o saltar el día de una sesión | Ningún consumidor todavía — sin `mensaje`, solo `accion`/`motivo`/`marcaDolor` |
| `AccesoADatosSensibles` | Cada llamada a `ListCoachAlertsQuery` con al menos una alerta activa (`@AuditAccess`) — un evento por alumno con alerta | Módulo `auditoria` (`AuditEventListener`) |

**No se publica `AccesoADatosSensibles`** desde `SubmitSessionReportCommand`, `GetMyWeekQuery`,
`RecordMarkCommand`, `WithdrawMarkCommand`, `GetMyMarksQuery`, `RescheduleDayCommand` ni
`WithdrawDayAdjustmentCommand`: todos son el alumno accediendo a sus propios datos, excluido explícitamente
por `rgpd-en-modulos.md` §5. `ListGroupActivityQuery` tampoco lo publica pese a leer datos del club: su
resultado es un agregado por grupo (`MAX(reportado_en)`) sin ningún alumno identificable, así que no hay
sujeto que auditar (ver su propio KDoc).

`ListCoachAlertsQuery` **sí** lo publica — es el primer caso de uso de `seguimiento` donde un tercero
(entrenador) lee datos de salud de otro (alumno). Implementado con el panel de alertas del entrenador pero
**roto en silencio hasta que se añadió la auditoría de acceso a datos sensibles en el módulo**: el método
llevaba `@Transactional(readOnly = true)`, que impedía la escritura en el outbox sin
lanzar ninguna excepción — el aspecto se disparaba, calculaba los sujetos correctos, y aun así no quedaba
ninguna fila en `event_publication`. `ListCoachAlertsQueryAuditAccessIntegrationTest` es la prueba de
extremo a extremo que lo detectó; `RgpdArchTest` ahora rechaza el build si un `@AuditAccess` futuro repite el
mismo error.

## Pendientes jurídicos del módulo

- **Descripción libre del dolor**: la columna `reporte_sesion.descripcion_dolor` se crea pero no se rellena.
  Es un dato médico derivado (ubicación/intensidad) con pregunta jurídica abierta sobre si el consentimiento
  genérico de tratamiento basta o hace falta una base legal distinta — pendiente de asesoría legal antes de
  activarla.
- **Consentimiento explícito Art. 9.2.a** (ADR-0014 D16/D18): **el mecanismo ya existe** — tabla
  `identidad.consentimiento`, casilla no premarcada en la activación, `/me/consentimiento` para conceder o
  revocar (módulo `identidad`; ver `identidad/RGPD.md`). En este módulo la puerta que rechaza nuevos
  reportes de un alumno sin consentimiento vigente también está implementada — proyección local
  (`consentimiento_alumno`) alimentada por `ConsentProjectionListener` sobre
  `ConsentimientoConcedido`/`ConsentimientoRevocado`, con `ensure` en `SubmitSessionReportCommand`.
- Confirmar con asesoría legal si el borrado físico de `reporte_sesion` (categoría 1 de ADR-0014 D5/D6) es
  también correcto desde el punto de vista de retención de datos de salud, no solo desde el de RGPD general.
- **RAT (registro de actividades de tratamiento, ADR-0014 D19)**: creado en `docs/legal/rat.md`, con la
  entrada de este tratamiento (consentimiento explícito Art. 9.2.a) — pendiente de validación legal
  completa, no de existir el fichero.
