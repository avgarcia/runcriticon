package com.runcriticon.identidad.infrastructure.persistencia

import com.runcriticon.identidad.domain.usuario.Email
import com.runcriticon.identidad.domain.usuario.EstadoUsuario
import com.runcriticon.identidad.domain.usuario.Usuario
import com.runcriticon.identidad.domain.usuario.UsuarioId
import com.runcriticon.shared.autorizacion.modelo.Rol

/**
 * Mapeo entity <-> dominio (manual en H0 para no depender de la generación de Konvert).
 */
internal fun UsuarioEntity.aDominio(): Usuario =
    Usuario(
        id = UsuarioId.de(id),
        clubId = clubId,
        email = Email.de(email),
        nombre = nombre,
        rol = rolDesdeTexto(rol),
        passwordHash = passwordHash,
        estado = EstadoUsuario.valueOf(estado),
    )

internal fun rolDesdeTexto(texto: String): Rol = Rol.valueOf(texto)
