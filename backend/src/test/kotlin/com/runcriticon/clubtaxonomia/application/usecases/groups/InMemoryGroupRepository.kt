package com.runcriticon.clubtaxonomia.application.usecases.groups

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.group.GroupMembers
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Doble en memoria del puerto. Además de guardar lo escrito, registra con qué club y con qué filtro se le llamó: casi
 * todo lo que hay que comprobar en los casos de uso de grupo es precisamente eso —que se usa el club del actor y que
 * un filtro inválido no llega hasta aquí—, no el contenido de la consulta.
 */
class InMemoryGroupRepository(
    private val preview: GroupMembers = GroupMembers.Empty,
) : GroupRepository {
    val saved: MutableList<Pair<ClubId, Group>> = mutableListOf()
    val previewCalls: MutableList<Pair<ClubId, Set<TagValueId>>> = mutableListOf()

    val saveCount: Int get() = saved.size
    val previewCount: Int get() = previewCalls.size

    override fun save(
        clubId: ClubId,
        group: Group,
    ) {
        saved += clubId to group
    }

    override fun resolveMembers(
        clubId: ClubId,
        groupId: GroupId,
    ): Set<PersonId> = emptySet()

    override fun previewMembers(
        clubId: ClubId,
        requiredTagValueIds: Set<TagValueId>,
    ): GroupMembers {
        previewCalls += clubId to requiredTagValueIds
        return preview
    }
}
