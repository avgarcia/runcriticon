package com.runcriticon.clubtaxonomia.domain.tag

import java.time.Instant

/**
 * Valor permitido de un eje de la taxonomía (`TagValue`, ADR-0002 D1). Entidad hija de [TagKey].
 *
 * El archivado ([archivedAt]) es soft-delete (D10): un valor archivado se conserva —las asignaciones existentes no se
 * borran— pero deja de ofrecerse para nuevas asignaciones y no participa en la unicidad. `null` = activo.
 */
data class TagValue(
    val id: TagValueId,
    val tagKeyId: TagKeyId,
    val label: TagLabel,
    val metadata: TagValueMetadata,
    val archivedAt: Instant?,
) {
    val isActive: Boolean
        get() = archivedAt == null

    companion object {
        /** Longitud máxima del valor (wireframe 02, spec editor de taxonomía). */
        const val MAX_LABEL_LENGTH: Int = 60

        /** Nombre de negocio del campo, para los errores que llegan a REST. */
        const val FIELD: String = "valor"
    }
}
