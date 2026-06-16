package com.runcriticon.identidad.infrastructure.persistencia

import com.runcriticon.identidad.domain.usuario.Email
import com.runcriticon.identidad.domain.usuario.User
import com.runcriticon.identidad.domain.usuario.UserId
import com.runcriticon.identidad.domain.usuario.UserStatus
import com.runcriticon.shared.autorizacion.modelo.Role

/**
 * Mapeo entity <-> dominio (manual en H0 para no depender de la generación de Konvert).
 */
internal fun UsuarioEntity.toDomain(): User =
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
