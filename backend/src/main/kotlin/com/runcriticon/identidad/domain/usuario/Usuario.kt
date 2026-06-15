package com.runcriticon.identidad.domain.usuario

import com.runcriticon.shared.autorizacion.modelo.Rol
import java.util.UUID

/**
 * Agregado de identidad (ADR-0003 D2): un usuario pertenece a un club, tiene un único rol y, en
 * el MVP, puede autenticarse con contraseña (ADR-0003 D5). Dominio puro: sin Spring ni JPA.
 */
data class Usuario(
    val id: UsuarioId,
    val clubId: UUID,
    val email: Email,
    val nombre: String,
    val rol: Rol,
    val passwordHash: String?,
    val estado: EstadoUsuario,
) {
    fun estaActivo(): Boolean = estado == EstadoUsuario.ACTIVO
}
