package com.runcriticon.identidad.infrastructure.persistence.mappers

import com.runcriticon.identidad.domain.club.Club
import com.runcriticon.identidad.infrastructure.persistence.entities.ClubEntity
import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Konverter.Source
import io.mcarle.konvert.api.Mapping
import java.time.Instant

@Konverter
internal interface ClubMapper {
    @Konvert(
        mappings = [
            Mapping(target = "id", expression = "com.runcriticon.shared.tenancy.ClubId.of(it.id)"),
        ],
    )
    fun toDomain(entity: ClubEntity): Club

    /**
     * `now` alimenta `createdAt` solo en el alta: en un re-save la columna `creado_en` es no actualizable
     * (ver [ClubEntity]) y se ignora, de modo que solo avanza `modifiedAt` — igual que en `UserMapper`.
     */
    @Konvert(
        mappings = [
            Mapping(target = "id", expression = "domain.id.value"),
            Mapping(target = "name", expression = "domain.name"),
            Mapping(target = "slug", expression = "domain.slug"),
            Mapping(target = "createdAt", expression = "now"),
            Mapping(target = "modifiedAt", expression = "now"),
        ],
    )
    fun toEntity(
        @Source domain: Club,
        now: Instant,
    ): ClubEntity
}
