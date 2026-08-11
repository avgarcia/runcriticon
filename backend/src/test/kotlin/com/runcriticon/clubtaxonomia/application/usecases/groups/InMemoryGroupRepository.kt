package com.runcriticon.clubtaxonomia.application.usecases.groups

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.group.GroupMembers
import com.runcriticon.clubtaxonomia.domain.group.GroupSummary
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
    private val summaries: List<GroupSummary> = emptyList(),
    /**
     * Grupos que el doble da por existentes en el club. Vacío por defecto: los casos de uso de escritura tienen que
     * poder probar el camino del grupo que no existe sin montar nada.
     */
    private val existing: Map<GroupId, GroupDetail> = emptyMap(),
) : GroupRepository {
    val saved: MutableList<Pair<ClubId, Group>> = mutableListOf()
    val previewCalls: MutableList<Pair<ClubId, Set<TagValueId>>> = mutableListOf()
    val listCalls: MutableList<ClubId> = mutableListOf()
    val detailCalls: MutableList<Pair<ClubId, GroupId>> = mutableListOf()
    val overrides: MutableMap<Pair<GroupId, PersonId>, Boolean> = mutableMapOf()
    val overrideCalls: MutableList<Triple<ClubId, GroupId, PersonId>> = mutableListOf()
    val deleteCalls: MutableList<Triple<ClubId, GroupId, PersonId>> = mutableListOf()

    val saveCount: Int get() = saved.size
    val previewCount: Int get() = previewCalls.size
    val listCount: Int get() = listCalls.size
    val overrideCount: Int get() = overrideCalls.size

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

    override fun listSummaries(clubId: ClubId): List<GroupSummary> {
        listCalls += clubId
        return summaries
    }

    override fun findDetail(
        clubId: ClubId,
        groupId: GroupId,
    ): GroupDetail? {
        detailCalls += clubId to groupId
        return existing[groupId]
    }

    override fun exists(
        clubId: ClubId,
        groupId: GroupId,
    ): Boolean = groupId in existing

    override fun upsertOverride(
        clubId: ClubId,
        groupId: GroupId,
        studentId: PersonId,
        included: Boolean,
    ) {
        overrideCalls += Triple(clubId, groupId, studentId)
        overrides[groupId to studentId] = included
    }

    override fun deleteOverride(
        clubId: ClubId,
        groupId: GroupId,
        studentId: PersonId,
    ): Int {
        deleteCalls += Triple(clubId, groupId, studentId)
        return if (overrides.remove(groupId to studentId) != null) 1 else 0
    }
}
