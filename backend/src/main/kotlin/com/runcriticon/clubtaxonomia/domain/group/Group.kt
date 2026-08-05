package com.runcriticon.clubtaxonomia.domain.group

import arrow.core.Either
import arrow.core.raise.either
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Un grupo del club: una consulta nombrada sobre tags (ADR-0002 D3). [requiredTagValueIds] **es** la consulta —
 * un alumno pertenece al grupo si tiene todos esos valores asignados. Solo `AND` en el MVP: sin disyunción ni
 * negación (aplazado).
 *
 * [requiredTagValueIds] vacío es válido: un grupo sin tags requeridos no es "todo el club", es un grupo que solo
 * contiene a quien se incluya manualmente (D4) — caso borde explícito del AC de LAL-90.
 *
 * **Fuera de esta raíz** (por diseño, no por olvido):
 *  - Los overrides manuales (`grupo_alumno_override`, D4) — su caso de uso de escritura es LAL-92; este agregado no
 *    los modela porque la resolución de membresía combina D3+D4 en la consulta de persistencia, no en memoria.
 *  - Los entrenadores asignados al grupo (LAL-93) — la relación entrenador↔grupo es propiedad de la autorización de
 *    publicación, no de este agregado.
 *  - Renombrar o archivar un grupo — sin ticket todavía; esta raíz hoy solo modela la creación.
 */
data class Group(
    val id: GroupId,
    val clubId: ClubId,
    val name: GroupName,
    val requiredTagValueIds: Set<TagValueId>,
) {
    companion object {
        fun create(
            clubId: ClubId,
            rawName: String,
            requiredTagValueIds: Set<TagValueId> = emptySet(),
            id: GroupId = GroupId.new(),
        ): Either<ClubTaxonomiaError, Group> =
            either {
                val name = GroupName.of(rawName).bind()
                Group(id = id, clubId = clubId, name = name, requiredTagValueIds = requiredTagValueIds)
            }
    }
}
