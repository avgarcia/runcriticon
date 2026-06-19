package com.runcriticon.identidad.infrastructure.persistence

import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.model.Role

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

internal fun rolFromText(text: String): Role = Role.valueOf(text)
