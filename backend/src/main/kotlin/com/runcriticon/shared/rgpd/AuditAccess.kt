package com.runcriticon.shared.rgpd

/**
 * Tipo de acceso sensible que, al ocurrir, debe dejar rastro de auditoría (ADR-0013, ADR-0014,
 * rgpd-en-modulos). Cubre las lecturas que no son del propio titular o que tocan datos de salud.
 */
enum class AccessType {
    /** Lectura de datos de salud (categoría especial RGPD art. 9). */
    SALUD,

    /** Lectura del perfil de un tercero (no del propio principal). */
    PERFIL_TERCERO,
}

/**
 * Marca un caso de uso cuyo acceso a datos sensibles debe auditarse (ADR-0013). Al ejecutarse,
 * genera un asiento de auditoría con el [type] de acceso y el [resource] consultado. El interceptor
 * que materializa el asiento llega en Fase 1; en H0 queda el contrato.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuditAccess(
    val type: AccessType,
    val resource: String,
)
