package com.runcriticon.identidad.domain.user

import com.runcriticon.shared.autorizacion.model.Role
import java.time.Duration
import java.time.Instant
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
    val passwordUpdatedAt: Instant? = null,
) {
    fun isActive(): Boolean = status == UserStatus.ACTIVO

    /**
     * Indica si la contraseña ha caducado (ADR-0003 D7: 90 días desde [passwordUpdatedAt]). Devuelve
     * `false` cuando no hay contraseña (cuenta solo-magic-link) o no se conoce cuándo se fijó: la
     * caducidad no debe bloquear a quien no tiene contraseña ni computarse sobre un dato ausente.
     */
    fun isPasswordExpired(
        now: Instant,
        maxAge: Duration = DEFAULT_PASSWORD_MAX_AGE,
    ): Boolean {
        if (passwordHash == null || passwordUpdatedAt == null) return false
        return now.isAfter(passwordUpdatedAt.plus(maxAge))
    }

    /**
     * Activa la cuenta tras consumir la invitación (ADR-0003 D4, LAL-9): fija la contraseña, anota
     * cuándo (para la caducidad D7) y pasa a [UserStatus.ACTIVO]. Solo una cuenta `INVITADO` se activa.
     */
    fun activate(
        passwordHash: String,
        now: Instant,
    ): User {
        require(status == UserStatus.INVITADO) { "solo se activa una cuenta INVITADO" }
        return copy(passwordHash = passwordHash, status = UserStatus.ACTIVO, passwordUpdatedAt = now)
    }

    /**
     * Fija una contraseña nueva en una cuenta ya activa y reinicia el reloj de caducidad (ADR-0003
     * D7, LAL-10: cambio forzado al caducar la contraseña). No cambia el estado de la cuenta.
     */
    fun changePassword(
        passwordHash: String,
        now: Instant,
    ): User {
        require(status == UserStatus.ACTIVO) { "solo se cambia la contraseña de una cuenta ACTIVO" }
        return copy(passwordHash = passwordHash, passwordUpdatedAt = now)
    }

    companion object {
        /** Caducidad de la contraseña (ADR-0003 D7): 90 días desde que se fijó por última vez. */
        val DEFAULT_PASSWORD_MAX_AGE: Duration = Duration.ofDays(90)

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
