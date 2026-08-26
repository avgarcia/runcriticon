# RGPD — módulo `seguimiento`

Espejo aplicado de ADR-0014. Si hay conflicto con el ADR, gana el ADR — salvo la excepción documentada abajo
sobre `reporte_sesion`, donde dos ADRs Aceptados se contradicen entre sí.

## Tablas con datos personales

| Tabla | Categoría | Retención | Borrado al olvido |
|---|---|---|---|
| `seguimiento.plan_resuelto_por_alumno` | 1 — PII primaria | hasta baja + 30 d | Físico (DELETE) |
| `seguimiento.reporte_sesion` | 1 — PII primaria (incluye datos de salud art. 9: sensaciones, marca de dolor) | hasta baja + 30 d | Físico (DELETE) |

Ambas llevan `club_id`, filtrado por `@AuthScope(Scope.CLUB)` en todo `@Repository` que las toca.

## Eventos consumidos

| Evento | Origen | Acción |
|---|---|---|
| `PlanPublicado` | Planificación | Alimenta la proyección `plan_resuelto_por_alumno` (no es un evento RGPD, se lista aquí por completitud) |
| `AlumnoEliminado` | Identidad | `SeguimientoDeletionListener` → `SeguimientoErasureJdbc.erase`: `DELETE` en `reporte_sesion` primero, luego en `plan_resuelto_por_alumno` (evita dejar un reporte huérfano visible si el proceso se interrumpe entre las dos sentencias) |

## Eventos publicados

| Evento | Cuándo | Consumido por |
|---|---|---|
| `ReporteRegistrado` | Al enviar o editar un reporte de sesión | Ningún consumidor todavía (LAL-116) — no lleva `notas` ni el texto del dolor, solo estado/valoración/motivo/marca |

**No se publica `AccesoADatosSensibles`** desde `SubmitSessionReportCommand` ni desde `GetMyWeekQuery`: ambos
son el alumno accediendo a sus propios datos, excluido explícitamente por `rgpd-en-modulos.md` §5. Además
`@AuditAccess`/`AccessType.SALUD` son hoy inertes en todo el repo — sin aspecto que los implemente, sin
consumidor capaz de representar `AccessType` en el evento — así que emitirlo ahora no auditaría nada real.
Se retomará cuando el entrenador pueda leer reportes ajenos (LAL-34), que es cuando deja de ser "acceso a
datos propios".

## Contradicción entre ADRs sobre `reporte_sesion` — se sigue ADR-0014

`ADR-0004` D16 pide **anonimizar** `seguimiento.reporte_sesion` (sustituir `alumno_id` por un `anonimoId` y
conservar la fila) y describe un mecanismo DSAR (`BorradoAlumnoSolicitado`/`BorradoAlumnoCompletado`) que no
existe en el repo. `ADR-0014` D5 nombra la misma tabla, literalmente, como categoría 1 → **borrado físico**.

Se sigue `ADR-0014`: es el ADR de RGPD (autoridad sobre la materia), lo acompañan `persistencia.md`,
`rgpd-en-modulos.md` y el glosario, y ya hay precedente mergeado — `PlanificacionErasureJdbc` borra
físicamente `personalizacion` y `plan_snapshot_alumno`, dos de las tres tablas que D16 nombra para anonimizar.
Aplicar D16 solo a la tabla nueva de este módulo habría dejado dos criterios de borrado distintos en el mismo
producto sin ninguna justificación de negocio. Revisión de `ADR-0004` D16 pendiente en Linear
(`feature/revision-adr-0004`), no se corrige aquí (CLAUDE.md: un cambio de ADR va en su propia PR).

## Pendientes jurídicos del módulo

- **Descripción libre del dolor**: la columna `reporte_sesion.descripcion_dolor` se crea pero no se rellena.
  Es un dato médico derivado (ubicación/intensidad) con pregunta jurídica abierta sobre si el consentimiento
  genérico de tratamiento basta o hace falta una base legal distinta — pendiente de asesoría legal antes de
  activarla.
- **Consentimiento explícito Art. 9.2.a** (ADR-0014 D16/D18): **el mecanismo ya existe** — tabla
  `identidad.consentimiento`, casilla no premarcada en la activación, `/me/consentimiento` para conceder o
  revocar (LAL-128, PR1, módulo `identidad`; ver `identidad/RGPD.md`). Lo que sigue pendiente **en este
  módulo** es la puerta que rechace nuevos reportes de un alumno sin consentimiento vigente — proyección
  local + listener de `ConsentimientoConcedido`/`ConsentimientoRevocado` + `ensure` en
  `SubmitSessionReportCommand` (LAL-128, PR2, todavía no mergeada a la fecha de este comentario).
- Confirmar con asesoría legal si el borrado físico de `reporte_sesion` (en vez de la anonimización que pide
  ADR-0004 D16) es también correcto desde el punto de vista de retención de datos de salud, no solo desde el
  de RGPD general.
- **RAT (registro de actividades de tratamiento, ADR-0014 D19)**: creado en `docs/legal/rat.md` (LAL-128),
  con la entrada de este tratamiento — pendiente de validación legal completa, no de existir el fichero.
