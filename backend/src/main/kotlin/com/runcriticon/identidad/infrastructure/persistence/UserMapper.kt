package com.runcriticon.identidad.infrastructure.persistence

import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.model.Role
import java.time.Instant

/**
 * Mapeo entity <-> dominio (manual en H0 para no depender de la generación de Konvert).
 */
internal fun UserEntity.toDomain(): User =
    User(
        id = UserId.of(id),
        clubId = clubId,
        email = Email.of(email),
        name = name,
        role = rolFromText(role),
        passwordHash = passwordHash,
        status = UserStatus.valueOf(status),
    )

/**
 * Mapeo dominio -> entity para el alta. El agregado solo lleva el [Email] ya normalizado, así que
 * `email` y `email_normalizado` coinciden; los timestamps de persistencia los aporta el adaptador.
 * `now` alimenta `createdAt` solo en el alta: en un re-save la columna `creado_en` es no actualizable
 * (ver [UserEntity]) y se ignora, de modo que solo avanza `modifiedAt`.
 */
internal fun User.toEntity(now: Instant): UserEntity =
    UserEntity(
        id = id.value,
        clubId = clubId,
        email = email.value,
        normalizedEmail = email.value,
        name = name,
        role = role.name,
        passwordHash = passwordHash,
        status = status.name,
        createdAt = now,
        modifiedAt = now,
    )

internal fun rolFromText(text: String): Role = Role.valueOf(text)
