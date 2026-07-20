package com.runcriticon.identidad.application.ports.outbound.security

import com.runcriticon.identidad.domain.invitation.RawToken

/**
 * Puerto de generación de tokens de un solo uso: aleatorio con ≥256 bits de entropía. La implementación
 * (infraestructura) usa `SecureRandom`. El texto claro solo se usa para el email del destinatario; nunca se persiste.
 */
interface TokenGenerator {
    fun generate(): RawToken
}
