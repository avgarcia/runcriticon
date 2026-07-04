package com.runcriticon.identidad.infrastructure.ratelimit

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

/**
 * Resuelve la IP del cliente para el rate-limiting por IP (ADR-0003 D12). Con
 * `server.forward-headers-strategy: framework` activo (necesario tras App Runner, cf. LAL-56),
 * Spring reescribe `remoteAddr` a partir de `X-Forwarded-For`, así que aquí basta leer `remoteAddr`
 * ya normalizado. Sin esa config, todas las peticiones compartirían la IP del proxy y el límite por
 * IP sería inservible.
 */
@Component
class ClientIpResolver {
    fun resolve(request: HttpServletRequest): String = request.remoteAddr ?: UNKNOWN

    private companion object {
        const val UNKNOWN = "unknown"
    }
}
