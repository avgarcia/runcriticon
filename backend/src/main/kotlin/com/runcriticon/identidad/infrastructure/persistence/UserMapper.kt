package com.runcriticon.identidad.infrastructure.persistence

import com.runcriticon.identidad.domain.user.User
import com.runcriticon.shared.autorizacion.model.Role
import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Konverter.Source
import io.mcarle.konvert.api.Mapping
import java.time.Instant

internal fun rolFromText(text: String): Role = Role.valueOf(text)

@Konverter
internal interface UserMapper {
    @Konvert(
        mappings = [
            Mapping(target = "id", expression = "com.runcriticon.identidad.domain.user.UserId.of(it.id)"),
            Mapping(target = "clubId", expression = "com.runcriticon.shared.tenancy.ClubId.of(it.clubId)"),
            Mapping(target = "email", expression = "com.runcriticon.identidad.domain.user.Email.of(it.email)"),
            Mapping(target = "role", expression = "rolFromText(it.role)"),
            Mapping(
                target = "status",
                expression = "com.runcriticon.identidad.domain.user.UserStatus.valueOf(it.status)",
            ),
        ],
    )
    fun toDomain(entity: UserEntity): User

    /**
     * `now` alimenta `createdAt` solo en el alta: en un re-save la columna `creado_en` es no
     * actualizable (ver [UserEntity]) y se ignora, de modo que solo avanza `modifiedAt`.
     */
    @Konvert(
        mappings = [
            Mapping(target = "id", expression = "domain.id.value"),
            Mapping(target = "clubId", expression = "domain.clubId.value"),
            Mapping(target = "email", expression = "domain.email.value"),
            Mapping(target = "normalizedEmail", expression = "domain.email.value"),
            Mapping(target = "name", expression = "domain.name"),
            Mapping(target = "role", expression = "domain.role.name"),
            Mapping(target = "passwordHash", expression = "domain.passwordHash"),
            Mapping(target = "passwordUpdatedAt", expression = "domain.passwordUpdatedAt"),
            Mapping(target = "status", expression = "domain.status.name"),
            Mapping(target = "createdAt", expression = "now"),
            Mapping(target = "modifiedAt", expression = "now"),
        ],
    )
    fun toEntity(
        @Source domain: User,
        now: Instant,
    ): UserEntity
}
