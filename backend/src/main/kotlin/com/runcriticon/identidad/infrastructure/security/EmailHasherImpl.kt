package com.runcriticon.identidad.infrastructure.security

import com.runcriticon.identidad.application.ports.outbound.security.EmailHasher
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Adaptador de [EmailHasher]: HMAC-SHA256 del email normalizado con el secreto de aplicación (mismo secreto que
 * [TokenHasherImpl], inyectado desde SSM en prod). Se usa para el `email_hash` del asiento `*_RATE_LIMITED` — nunca se
 * persiste el email en claro.
 */
@Component
class EmailHasherImpl(
    @Value("\${runcriticon.security.token-hmac-secret:}")
    private val secret: String,
) : EmailHasher {
    init {
        require(secret.isNotBlank()) {
            "runcriticon.security.token-hmac-secret no configurado: define TOKEN_HMAC_SECRET " +
                "(SSM /runcriticon/{env}/security/token-hmac-secret)."
        }
    }

    override fun hash(email: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), ALGORITHM))
        val digest = mac.doFinal(email.trim().lowercase().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val ALGORITHM = "HmacSHA256"
    }
}
