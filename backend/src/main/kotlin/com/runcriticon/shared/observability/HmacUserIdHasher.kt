package com.runcriticon.shared.observability

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Implementación de [UserIdHasher]: HMAC-SHA256 del `userId` con el salt `crypto/userid-hash-salt`
 * (ADR-0011 D5, ADR-0013 D6, rotación anual). Espejo de `EmailHasherImpl` — mismo algoritmo, secreto
 * distinto para no acoplar la rotación de logs a la de tokens.
 *
 * El secreto real de producción viene de SSM vía `USERID_HASH_SALT` (ADR-0013 D7); el default de
 * dev vive en `application-local.yml`. **No tiene default en `application.yml`**: si falta, la app
 * falla al arrancar en vez de hashear con una clave vacía.
 */
@Component
class HmacUserIdHasher(
    @Value("\${runcriticon.observability.userid-hash-salt:}")
    private val salt: String,
) : UserIdHasher {
    init {
        require(salt.isNotBlank()) {
            "runcriticon.observability.userid-hash-salt no configurado: define USERID_HASH_SALT " +
                "(SSM /runcriticon/{env}/crypto/userid-hash-salt, ADR-0013)."
        }
    }

    override fun hash(userId: UUID): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(salt.toByteArray(Charsets.UTF_8), ALGORITHM))
        val digest = mac.doFinal(userId.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val ALGORITHM = "HmacSHA256"
    }
}
