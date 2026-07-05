package com.runcriticon.identidad.application.ports

import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.model.ClubId
import java.time.Instant

/**
 * Puerto del histórico de contraseñas (ADR-0003 D6): permite a la política comprobar que una
 * contraseña nueva no reutiliza las últimas N. Guarda solo el hash Argon2id (la comparación con la
 * contraseña en claro la hace [PasswordHasher.matches], porque el hash va salado).
 */
interface PasswordHistory {
    /** Hashes de las últimas [count] contraseñas del usuario, de la más reciente a la más antigua. */
    fun recentHashes(
        userId: UserId,
        count: Int,
    ): List<String>

    /** Registra el hash de la contraseña recién fijada. */
    fun record(
        userId: UserId,
        clubId: ClubId,
        passwordHash: String,
        now: Instant,
    )
}
