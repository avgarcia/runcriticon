package com.runcriticon.clubtaxonomia.domain.tag

/**
 * Forma de los valores de un [TagKey]: si pueden llevar metadata de carrera o no.
 *
 * Es un invariante del agregado `Taxonomía`, no una pista de UI: [SIMPLE] rechaza que sus valores
 * lleven [TagValueMetadata.Race] (ver `Taxonomy.addValue` / `changeValueMetadata`).
 */
enum class TagKeyType {
    /** Sus valores solo llevan [TagValueMetadata.Empty]. */
    SIMPLE,

    /** Sus valores pueden llevar [TagValueMetadata.Race] (el eje `objetivo` nace así). */
    RACE,
}
