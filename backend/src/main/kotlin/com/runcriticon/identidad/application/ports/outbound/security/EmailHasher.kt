package com.runcriticon.identidad.application.ports.outbound.security

/**
 * Puerto de hashing de emails para auditoría. HMAC-SHA256 con el secreto de aplicación: permite registrar en el asiento
 * `*_RATE_LIMITED` un `email_hash` con el que el admin puede correlacionar abusos **sin persistir el email en claro**.
 */
interface EmailHasher {
    fun hash(email: String): String
}
