package com.runcriticon.clubtaxonomia.domain.tag

import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.group.GroupName

/**
 * Impacto de archivar un `TagKey` o un `TagValue`, para mostrar al admin antes de que confirme (LAL-83).
 *
 * [studentsAffected] es puramente informativo: archivar no toca `alumno_tag` (ADR-0002 D10), así que este número no
 * cambia por archivar, solo advierte de cuánta gente lo tiene asignado hoy. [groupsRequiring] es lo bloqueante — si
 * no está vacío, el comando de archivado lo rechaza (`TagKeyRequiredByGroup`/`TagValueRequiredByGroup`).
 */
data class TagArchiveImpact(
    val studentsAffected: Int,
    val groupsRequiring: List<RequiringGroup>,
) {
    /**
     * Un grupo vivo cuyo filtro exige alguno de los valores que se van a archivar.
     *
     * [wouldLoseAllRequiredTags] distingue el caso borde de ADR-0002 D3: si es `true`, todos los tags requeridos del
     * grupo están dentro de lo que se archiva, y el grupo se queda sin ningún filtro con el que ganar miembros
     * nuevos (solo le quedan las inclusiones manuales, D4). Si es `false`, el grupo conserva al menos un tag
     * requerido activo fuera de esta operación.
     */
    data class RequiringGroup(
        val groupId: GroupId,
        val groupName: GroupName,
        val wouldLoseAllRequiredTags: Boolean,
    )
}
