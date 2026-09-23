package com.runcriticon.clubtaxonomia.domain.group

/**
 * Motivo por el que el sistema sugiere fusionar (o revisar) uno o dos grupos.
 *
 * Valor de enum persistido en castellano, mismo criterio que el resto del repo (`ENTRENADOR`, `ALUMNO`...).
 */
enum class MergeSuggestionType {
    /** El grupo tiene 2 alumnos o menos: demasiado pequeño para mantenerlo aparte. */
    MICRO,

    /** Dos grupos comparten al menos el 80 % de sus alumnos (índice de Jaccard): casi duplicados. */
    DUPLICADO,
}
