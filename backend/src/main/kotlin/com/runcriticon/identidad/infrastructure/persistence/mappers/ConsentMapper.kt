package com.runcriticon.identidad.infrastructure.persistence.mappers

import com.runcriticon.identidad.domain.consent.Consent
import com.runcriticon.identidad.infrastructure.persistence.entities.ConsentEntity
import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping

@Konverter
internal interface ConsentMapper {
    @Konvert(
        mappings = [
            Mapping(target = "id", expression = "com.runcriticon.identidad.domain.consent.ConsentId.of(it.id)"),
            Mapping(target = "userId", expression = "com.runcriticon.identidad.domain.user.UserId.of(it.userId)"),
            Mapping(target = "clubId", expression = "com.runcriticon.shared.tenancy.ClubId.of(it.clubId)"),
        ],
    )
    fun toDomain(entity: ConsentEntity): Consent

    @Konvert(
        mappings = [
            Mapping(target = "id", expression = "it.id.value"),
            Mapping(target = "userId", expression = "it.userId.value"),
            Mapping(target = "clubId", expression = "it.clubId.value"),
        ],
    )
    fun toEntity(domain: Consent): ConsentEntity
}
