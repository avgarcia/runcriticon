# Tests críticos — módulo Club y taxonomía

| Caso | Tipo de test | Por qué duele si falla en producción |
|---|---|---|
| `GroupAuthorizationTest`: todos los casos de uso de grupo operan sobre el club del actor, nunca sobre otro | Acceso cruzado (IDOR, ADR-0009 D14) | Un admin o entrenador de un club podría leer o modificar grupos y alumnos de otro club distinto |
| `GroupAuthorizationTest`: el alumno no puede ejecutar ninguna operación de grupo, y no se toca la base | Acceso cruzado | Un alumno podría crear, editar o reasignar entrenadores de grupos, escalando privilegios sobre la estructura del club |
| `TaxonomyCrossClubAccessTest`: archivar un `TagKey` de otro club devuelve `TagKeyNotFound` y no persiste | Acceso cruzado (IDOR) | Permitiría archivar o modificar la taxonomía (tags) de un club ajeno adivinando IDs, corrompiendo la clasificación de otro club |
| `StudentDeletionListenerTest`: reentregar la misma baja no vuelve a borrar | Integración (idempotencia de `@ApplicationModuleListener`) | Con el outbox reintentando eventos, un reproceso no idempotente podría lanzar errores o corromper el estado de la proyección local del alumno |
| `StudentDeletionListenerTest`: la baja de un admin no borra la proyección pero sí anonimiza su `actor_id` en auditoría | Integración (RGPD) | Si se confunde el tratamiento de un admin con el de un alumno, se pierde traza de auditoría necesaria o se deja PII sin anonimizar |
| `MergeSuggestionListenerTest`: reentregar el mismo evento no vuelve a recalcular | Integración (idempotencia) | Reprocesar el mismo evento podría duplicar o inflar sugerencias de fusión de grupos, generando ruido operativo para los entrenadores |
| `MembresiaDeGrupoCambiadaContractTest`: el evento con grupo vacío cumple el JSON Schema v1 | Contrato | Un evento malformado con grupo vacío rompería a los consumidores (planificación, seguimiento) que dependen de este contrato para su proyección local |
| `GroupTest`: `requiredTagValueIds` vacío es un grupo válido | Unitario | Rechazar este caso borde bloquearía la creación de grupos genéricos sin segmentación por tag, un flujo legítimo y común |

Si una PR introduce un caso crítico nuevo, actualiza esta tabla en el mismo commit.
