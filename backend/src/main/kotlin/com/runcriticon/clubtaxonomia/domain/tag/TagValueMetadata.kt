package com.runcriticon.clubtaxonomia.domain.tag

import java.time.LocalDate

/**
 * Apéndice tipado y opcional de un `TagValue` (ADR-0002 D1).
 *
 * El dominio **no** conoce JSONB: la metadata se modela como esta `sealed class`, con una variante por tipo que
 * necesita cada eje. La serialización a/desde JSONB vive en el mapeador de `infrastructure` (LAL-79); si un JSON
 * corrupto no deserializa, el mapeador degrada a [Empty] y loguea — nunca propaga el fallo al dominio.
 *
 * Al ser sellada, un `when` sobre ella es exhaustivo sin `else`: añadir una variante rompe la compilación a propósito
 * en todos los puntos que la consumen.
 */
sealed class TagValueMetadata {
    /** Sin metadata: valores de `nivel`, `terreno`, `estado`… */
    data object Empty : TagValueMetadata()

    /**
     * Metadata de una carrera (valores del eje `objetivo`): fecha y distancia estándar.
     */
    data class Race(
        val date: LocalDate,
        val distance: Distance,
    ) : TagValueMetadata()
}
