package com.runcriticon.identidad.domain.usuario

import com.runcriticon.shared.autorizacion.modelo.Role
import java.util.UUID

/**
 * Agregado de identidad (ADR-0003 D2): un usuario pertenece a un club, tiene un único rol y, en
 * el MVP, puede autenticarse con contraseña (ADR-0003 D5). Dominio puro: sin Spring ni JPA.
 */
data class User(
    val id: UserId,
    val clubId: UUID,
    val email: Email,
    val name: String,
    val role: Role,
    val passwordHash: String?,
    val status: UserStatus,
) {
    fun isActive(): Boolean = status == UserStatus.ACTIVO
}
