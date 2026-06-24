package com.runcriticon.identidad.infrastructure.security

import com.runcriticon.identidad.application.ports.TokenHasher
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.invitation.TokenHash
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Adaptador del puerto [TokenHasher] (ADR-0003 D13): HMAC-SHA256 del token con el secreto de
 * aplicación (inyectado desde SSM en prod, ADR-0013). El dominio nunca ve el secreto; solo guarda
 * y compara el [TokenHash] resultante en hex.
 */
@Component
class TokenHasherImpl(
    @Value("\${runcriticon.security.token-hmac-secret:}")
    private val secret: String,
) : TokenHasher {
    init {
        // Fail-fast al arrancar: un secreto en blanco haría que SecretKeySpec lanzara
        // "Empty key" en la primera invitación, en runtime y con mensaje opaco. Mejor que
        // un despliegue mal configurado no arranque (ADR-0003 D13, secreto desde SSM ADR-0013).
        require(secret.isNotBlank()) {
            "runcriticon.security.token-hmac-secret no configurado: define TOKEN_HMAC_SECRET " +
                "(SSM /runcriticon/{env}/security/token-hmac-secret, ADR-0013)."
        }
    }

    override fun hash(raw: RawToken): TokenHash {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), ALGORITHM))
        val digest = mac.doFinal(raw.value.toByteArray(Charsets.UTF_8))
        return TokenHash(digest.toHex())
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val ALGORITHM = "HmacSHA256"
    }
}
