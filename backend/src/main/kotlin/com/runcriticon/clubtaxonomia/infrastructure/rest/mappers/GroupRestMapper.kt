package com.runcriticon.clubtaxonomia.infrastructure.rest.mappers

import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupCoach
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
import com.runcriticon.clubtaxonomia.domain.group.GroupExclusion
import com.runcriticon.clubtaxonomia.domain.group.GroupMember
import com.runcriticon.clubtaxonomia.domain.group.GroupMemberOrigin
import com.runcriticon.clubtaxonomia.domain.group.GroupMembers
import com.runcriticon.clubtaxonomia.domain.group.GroupMembership
import com.runcriticon.clubtaxonomia.domain.group.GroupSummary
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestionOverview
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestionType
import com.runcriticon.clubtaxonomia.domain.person.PersonStatus
import com.runcriticon.shared.api.rest.GroupCoachResponse
import com.runcriticon.shared.api.rest.GroupCoachesResponse
import com.runcriticon.shared.api.rest.GroupDetailResponse
import com.runcriticon.shared.api.rest.GroupExclusionResponse
import com.runcriticon.shared.api.rest.GroupMemberResponse
import com.runcriticon.shared.api.rest.GroupMembersResponse
import com.runcriticon.shared.api.rest.GroupMembershipResponse
import com.runcriticon.shared.api.rest.GroupResponse
import com.runcriticon.shared.api.rest.GroupSummaryResponse
import com.runcriticon.shared.api.rest.GroupsResponse
import com.runcriticon.shared.api.rest.MergeSuggestionResponse
import com.runcriticon.shared.api.rest.MergeSuggestionsResponse
import com.runcriticon.shared.api.rest.TipoSugerenciaFusion
import java.time.ZoneOffset
import com.runcriticon.shared.api.rest.GroupMemberOrigin as ApiGroupMemberOrigin

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

/**
 * El detalle traduce además la frontera de idioma: el dominio nombra el origen en inglés y el contrato lo publica en
 * castellano, como los valores de enum que se persisten.
 */
internal fun GroupDetail.toResponse(): GroupDetailResponse =
    GroupDetailResponse(
        id = group.id.value,
        nombre = group.name.value,
        valores = group.requiredTagValueIds.map { it.value },
        total = total,
        miembros = members.map { it.toResponse() },
        excluidos = exclusions.map { it.toResponse() },
    )

internal fun GroupMembership.toResponse(): GroupMembershipResponse =
    GroupMembershipResponse(
        id = member.id.value,
        nombre = member.name,
        origen =
            when (origin) {
                GroupMemberOrigin.FILTER -> ApiGroupMemberOrigin.FILTRO
                GroupMemberOrigin.MANUAL_INCLUSION -> ApiGroupMemberOrigin.INCLUSION_MANUAL
            },
        ajusteManual = hasOverride,
    )

internal fun GroupExclusion.toResponse(): GroupExclusionResponse =
    GroupExclusionResponse(
        id = member.id.value,
        nombre = member.name,
        cumpleFiltro = matchesFilter,
    )

internal fun GroupSummary.toResponse(): GroupSummaryResponse =
    GroupSummaryResponse(
        id = group.id.value,
        nombre = group.name.value,
        valores = group.requiredTagValueIds.map { it.value },
        totalAlumnos = memberCount,
        tieneEntrenador = hasCoach,
    )

internal fun List<GroupCoach>.toResponse(): GroupCoachesResponse =
    GroupCoachesResponse(entrenadores = map { it.toResponse() })

internal fun GroupCoach.toResponse(): GroupCoachResponse =
    GroupCoachResponse(
        id = id.value,
        nombre = name,
        email = email,
        estado = status.toGroupCoachResponse(),
    )

private fun PersonStatus.toGroupCoachResponse(): GroupCoachResponse.Estado =
    when (this) {
        PersonStatus.INVITADO -> GroupCoachResponse.Estado.INVITADO
        PersonStatus.ACTIVO -> GroupCoachResponse.Estado.ACTIVO
    }

internal fun List<MergeSuggestionOverview>.toResponse(): MergeSuggestionsResponse =
    MergeSuggestionsResponse(sugerencias = map { it.toResponse() })

internal fun MergeSuggestionOverview.toResponse(): MergeSuggestionResponse =
    MergeSuggestionResponse(
        tipo = type.toResponse(),
        grupoIdA = groupAId.value,
        grupoNombreA = groupAName.value,
        grupoIdB = groupBId.value,
        grupoNombreB = groupBName.value,
        calculadoEn = calculatedAt.atOffset(ZoneOffset.UTC),
    )

internal fun MergeSuggestionType.toResponse(): TipoSugerenciaFusion =
    when (this) {
        MergeSuggestionType.MICRO -> TipoSugerenciaFusion.MICRO
        MergeSuggestionType.DUPLICADO -> TipoSugerenciaFusion.DUPLICADO
    }

internal fun TipoSugerenciaFusion.toDomain(): MergeSuggestionType =
    when (this) {
        TipoSugerenciaFusion.MICRO -> MergeSuggestionType.MICRO
        TipoSugerenciaFusion.DUPLICADO -> MergeSuggestionType.DUPLICADO
    }
