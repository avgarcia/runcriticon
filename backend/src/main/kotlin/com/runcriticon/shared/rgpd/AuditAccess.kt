package com.runcriticon.shared.rgpd

/**
 * Tipo de acceso sensible que, al ocurrir, debe dejar rastro de auditoría. Cubre las lecturas que no son del propio
 * titular o que tocan datos de salud.
 */
enum class AccessType {
    /** Lectura de datos de salud (categoría especial RGPD art. 9). */
    SALUD,

    /** Lectura del perfil de un tercero (no del propio principal). */
    PERFIL_TERCERO,
}

/**
 * Marca un caso de uso cuyo acceso a datos sensibles debe auditarse. Al ejecutarse, genera un asiento de auditoría con
 * el [type] de acceso y el [resource] consultado.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuditAccess(
    val type: AccessType,
    val resource: String,
)
