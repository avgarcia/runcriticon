package com.runcriticon.clubtaxonomia.domain.tag

import com.runcriticon.shared.tenancy.ClubId
import java.time.Instant

/**
 * Eje de la taxonomía de un club: `nivel`, `objetivo`, `terreno`… El conjunto de `TagKey` de un club **es** su
 * taxonomía. Contiene sus [values] (composición del agregado `Taxonomy`).
 *
 * El archivado ([archivedAt], soft-delete) marca solo la key: sus valores conservan su propio `archivedAt` (no hay
 * cascada). Toda la rama queda oculta para asignar porque
 * [com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy.assignableValues] excluye los valores de keys archivadas.
 * `null` = activa.
 */
data class TagKey(
    val id: TagKeyId,
    val clubId: ClubId,
    val label: TagLabel,
    val archivedAt: Instant?,
    val values: List<TagValue>,
) {
    val isActive: Boolean
        get() = archivedAt == null

    companion object {
        /** Longitud máxima del nombre (wireframe 02, spec editor de taxonomía). */
        const val MAX_LABEL_LENGTH: Int = 40

        /** Nombre de negocio del campo, para los errores que llegan a REST. */
        const val FIELD: String = "nombre"
    }
}
