package com.runcriticon.identidad.domain.user

import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import java.time.Duration
import java.time.Instant

/**
 * Agregado de identidad: un usuario pertenece a un club, tiene un único rol y, en el MVP, puede autenticarse con
 * contraseña.
 *
 * @property markedForDeletionAt cuándo el admin marcó la baja; no nulo si y solo si [status] es [UserStatus.BAJA].
 * @property statusBeforeDeletion estado al que vuelve la cuenta si se cancela la baja; no nulo si y solo si [status] es
 *   [UserStatus.BAJA].
 */
data class User(
    val id: UserId,
    val clubId: ClubId,
    val email: Email,
    val name: String,
    val role: Role,
    val passwordHash: String?,
    val status: UserStatus,
    val passwordUpdatedAt: Instant? = null,
    val markedForDeletionAt: Instant? = null,
    val statusBeforeDeletion: UserStatus? = null,
) {
    init {
        require((status == UserStatus.BAJA) == (markedForDeletionAt != null)) {
            "una cuenta tiene fecha de baja si y solo si está en BAJA"
        }
        require((status == UserStatus.BAJA) == (statusBeforeDeletion != null)) {
            "una cuenta tiene estado previo a la baja si y solo si está en BAJA"
        }
        require(statusBeforeDeletion != UserStatus.BAJA) { "el estado previo a la baja no puede ser BAJA" }
    }

    fun isActive(): Boolean = status == UserStatus.ACTIVO

    /**
     * Indica si la contraseña ha caducado (90 días desde [passwordUpdatedAt]). Devuelve `false` cuando no hay
     * contraseña (cuenta solo-magic-link) o no se conoce cuándo se fijó: la caducidad no debe bloquear a quien no tiene
     * contraseña ni computarse sobre un dato ausente.
     */
    fun isPasswordExpired(
        now: Instant,
        maxAge: Duration = DEFAULT_PASSWORD_MAX_AGE,
    ): Boolean {
        if (passwordHash == null || passwordUpdatedAt == null) return false
        return now.isAfter(passwordUpdatedAt.plus(maxAge))
    }

    /**
     * Activa la cuenta tras consumir la invitación: fija la contraseña, anota cuándo y pasa a [UserStatus.ACTIVO]. Solo
     * una cuenta `INVITADO` se activa.
     */
    fun activate(
        passwordHash: String,
        now: Instant,
    ): User {
        require(status == UserStatus.INVITADO) { "solo se activa una cuenta INVITADO" }
        return copy(passwordHash = passwordHash, status = UserStatus.ACTIVO, passwordUpdatedAt = now)
    }

    /**
     * Fija una contraseña nueva en una cuenta ya activa y reinicia el reloj de caducidad. No cambia el estado de la
     * cuenta.
     */
    fun changePassword(
        passwordHash: String,
        now: Instant,
    ): User {
        require(status == UserStatus.ACTIVO) { "solo se cambia la contraseña de una cuenta ACTIVO" }
        return copy(passwordHash = passwordHash, passwordUpdatedAt = now)
    }

    /**
     * Sustituye el hash por uno recalculado con los parámetros vigentes. NO toca [passwordUpdatedAt]: la contraseña es
     * la misma, solo cambia su encoding; reiniciar el reloj de caducidad alargaría la vida de una contraseña vieja.
     */
    fun rehashPassword(newHash: String): User {
        require(passwordHash != null) { "solo se re-hashea una cuenta con contraseña" }
        return copy(passwordHash = newHash)
    }

    /**
     * Desactiva la cuenta: pasa de [UserStatus.ACTIVO] a [UserStatus.DESACTIVADO]. No toca la contraseña — el borrado
     * de credenciales es un derecho RGPD aparte. La revocación de sesiones y el asiento de auditoría los orquesta el
     * caso de uso, no el dominio.
     *
     * Solo una cuenta `ACTIVO` se desactiva; reintentar sobre una ya `DESACTIVADO` es una violación de invariante (el
     * caso de uso la traduce a `Conflict`, un 409).
     *
     * @param now instante de la desactivación (para trazabilidad del caso de uso; el agregado no lo guarda).
     */
    fun deactivate(
        @Suppress("UNUSED_PARAMETER") now: Instant,
    ): User {
        require(status == UserStatus.ACTIVO) { "solo se desactiva una cuenta ACTIVO" }
        return copy(status = UserStatus.DESACTIVADO)
    }

    /**
     * Marca la baja que decide el admin (ADR-0014 D14): la cuenta pasa a [UserStatus.BAJA] y guarda el estado que
     * tenía, al que vuelve si se cancela la baja. Se marca desde cualquier otro estado, igual que la supresión
     * inmediata no depende del estado de la cuenta. La revocación de sesiones y el asiento de auditoría los orquesta el
     * caso de uso.
     */
    fun markForDeletion(now: Instant): User {
        require(status != UserStatus.BAJA) { "la cuenta ya está en BAJA" }
        return copy(status = UserStatus.BAJA, markedForDeletionAt = now, statusBeforeDeletion = status)
    }

    /** Cancela la baja durante la gracia: la cuenta vuelve al estado que tenía al marcarla. */
    fun cancelDeletion(): User {
        require(status == UserStatus.BAJA) { "solo se cancela la baja de una cuenta en BAJA" }
        return copy(
            status = checkNotNull(statusBeforeDeletion),
            markedForDeletionAt = null,
            statusBeforeDeletion = null,
        )
    }

    /** Indica si la cuenta está en baja y ya pasaron los [DELETION_GRACE] desde que se marcó. */
    fun isDeletionGraceExpired(now: Instant): Boolean =
        status == UserStatus.BAJA && checkNotNull(markedForDeletionAt).plus(DELETION_GRACE).isBefore(now)

    companion object {
        /** Caducidad de la contraseña: 90 días desde que se fijó por última vez. */
        val DEFAULT_PASSWORD_MAX_AGE: Duration = Duration.ofDays(90)

        /** Gracia de la baja que decide el admin antes de suprimir la cuenta (ADR-0014 D10 y D14). */
        val DELETION_GRACE: Duration = Duration.ofDays(30)

        /**
         * Crea un usuario recién invitado: estado [UserStatus.INVITADO] y sin contraseña (la fijará al activar).
         * Encapsula el invariante del alta por invitación.
         */
        fun newInvited(
            clubId: ClubId,
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
