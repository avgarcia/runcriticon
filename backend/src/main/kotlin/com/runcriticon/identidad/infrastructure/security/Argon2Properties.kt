package com.runcriticon.identidad.infrastructure.security
import com.runcriticon.identidad.application.usecases.authentication.AuthenticateUserCommand
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Parámetros de Argon2id para el hash de contraseñas. Los defaults son el baseline OWASP (m=19 MiB, t=2, p=1); se
 * ajustan por entorno sin tocar código. Al endurecerlos, el upgrade-on-login
 * ([com.runcriticon.identidad.application.usecases.authentication.AuthenticateUserCommand]) re-hashea los hashes antiguos de forma
 * transparente en el siguiente login.
 *
 * @property saltLength longitud del salt en bytes.
 * @property hashLength longitud del hash resultante en bytes.
 * @property parallelism grado de paralelismo (p).
 * @property memoryKb coste de memoria en KiB (m).
 * @property iterations número de iteraciones (t).
 */
@ConfigurationProperties("runcriticon.security.argon2")
data class Argon2Properties(
    val saltLength: Int = DEFAULT_SALT_LENGTH,
    val hashLength: Int = DEFAULT_HASH_LENGTH,
    val parallelism: Int = DEFAULT_PARALLELISM,
    val memoryKb: Int = DEFAULT_MEMORY_KB,
    val iterations: Int = DEFAULT_ITERATIONS,
) {
    private companion object {
        const val DEFAULT_SALT_LENGTH = 16
        const val DEFAULT_HASH_LENGTH = 32
        const val DEFAULT_PARALLELISM = 1
        const val DEFAULT_MEMORY_KB = 19_456
        const val DEFAULT_ITERATIONS = 2
    }
}
