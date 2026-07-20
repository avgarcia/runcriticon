package com.runcriticon.identidad.application.ports.outbound.security

import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.invitation.TokenHash

/**
 * Puerto de hashing de tokens: SHA-256 + HMAC con el secreto de aplicación. La implementación (infraestructura) inyecta
 * el secreto; el dominio nunca lo ve. Se reutiliza para verificar: se hashea el token presentado y la comparación
 * timing-safe vive en [TokenHash.matches].
 */
interface TokenHasher {
    fun hash(raw: RawToken): TokenHash
}
