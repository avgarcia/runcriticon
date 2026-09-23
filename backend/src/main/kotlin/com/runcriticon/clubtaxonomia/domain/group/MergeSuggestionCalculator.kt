package com.runcriticon.clubtaxonomia.domain.group

import com.runcriticon.clubtaxonomia.domain.person.PersonId

/**
 * Reglas puras de la sugerencia de fusión de micro-grupos (M9b, risks R16): cuándo un grupo es "micro" y cuándo dos grupos son "casi duplicados".
 *
 * Sin puertos ni dependencias de infraestructura: opera sobre los conjuntos de miembros ya resueltos por
 * `GroupRepository`, que es quien sabe leerlos de la base de datos.
 */
object MergeSuggestionCalculator {
    /** Un grupo con 2 alumnos o menos se señala como micro (AC1). */
    const val MICRO_THRESHOLD = 2

    /**
     * Umbral de solape a partir del cual dos grupos se consideran casi duplicados (AC2): 80 %, medido como
     * índice de Jaccard (ver [overlapRatio]).
     */
    const val DUPLICATE_THRESHOLD = 0.8

    fun isMicro(members: Set<PersonId>): Boolean = members.size <= MICRO_THRESHOLD

    fun isDuplicate(
        membersA: Set<PersonId>,
        membersB: Set<PersonId>,
    ): Boolean = overlapRatio(membersA, membersB) >= DUPLICATE_THRESHOLD

    /**
     * Índice de Jaccard: `|A ∩ B| / |A ∪ B|`. Simétrico y sin dirección — a diferencia de "qué fracción del más
     * pequeño está en el más grande", no dispara con un grupo pequeño anidado dentro de una categoría grande (eso
     * es un filtro más específico, no un casi-duplicado); dispara cuando los dos grupos son, en la práctica, la
     * misma gente.
     *
     * `0.0` si ambos conjuntos están vacíos: la unión vacía no puede dividir, y dos grupos vacíos no son "casi
     * duplicados" entre sí — cada uno, por separado, ya cae en [isMicro].
     */
    fun overlapRatio(
        membersA: Set<PersonId>,
        membersB: Set<PersonId>,
    ): Double {
        val union = membersA.size + membersB.size - membersA.intersect(membersB).size
        if (union == 0) return 0.0
        return membersA.intersect(membersB).size.toDouble() / union
    }
}
