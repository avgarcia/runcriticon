package com.runcriticon.identidad.domain.user

import com.runcriticon.shared.autorizacion.model.Role
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

    /**
     * Activa la cuenta tras consumir la invitación (ADR-0003 D4, LAL-9): fija la contraseña y pasa
     * a [UserStatus.ACTIVO]. Solo una cuenta `INVITADO` se activa (precondición de dominio).
     */
    fun activate(passwordHash: String): User {
        require(status == UserStatus.INVITADO) { "solo se activa una cuenta INVITADO" }
        return copy(passwordHash = passwordHash, status = UserStatus.ACTIVO)
    }

    companion object {
        /**
         * Crea un usuario recién invitado (ADR-0003 D3): estado [UserStatus.INVITADO] y sin
         * contraseña (la fijará al activar). Encapsula el invariante del alta por invitación.
         */
        fun newInvited(
            clubId: UUID,
            email: Email,
            name: String,
            role: Role,
        ): User =
            User(
                id = UserId.new(),
                clubId = clubId,
                email = email,
                name = name,
                role = role,
                passwordHash = null,
                status = UserStatus.INVITADO,
            )
    }
}
