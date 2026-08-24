package com.runcriticon.seguimiento.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Forma JSON de la columna `sesion_resuelta` (JSONB): solo tipo, volumen y notas — lo que no cabe en columnas
 * planas. El ritmo va aparte, en columnas `ritmo_*` planas: LAL-32 necesitará filtrar por
 * `ritmo_referencia_distancia` en SQL para recalcular tras una marca nueva, y un JSONB no indexa eso sin una
 * expresión dedicada. `dia`, `mensaje_al_alumno` y `es_personalizada` tampoco van aquí: son columnas propias
 * con su propio significado de consulta.
 *
 * Sin `@JsonInclude`/polimorfismo: a diferencia de `TagValueMetadata` de `clubtaxonomia`, esta forma no es una
 * sealed class de dominio, es un DTO de infraestructura — el mapeo dominio↔JSON vive en
 * `ResolvedPlanProjectionJdbc`/`ResolvedPlanReaderJdbc`, no aquí.
 */
internal data class ResolvedSessionPayload(
    val tipo: String,
    val volumenTipo: String? = null,
    val volumenMetros: Int? = null,
    val volumenMinutos: Int? = null,
    val notas: String? = null,
)

/** Instancia propia del módulo, no el bean compartido de Spring — igual criterio que los converters JSONB del
 * resto del repo: basta para (de)serializar un DTO plano, sin módulos de tiempo ni mixins. */
internal val RESOLVED_SESSION_MAPPER: ObjectMapper = jacksonObjectMapper()
