package com.runcriticon.identidad.infrastructure.persistence

import com.runcriticon.identidad.domain.invitation.Invitation
import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping

@Konverter
internal interface InvitationMapper {
    @Konvert(
        mappings = [
            Mapping(target = "id", expression = "com.runcriticon.identidad.domain.invitation.InvitationId.of(it.id)"),
            Mapping(target = "userId", expression = "com.runcriticon.identidad.domain.user.UserId.of(it.userId)"),
            Mapping(target = "clubId", expression = "com.runcriticon.shared.tenancy.ClubId.of(it.clubId)"),
            Mapping(
                target = "tokenHash",
                expression = "com.runcriticon.identidad.domain.invitation.TokenHash(it.tokenHash)",
            ),
        ],
    )
    fun toDomain(entity: InvitationEntity): Invitation

    @Konvert(
        mappings = [
            Mapping(target = "id", expression = "it.id.value"),
            Mapping(target = "userId", expression = "it.userId.value"),
            Mapping(target = "clubId", expression = "it.clubId.value"),
            Mapping(target = "tokenHash", expression = "it.tokenHash.value"),
        ],
    )
    fun toEntity(domain: Invitation): InvitationEntity
}
