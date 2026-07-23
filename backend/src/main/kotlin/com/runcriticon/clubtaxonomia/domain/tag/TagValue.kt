package com.runcriticon.clubtaxonomia.domain.tag

import java.time.Instant

/**
 * Valor permitido de un eje de la taxonomía. Entidad hija de [TagKey]: su [label] es el valor dentro del eje
 * (`principiante`, `5K`…), no el nombre del eje, que es el [TagKey.label] del padre. No guarda el id de su padre —
 * el enlace lo da la composición [TagKey.values]; la clave ajena de la tabla la reconstruye el mapeador de
 * `infrastructure` desde la posición en el agregado.
 *
 * El archivado ([archivedAt]) es soft-delete: un valor archivado se conserva —las asignaciones existentes no se
 * borran— pero deja de ofrecerse para nuevas asignaciones y no participa en la unicidad. `null` = activo.
 */
data class TagValue(
    val id: TagValueId,
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
