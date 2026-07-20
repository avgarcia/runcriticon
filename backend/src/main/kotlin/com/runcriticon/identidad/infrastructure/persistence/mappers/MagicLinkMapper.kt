package com.runcriticon.identidad.infrastructure.persistence.mappers

import com.runcriticon.identidad.domain.magiclink.MagicLink
import com.runcriticon.identidad.infrastructure.persistence.entities.MagicLinkEntity
import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping

@Konverter
internal interface MagicLinkMapper {
    @Konvert(
        mappings = [
            Mapping(target = "id", expression = "com.runcriticon.identidad.domain.magiclink.MagicLinkId.of(it.id)"),
            Mapping(target = "userId", expression = "com.runcriticon.identidad.domain.user.UserId.of(it.userId)"),
            Mapping(target = "clubId", expression = "com.runcriticon.shared.tenancy.ClubId.of(it.clubId)"),
            Mapping(
                target = "tokenHash",
                expression = "com.runcriticon.identidad.domain.invitation.TokenHash(it.tokenHash)",
            ),
            Mapping(
                target = "proposito",
                expression = "com.runcriticon.identidad.domain.magiclink.MagicLinkPurpose.valueOf(it.purpose)",
            ),
        ],
    )
    fun toDomain(entity: MagicLinkEntity): MagicLink

    @Konvert(
        mappings = [
            Mapping(target = "id", expression = "it.id.value"),
            Mapping(target = "userId", expression = "it.userId.value"),
            Mapping(target = "clubId", expression = "it.clubId.value"),
            Mapping(target = "tokenHash", expression = "it.tokenHash.value"),
            Mapping(target = "purpose", expression = "it.proposito.name"),
        ],
    )
    fun toEntity(domain: MagicLink): MagicLinkEntity
}
