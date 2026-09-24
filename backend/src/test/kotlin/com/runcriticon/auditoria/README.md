# Tests críticos — módulo Auditoría

| Caso | Tipo de test | Por qué duele si falla en producción |
|---|---|---|
| `AuditTrailAnonymizationIntegrationTest`: borrar al alumno anonimiza las filas donde aparece como actor o como sujeto | Integración (RGPD) | Si el borrado de un alumno no anonimiza su rastro en auditoría, quedan datos personales identificables retenidos pese a la solicitud de baja, incumpliendo RGPD |
| `AuditTrailAnonymizationIntegrationTest`: anonimizar al sujeto no despoja el `actor_id` de un tercero | Integración (RGPD) | Un fallo aquí borraría o anonimizaría erróneamente la traza de auditoría de un tercero no implicado en la baja, perdiendo evidencia legítima de sus acciones |
| `AuditEventListenerIntegrationTest`: reentregar el mismo `AccesoDenegado` no duplica la fila | Integración (idempotencia de `@ApplicationModuleListener`) | Reintentos del outbox duplicarían asientos de auditoría, infringiendo la fiabilidad del log que se usa como evidencia ante incidentes de seguridad |
| `AuditEventListenerIntegrationTest`: un `AccesoDenegado` se persiste como fila `ACCESO_DENEGADO` | Integración | Si los intentos de acceso no autorizado (IDOR, RBAC) no quedan registrados, se pierde la capacidad de detectar e investigar ataques o fugas de datos |
| `ListAuditEventsQueryTest`: ni entrenador ni alumno pueden consultar el log de auditoría | Acceso cruzado (ADR-0009 D14) | Un entrenador o alumno podría leer el registro completo de accesos y bajas del club, exponiendo información sensible de otros usuarios y de la operativa administrativa |
| `ListAuditEventsQueryTest`: un rango `desde` posterior a `hasta` es `InvalidInput` y no llega al repositorio | Unitario | Un rango de fechas inválido sin validar podría generar una consulta costosa o con resultados inconsistentes contra el repositorio |
| `AuditoriaRetentionJobIntegrationTest`: purga asientos más antiguos que la retención y conserva los recientes | Integración (RGPD) | Si el job de retención no purga asientos vencidos, se incumple el plazo legal de conservación de datos de auditoría |
| `AuditoriaRetentionJobIntegrationTest`: un asiento justo dentro de la ventana de 24 meses no se purga | Integración (RGPD, caso borde) | Un error de límite (off-by-one) borraría prematuramente evidencia de auditoría todavía dentro del plazo legal de retención |

Si una PR introduce un caso crítico nuevo, actualiza esta tabla en el mismo commit.
