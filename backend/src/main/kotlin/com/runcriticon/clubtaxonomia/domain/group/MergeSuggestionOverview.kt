package com.runcriticon.clubtaxonomia.domain.group

import java.time.Instant

/**
 * Una sugerencia de fusión tal y como se pinta en la pantalla del admin/entrenador (LAL-96): con el nombre de
 * cada grupo, no solo su id.
 *
 * En [MergeSuggestionType.MICRO], [groupBId]/[groupBName] repiten los de A (mismo criterio que [MergeSuggestion]:
 * un solo grupo, sin campo nullable). El consumidor distingue por [type] si debe pintar uno o dos grupos.
 */
data class MergeSuggestionOverview(
    val groupAId: GroupId,
    val groupAName: GroupName,
    val groupBId: GroupId,
    val groupBName: GroupName,
    val type: MergeSuggestionType,
    val calculatedAt: Instant,
)
