package com.runcriticon.shared.rgpd

import java.util.UUID

/**
 * Implementado por el valor de éxito (`Either.Right`) de un caso de uso anotado con [AuditAccess], cuando el
 * acceso alcanza a uno o varios sujetos de datos sensibles — [AuditAccessAspect] publica un
 * `AccesoADatosSensibles` (ADR-0009 D15) por cada id que devuelva [auditSubjectIds].
 *
 * Vive en `application`, no en `domain`: el dominio no conoce nada de auditoría RGPD, solo el resultado que
 * expone el caso de uso al exterior (mismo criterio que `GetMyWeekQuery.WeekResult`, un tipo de resultado
 * propio del caso de uso, no del agregado).
 */
interface AuditSubjects {
    fun auditSubjectIds(): Set<UUID>
}
