# Tests críticos — módulo Seguimiento

| Caso | Tipo de test | Por qué duele si falla en producción |
|---|---|---|
| `MarkIsolationTest`: retirar una marca solo afecta a la de quien llama, nunca a la de otro alumno | Acceso cruzado (IDOR) | Un alumno podría borrar las marcas de tiempo registradas por otro alumno, perdiendo su histórico de progreso |
| `MarkIsolationTest`: registrar una marca la guarda bajo el `studentId` de quien la registró | Unitario / aislamiento de datos | Si el `studentId` no se fija por el llamador, una marca podría grabarse bajo la identidad de otro alumno, falseando su progreso |
| `SessionReportIsolationTest`: el reporte de sesión siempre se consulta y escribe bajo el `studentId` de quien reporta | Acceso cruzado | Un alumno podría leer o sobreescribir el reporte de sesión de otro, exponiendo datos de entrenamiento ajenos |
| `RescheduleDayAuthorizationTest`: un rol sin permiso no puede reajustar una sesión, y no se toca ni el lector ni el repositorio | Acceso cruzado (ADR-0009 D14) | Un rol no autorizado podría reprogramar el calendario de entrenamiento de un alumno sin su consentimiento ni el del entrenador |
| `SeguimientoDeletionListenerTest`: reentregar el mismo evento no vuelve a borrar | Integración (idempotencia RGPD) | Un reintento del outbox tras la baja de un alumno podría fallar o duplicar el borrado de su proyección de seguimiento |
| `SeguimientoDeletionListenerTest`: un entrenador eliminado borra sus filas de `grupo_entrenador` | Integración (RGPD) | Si no se limpia la relación entrenador-grupo al eliminar al entrenador, quedan referencias huérfanas que rompen consultas de alertas y carga de trabajo |
| `StudentMarkTest`: un tiempo negativo o cero es `InvalidInput` | Unitario | Sin esta validación se podrían registrar marcas de tiempo físicamente imposibles, corrompiendo los cálculos de ritmo (`Pace`) y las alertas de rendimiento |
| `MarcaActualizadaContractTest`: el evento sin actor ni `traceparent` cumple el JSON Schema v1 | Contrato | Confirma que el evento sigue siendo válido en el caso borde de falta de trazabilidad, evitando que se descarten silenciosamente eventos legítimos en consumidores downstream |

Si una PR introduce un caso crítico nuevo, actualiza esta tabla en el mismo commit.
