package com.runcriticon.clubtaxonomia.domain.group

import java.time.Instant

/**
 * Una sugerencia de fusión calculada para un grupo, o para un par de grupos.
 *
 * [MergeSuggestionType.MICRO] es una sugerencia de **un solo grupo**: [groupIdA] y [groupIdB] son el mismo id —
 * no hay un segundo grupo con el que fusionar, solo el aviso de que este es demasiado pequeño. Modelarlo como un
 * "par consigo mismo" en vez de añadir un campo nullable mantiene una única forma de persistencia y de
 * descarte para los dos tipos: la clave (`groupIdA`, `groupIdB`, `type`) identifica la sugerencia en ambos casos.
 *
 * [MergeSuggestionType.DUPLICADO] sí es un par de grupos distintos; [groupIdA]/[groupIdB] llegan ya en el orden
 * canónico que fija [pairOf] (el de menor [GroupId.value] primero), para que el par (A, B) y (B, A) sean la
 * misma fila.
 */
data class MergeSuggestion(
    val groupIdA: GroupId,
    val groupIdB: GroupId,
    val type: MergeSuggestionType,
    val calculatedAt: Instant,
) {
    init {
        require(type != MergeSuggestionType.DUPLICADO || groupIdA.value < groupIdB.value) {
            "una sugerencia DUPLICADO exige groupIdA < groupIdB (orden canónico del par)"
        }
        require(type != MergeSuggestionType.MICRO || groupIdA == groupIdB) {
            "una sugerencia MICRO es sobre un único grupo: groupIdA debe ser igual a groupIdB"
        }
    }

    companion object {
        fun micro(
            groupId: GroupId,
            calculatedAt: Instant,
        ) = MergeSuggestion(groupId, groupId, MergeSuggestionType.MICRO, calculatedAt)

        /** Ordena el par por [GroupId.value] antes de construir la sugerencia, sin depender de quién llama primero. */
        fun duplicateOf(
            groupIdX: GroupId,
            groupIdY: GroupId,
            calculatedAt: Instant,
        ): MergeSuggestion {
            val (a, b) = canonicalPair(groupIdX, groupIdY)
            return MergeSuggestion(a, b, MergeSuggestionType.DUPLICADO, calculatedAt)
        }

        /** Mismo orden canónico que usa [duplicateOf], expuesto para quien solo necesita la clave, no la sugerencia. */
        fun canonicalPair(
            groupIdX: GroupId,
            groupIdY: GroupId,
        ): Pair<GroupId, GroupId> = if (groupIdX.value < groupIdY.value) groupIdX to groupIdY else groupIdY to groupIdX
    }
}
