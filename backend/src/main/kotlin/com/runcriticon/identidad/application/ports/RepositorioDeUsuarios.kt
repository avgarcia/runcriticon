package com.runcriticon.identidad.application.ports

import com.runcriticon.identidad.domain.usuario.Email
import com.runcriticon.identidad.domain.usuario.Usuario
import java.util.UUID

/**
 * Puerto de persistencia de usuarios (ADR-0008: el dominio/aplicación define el puerto, la
 * infraestructura lo implementa). En H0 solo la lectura necesaria para el login.
 */
interface RepositorioDeUsuarios {
    fun buscarPorEmail(
        clubId: UUID,
        email: Email,
    ): Usuario?
}
