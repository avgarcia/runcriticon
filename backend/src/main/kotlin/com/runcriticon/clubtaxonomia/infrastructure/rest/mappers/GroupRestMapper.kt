package com.runcriticon.clubtaxonomia.infrastructure.rest.mappers

import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupMember
import com.runcriticon.clubtaxonomia.domain.group.GroupMembers
import com.runcriticon.clubtaxonomia.domain.group.GroupSummary
import com.runcriticon.shared.api.rest.GroupMemberResponse
import com.runcriticon.shared.api.rest.GroupMembersResponse
import com.runcriticon.shared.api.rest.GroupResponse
import com.runcriticon.shared.api.rest.GroupSummaryResponse
import com.runcriticon.shared.api.rest.GroupsResponse

/**
 * Traduce el grupo y su membresía a los modelos del contrato.
 *
 * El grupo viaja con su filtro porque es lo que lo define: el cliente repinta los chips con lo que responde el
 * servidor en vez de recomponerlos por su cuenta. El orden de la membresía lo fija la consulta, no este mapeador.
 */
internal fun Group.toResponse(): GroupResponse =
    GroupResponse(
        id = id.value,
        nombre = name.value,
        valores = requiredTagValueIds.map { it.value },
    )

internal fun GroupMembers.toResponse(): GroupMembersResponse =
    GroupMembersResponse(
        total = total,
        alumnos = members.map { it.toResponse() },
    )

internal fun GroupMember.toResponse(): GroupMemberResponse =
    GroupMemberResponse(
        id = id.value,
        nombre = name,
    )

internal fun List<GroupSummary>.toResponse(): GroupsResponse = GroupsResponse(grupos = map { it.toResponse() })

internal fun GroupSummary.toResponse(): GroupSummaryResponse =
    GroupSummaryResponse(
        id = group.id.value,
        nombre = group.name.value,
        valores = group.requiredTagValueIds.map { it.value },
        totalAlumnos = memberCount,
    )
