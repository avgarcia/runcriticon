package com.runcriticon.identidad.application.ports

/**
 * Puerto de hashing de emails para auditoría (ADR-0003 D12/D15). HMAC-SHA256 con el secreto de
 * aplicación: permite registrar en el asiento `*_RATE_LIMITED` un `email_hash` con el que el admin
 * puede correlacionar abusos **sin persistir el email en claro** (minimización RGPD, ADR-0014).
 */
interface EmailHasher {
    fun hash(email: String): String
}
