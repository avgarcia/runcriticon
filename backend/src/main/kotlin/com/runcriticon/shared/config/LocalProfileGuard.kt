package com.runcriticon.shared.config

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Bloquea el arranque en perfil `local` si detecta credenciales AWS reales (ADR-0013 D13): PII de
 * entornos remotos no debe replicarse en máquinas locales, así que el perfil local nunca debe
 * alcanzar SSM/Secrets Manager reales.
 *
 * La heurística ([MIN_REAL_CREDENTIAL_LENGTH]) distingue un valor real (access key ~20 chars,
 * session token mucho más largo) de un valor fake/ausente en `application-local.yml`.
 */
@Component
@Profile("local")
class LocalProfileGuard(
    private val env: Environment,
) {
    @PostConstruct
    fun verify() {
        val realAwsCredentials =
            listOf(
                env.getProperty("AWS_ACCESS_KEY_ID"),
                env.getProperty("AWS_SESSION_TOKEN"),
            ).filterNotNull().filter { it.length > MIN_REAL_CREDENTIAL_LENGTH }

        check(realAwsCredentials.isEmpty()) {
            """
            Credenciales AWS reales detectadas en perfil local.

            El perfil local NO debe acceder a SSM staging o producción.
            Razón: PII de entornos remotos no debe replicarse en máquinas locales (ADR-0013 D13).

            Soluciones:
            - Usa application-local.yml con valores fake.
            - Si necesitas un dato de staging para debugging, pásalo por canal seguro fuera de banda.
            - Si necesitas SSM real, usa el perfil staging o un compañero del equipo lo hace por ti.
            """.trimIndent()
        }
    }

    private companion object {
        const val MIN_REAL_CREDENTIAL_LENGTH = 16
    }
}
