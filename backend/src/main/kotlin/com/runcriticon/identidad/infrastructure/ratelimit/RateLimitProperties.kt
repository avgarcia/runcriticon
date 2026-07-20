package com.runcriticon.identidad.infrastructure.ratelimit

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuración del rate-limiting de identidad. Primer `@ConfigurationProperties` del módulo.
 *
 * @property magicLink bandas de magic link (por cuenta y por IP).
 * @property passwordReset bandas de reseteo de contraseña (por cuenta y por IP).
 * @property invitationPerActorHourly invitaciones/reenvíos por hora y por actor (admin/entrenador).
 * @property login escalera de retardo progresivo tras fallos de login (paso 0 = sin espera).
 * @property emailCooldown intervalo mínimo creciente entre peticiones de email del mismo destinatario.
 */
@ConfigurationProperties("runcriticon.identidad.ratelimit")
data class RateLimitProperties(
    val magicLink: EmailFlowLimits =
        EmailFlowLimits(
            accountHourly = MAGIC_LINK_ACCOUNT_HOURLY,
            accountDaily = MAGIC_LINK_ACCOUNT_DAILY,
            ipHourly = IP_HOURLY,
            ipDaily = IP_DAILY,
        ),
    val passwordReset: EmailFlowLimits =
        EmailFlowLimits(
            accountHourly = RESET_ACCOUNT_HOURLY,
            accountDaily = RESET_ACCOUNT_DAILY,
            ipHourly = IP_HOURLY,
            ipDaily = IP_DAILY,
        ),
    val invitationPerActorHourly: Long = INVITATION_PER_ACTOR_HOURLY,
    val login: List<Duration> = DEFAULT_LOGIN_LADDER,
    val emailCooldown: List<Duration> = DEFAULT_EMAIL_COOLDOWN,
) {
    /** Límites de un flujo emisor de email en sus dos dimensiones anónimas (por cuenta y por IP). */
    data class EmailFlowLimits(
        val accountHourly: Long,
        val accountDaily: Long,
        val ipHourly: Long,
        val ipDaily: Long,
    )

    private companion object {
        const val MAGIC_LINK_ACCOUNT_HOURLY = 3L
        const val MAGIC_LINK_ACCOUNT_DAILY = 10L
        const val RESET_ACCOUNT_HOURLY = 3L
        const val RESET_ACCOUNT_DAILY = 5L
        const val IP_HOURLY = 20L
        const val IP_DAILY = 100L
        const val INVITATION_PER_ACTOR_HOURLY = 100L

        val DEFAULT_LOGIN_LADDER =
            listOf(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(60))
        val DEFAULT_EMAIL_COOLDOWN =
            listOf(Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(5))
    }
}
