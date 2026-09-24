# Tests críticos — módulo Planificación

| Caso | Tipo de test | Por qué duele si falla en producción |
|---|---|---|
| `PublishPlanCommandTest`: un plan ya publicado no se puede republicar | Unitario | Republicar sobreescribiría el snapshot congelado de alumnos y sesiones que el alumno ya vio, rompiendo la trazabilidad de lo que se le comunicó |
| `PublishPlanCommandTest`: un plan sin sesiones no se puede publicar | Unitario | Un alumno recibiría un plan semanal vacío como si fuera válido, sin ninguna sesión de entrenamiento |
| `PublishPlanCommandTest`: un entrenador sin relación con el grupo recibe `Forbidden` y no publica nada | Acceso cruzado (IDOR, ADR-0009 D14) | Un entrenador podría publicar planes para grupos que no entrena, alterando el entrenamiento de alumnos ajenos |
| `PublishPlanCommandTest`: una proyección atrasada 60 s o más rechaza con `ProjectionStale` y no publica nada | Unitario | Publicar con una proyección de membresía desactualizada congelaría un snapshot con alumnos incorrectos en el plan |
| `PlanAuthorizationTest`: ni admin ni alumno pueden publicar un plan, y no se toca la base | Acceso cruzado | Un alumno podría publicar su propio plan de entrenamiento sin supervisión del entrenador, saltándose el flujo de revisión |
| `WeeklyPlanRepositoryJdbcIntegrationTest`: escribir o leer un plan de otro club no encuentra nada | Acceso cruzado (IDOR) a nivel de persistencia | Confirma que el filtro por `club_id` en el repositorio impide leer o escribir sesiones de planes de otro club aunque se conozca el ID |
| `PlanSnapshotSurvivesStudentTagChangeIntegrationTest`: quitar el tag que sostenía la pertenencia de un alumno no altera el snapshot de un plan ya publicado | Integración | Cambios posteriores de tags no deben alterar retroactivamente un plan ya comunicado al alumno; si fallara, el histórico de planes publicados sería inconsistente |
| `PlanPublicadoContractTest`: el evento con snapshot vacío cumple el JSON Schema v1 | Contrato | Un evento mal formado rompería a `seguimiento`, que depende de este contrato para construir la vista semanal del alumno |

Si una PR introduce un caso crítico nuevo, actualiza esta tabla en el mismo commit.
