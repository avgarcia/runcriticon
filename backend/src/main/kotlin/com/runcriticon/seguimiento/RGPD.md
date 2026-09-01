# RGPD — módulo `seguimiento`

Espejo aplicado de ADR-0014. Si hay conflicto con el ADR, gana el ADR.

## Tablas con datos personales

| Tabla | Categoría | Retención | Borrado al olvido |
|---|---|---|---|
| `seguimiento.plan_resuelto_por_alumno` | 1 — PII primaria | hasta baja + 30 d | Físico (DELETE) |
| `seguimiento.reporte_sesion` | 1 — PII primaria (incluye datos de salud art. 9: sensaciones, marca de dolor) | hasta baja + 30 d | Físico (DELETE) |
| `seguimiento.marca_alumno` | 1 — PII primaria (dato de salud art. 9: rendimiento del corredor) | hasta baja + 30 d | Físico (DELETE) |

Las tres llevan `club_id`, filtrado por `@AuthScope(Scope.CLUB)` en todo `@Repository` que las toca.
`marca_alumno` además: **privacidad fuerte** (ADR-0002 D7) — sin fila ADMIN/ENTRENADOR en
`AuthorizationMatrix` sobre `Resource.MARCA`, ni siquiera para lectura agregada.

## Eventos consumidos

| Evento | Origen | Acción |
|---|---|---|
| `PlanPublicado` | Planificación | Alimenta la proyección `plan_resuelto_por_alumno` (no es un evento RGPD, se lista aquí por completitud) |
| `AlumnoEliminado` | Identidad | `SeguimientoDeletionListener` → `SeguimientoErasureJdbc.erase`: `DELETE` en `reporte_sesion` primero, luego en `plan_resuelto_por_alumno`, luego en `marca_alumno` (evita dejar un reporte huérfano visible si el proceso se interrumpe entre las dos primeras sentencias) |

## Eventos publicados

| Evento | Cuándo | Consumido por |
|---|---|---|
| `ReporteRegistrado` | Al enviar o editar un reporte de sesión | Ningún consumidor todavía (LAL-116) — no lleva `notas` ni el texto del dolor, solo estado/valoración/motivo/marca |
| `MarcaActualizada` | Al registrar o editar una marca (LAL-31) | Ningún consumidor todavía (LAL-32) |
| `MarcaRetirada` | Al borrar una marca, solo si de verdad había fila (LAL-31) | Ningún consumidor todavía (LAL-32) |

**No se publica `AccesoADatosSensibles`** desde `SubmitSessionReportCommand`, `GetMyWeekQuery`,
`RecordMarkCommand`, `WithdrawMarkCommand` ni `GetMyMarksQuery`: todos son el alumno accediendo a sus propios
datos, excluido explícitamente por `rgpd-en-modulos.md` §5. Además `@AuditAccess`/`AccessType.SALUD` son hoy
inertes en todo el repo — sin aspecto que los implemente, sin consumidor capaz de representar `AccessType` en
el evento — así que emitirlo ahora no auditaría nada real. Se retomará cuando un tercero pueda leer datos
propios de otro alumno (reportes, LAL-34; marcas, ninguna historia lo contempla — ver privacidad fuerte
arriba), que es cuando deja de ser "acceso a datos propios".

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
- Confirmar con asesoría legal si el borrado físico de `reporte_sesion` (categoría 1 de ADR-0014 D5/D6) es
  también correcto desde el punto de vista de retención de datos de salud, no solo desde el de RGPD general.
- **RAT (registro de actividades de tratamiento, ADR-0014 D19)**: creado en `docs/legal/rat.md` (LAL-128),
  con la entrada de este tratamiento — pendiente de validación legal completa, no de existir el fichero.
