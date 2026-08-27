package com.runcriticon.planificacion.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Forma JSON de la columna `personalizacion.override` (JSONB, LAL-26). A diferencia de `sesion`, esta tabla no
 * tiene columnas planas propias para tipo/volumen/ritmo/notas: el override entero — mismo shape que `Sesion`,
 * ADR-0002 D9 — vive en el JSONB. El mapeo dominio↔JSON vive en `WeeklyPlanRepositoryJdbc`, no aquí (mismo
 * criterio que `ResolvedSessionPayload` de `seguimiento`).
 */
internal data class SessionOverridePayload(
    val tipo: String,
    val volumenTipo: String? = null,
    val volumenMetros: Int? = null,
    val volumenMinutos: Int? = null,
    val ritmoTipo: String? = null,
    val ritmoSegPorKm: Int? = null,
    val ritmoRefDistancia: String? = null,
    val ritmoDeltaSegPorKm: Int? = null,
    val notas: String? = null,
)

/** Instancia propia del módulo, no el bean compartido de Spring — igual criterio que `RESOLVED_SESSION_MAPPER`
 * de `seguimiento`: basta para (de)serializar un DTO plano, sin módulos de tiempo ni mixins. */
internal val PERSONALIZATION_OVERRIDE_MAPPER: ObjectMapper = jacksonObjectMapper()
